package dev.nikko.appcanvasfaker.hook

import dev.nikko.appcanvasfaker.util.HookLog
import android.content.Context
import android.util.Log
import dev.nikko.appcanvasfaker.BuildConfig
import dev.nikko.appcanvasfaker.core.ProtectionMode
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.json.JSONObject

/**
 * libxposed 102 入口：包过滤（仅排除自身）→ 经 StatsProvider(read_config, ContentProvider call)
 * 读到本包专属配置 → 注册 3 个 hook。全流程 try/catch，hook 异常不影响目标 app。
 *
 * 安装时序：每个包只允许一个"解析流程"（gate 去重）；只有拿到确定性结果
 * （安装成功 / 配置明确显示未启用）才算终态；配置缺失、为空或解析失败都会
 * 条件式后台重试，避免启动瞬间的 Provider 冷启动竞态把包永久标记成已处理。
 */
class LibXposedInit : XposedModule() {

    /** 解析流程去重：putIfAbsent 成功者负责该包从读取到终态的全过程。 */
    private val gates = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** 已达终态的包（信息记录用）：已装 hook 或确认未启用。 */
    private val settled = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val attachHooked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            val packageName = param.packageName
            HookLog.i(TAG, "onPackageLoaded entry pkg=$packageName")
            // 包过滤：不排除任何应用（含模块自身——供「哈希测试」页面做 Hook 验证）
            // 早期快速通道：context 已可用时立刻装（多数 App 在 onPackageLoaded 阶段 Application 已创建）
            val context = getHostApplication()
            if (context != null) {
                tryInstall(packageName, context, param)
            } else {
                Log.w(TAG, "context null at onPackageLoaded, hook Application.attach: $packageName")
                hookApplicationAttach(packageName, param)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onPackageLoaded failed for ${param.packageName}", t)
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        try {
            val packageName = param.packageName
            val context = getHostApplication()
            if (context != null) {
                tryInstall(packageName, context, param)
            } else {
                Log.w(TAG, "onPackageReady context still null: $packageName")
                hookApplicationAttach(packageName, param)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onPackageReady failed for ${param.packageName}", t)
        }
    }

    /**
     * 兜底：hook 目标 App 的 Application.attach(Context)。该方法被调用时 thisObject
     * 即 Application（自身是 Context），跨进程 ContentProvider call 所需的 contentResolver
     * 由此取得——彻底摆脱 onPackageLoaded/onPackageReady 阶段 ActivityThread.currentApplication()
     * 可能为 null 的时序问题。
     */
    private fun hookApplicationAttach(
        packageName: String,
        param: XposedModuleInterface.PackageLoadedParam
    ) {
        if (!attachHooked.add(packageName)) return
        try {
            val appClass = param.defaultClassLoader.loadClass("android.app.Application")
            val attach = appClass.getDeclaredMethod("attach", Context::class.java)
            hook(attach).intercept { chain ->
                HookLog.i(TAG, "Application.attach fired for $packageName")
                runCatching {
                    val app = chain.getThisObject() as? Context
                    if (app != null) {
                        tryInstall(packageName, app, param)
                    }
                }.onFailure { Log.e(TAG, "Application.attach install failed for $packageName", it) }
                chain.proceed()
            }
            // 第二触发点：Application.onCreate 时 App 已完全初始化，ContentProvider 必然可用
            val onCreate = appClass.getDeclaredMethod("onCreate")
            hook(onCreate).intercept { chain ->
                HookLog.i(TAG, "Application.onCreate fired for $packageName")
                runCatching {
                    val app = chain.getThisObject() as? Context
                    if (app != null) {
                        tryInstall(packageName, app, param)
                    }
                }.onFailure { Log.e(TAG, "Application.onCreate install failed for $packageName", it) }
                chain.proceed()
            }
            HookLog.i(TAG, "hooked Application attach/onCreate for $packageName")
        } catch (t: Throwable) {
            Log.e(TAG, "hook Application.attach failed for $packageName", t)
        }
    }

    /** 每个包仅第一个到达者启动解析流程，后续触发直接返回。 */
    private fun tryInstall(
        packageName: String,
        context: Context,
        param: XposedModuleInterface.PackageLoadedParam
    ) {
        if (gates.putIfAbsent(packageName, true) != null) return
        resolveConfig(packageName, context, param, attempt = 0)
    }

    /**
     * 单次解析：
     * - 规则启用 → 安装（成功后进入终态）；
     * - 拿到合法配置但未启用 → 终态，不再重试；
     * - 空配置（"{}"，provider 冷启动竞态）或解析失败 → 后台条件式重试。
     */
    private fun resolveConfig(
        packageName: String,
        context: Context,
        param: XposedModuleInterface.PackageLoadedParam,
        attempt: Int
    ) {
        if (attempt > MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, "give up config retry for $packageName after ${attempt - 1} attempts")
            return
        }
        val configJson = StatsProvider.readConfig(context, packageName)
        val config = runCatching { JSONObject(configJson) }.getOrNull()
        val rule = config?.optJSONObject("rules")?.optJSONObject(packageName)

        when {
            rule != null && rule.optBoolean("enabled", false) -> {
                if (attempt > 0) {
                    HookLog.i(TAG, "retry #$attempt got config for $packageName len=${configJson.length}")
                }
                doInstall(packageName, context, param, config, rule)
            }

            // 合法且完整（非空）的配置里没有启用的规则：用户确实没开，终态。
            // 注意不能用"rules 为空对象"判断——read_config 只下发本包规则，
            // 未启用时本就是空 rules，因此以整份是否为 "{}"（provider 异常/竞态）为准
            config != null && configJson != "{}" -> {
                HookLog.i(TAG, "rule disabled for $packageName, skip install")
                settled.add(packageName)
            }

            else -> {
                // 空配置或解析失败：可能正在跨进程写入，条件式后台轮询
                Log.w(TAG, "config unavailable at attempt #$attempt for $packageName, scheduling retry")
                Thread {
                    try {
                        Thread.sleep(RETRY_INTERVAL_MS)
                        resolveConfig(packageName, context, param, attempt + 1)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } catch (t: Throwable) {
                        runCatching { resolveConfig(packageName, context, param, attempt + 1) }
                    }
                }.apply {
                    isDaemon = true
                    name = "acf-config-retry-$packageName"
                }.start()
            }
        }
    }

    private fun doInstall(
        packageName: String,
        context: Context,
        param: XposedModuleInterface.PackageLoadedParam,
        config: JSONObject,
        rule: JSONObject
    ) {
        val seed = rule.optLong("seed", 0L)
        val mode = runCatching {
            ProtectionMode.valueOf(config.optString("mode", ProtectionMode.NOISE.name))
        }.getOrDefault(ProtectionMode.NOISE)
        val enableLogging = config.optBoolean("enable_logging", true)
        // v0.6.0 扩展开关（全局项）：A2 默认开、E1 默认开、D1 默认关（副作用大）
        val hookGetPixel = config.optBoolean("hook_getpixel", true)
        val hookTextMetrics = config.optBoolean("hook_text_metrics", true)
        val hookGlReadPixels = config.optBoolean("hook_glreadpixels", false)

        BitmapHooks.install(
            this, packageName, mode, seed, context, enableLogging, param,
            hookGetPixel, hookTextMetrics, hookGlReadPixels
        )
        settled.add(packageName)
        HookLog.i(
            TAG,
            "hooks installed for $packageName mode=$mode seed=$seed " +
                "h01=$hookGetPixel h05=$hookTextMetrics h02=$hookGlReadPixels"
        )
    }

    /** 宿主 App 的 Application 上下文：仅用于取得 contentResolver 发起跨进程 call。 */
    private fun getHostApplication(): Context? {
        return runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            activityThread.getMethod("currentApplication").invoke(null) as? Context
        }.getOrNull()
    }

    companion object {
        private const val TAG = "ACF-Hook"
        private const val MAX_RETRY_ATTEMPTS = 25
        private const val RETRY_INTERVAL_MS = 200L
    }
}
