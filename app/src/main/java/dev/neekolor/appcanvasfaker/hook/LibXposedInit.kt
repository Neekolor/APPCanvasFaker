package dev.neekolor.appcanvasfaker.hook

import android.content.SharedPreferences
import android.util.Log
import dev.neekolor.appcanvasfaker.core.ProtectionMode
import dev.neekolor.appcanvasfaker.core.RemoteBridge
import dev.neekolor.appcanvasfaker.core.RemoteConfig
import dev.neekolor.appcanvasfaker.util.HookLog
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.json.JSONObject

/**
 * libxposed 102 入口：官方 RemotePreferences 路线。
 * 配置经框架远端 prefs（LSPosed 数据库中转）直读，不再走自建 ContentProvider，
 * 因此不依赖目标应用对模块包的可见性，也不需要宿主 Context。
 *
 * 安装时序：每个包仅第一个到达者解析（gate 去重）；远端无配置/解析失败/
 * 规则未启用均为终态（不再轮询——框架侧数据恒可用，失败只可能是真异常，
 * 直接 Log.e 常驻留痕）。配置变更需重启目标应用生效（v1 不做热装，
 * 官方亦明确 hotReload 不应用于配置下发）。
 *
 * 注意：现代 API 下模块自身永远不会被注入（框架设计），主页哈希卡只能
 * 显示本机未污染基准值，属预期行为。
 */
class LibXposedInit : XposedModule() {

    /** 解析流程去重：putIfAbsent 成功者负责该包从读取到终态的全过程。 */
    private val gates = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            HookLog.i(TAG, "onPackageLoaded entry pkg=${param.packageName}")
            tryInstall(param.packageName, param)
        } catch (t: Throwable) {
            Log.e(TAG, "onPackageLoaded failed for ${param.packageName}", t)
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        try {
            tryInstall(param.packageName, param)
        } catch (t: Throwable) {
            Log.e(TAG, "onPackageReady failed for ${param.packageName}", t)
        }
    }

    /** 每个包仅第一个到达者启动解析流程，后续触发直接返回。 */
    private fun tryInstall(
        packageName: String,
        param: XposedModuleInterface.PackageLoadedParam
    ) {
        if (gates.putIfAbsent(packageName, true) != null) return
        resolveConfig(packageName, param)
    }

    private fun resolveConfig(
        packageName: String,
        param: XposedModuleInterface.PackageLoadedParam
    ) {
        val prefs = runCatching { getRemotePreferences(RemoteConfig.GROUP) }
            .onFailure { Log.e(TAG, "remote prefs unavailable for $packageName", it) }
            .getOrNull() ?: return
        val configJson = runCatching {
            prefs.getString(RemoteConfig.KEY_CONFIG_JSON, null)
        }.onFailure { Log.e(TAG, "remote config read failed for $packageName", it) }
            .getOrNull()
        if (configJson.isNullOrBlank()) {
            HookLog.i(TAG, "no remote config for $packageName, skip install")
            return
        }
        val config = runCatching { JSONObject(configJson) }
            .onFailure { Log.e(TAG, "remote config parse failed for $packageName", it) }
            .getOrNull() ?: return
        val rule = config.optJSONObject("rules")?.optJSONObject(packageName)
        if (rule != null && rule.optBoolean("enabled", false)) {
            doInstall(packageName, param, prefs, config, rule)
        } else {
            HookLog.i(TAG, "rule disabled for $packageName, skip install")
        }
    }

    private fun doInstall(
        packageName: String,
        param: XposedModuleInterface.PackageLoadedParam,
        prefs: SharedPreferences,
        config: JSONObject,
        rule: JSONObject
    ) {
        val seed = rule.optLong("seed", 0L)
        val mode = runCatching {
            ProtectionMode.valueOf(config.optString("mode", ProtectionMode.NOISE.name))
        }.getOrDefault(ProtectionMode.NOISE)
        val enableLogging = config.optBoolean("enable_logging", true)
        // v0.6.0 扩展开关（全局项）：H-01 默认开、H-05 默认开、H-02 默认关（副作用大）
        val hookGetPixel = config.optBoolean("hook_getpixel", true)
        val hookTextMetrics = config.optBoolean("hook_text_metrics", true)
        val hookGlReadPixels = config.optBoolean("hook_glreadpixels", false)
        // v0.8.4 C2 监听器包装开关（全局项）：默认开——应用内 PixelCopy 极少承载用户可见内容
        val hookPixelCopy = config.optBoolean("hook_pixelcopy", true)

        BitmapHooks.install(
            this, packageName, mode, seed, prefs, enableLogging, param,
            hookGetPixel, hookTextMetrics, hookGlReadPixels, hookPixelCopy
        )
        HookLog.i(
            TAG,
            "hooks installed for $packageName mode=$mode seed=$seed " +
                "h01=$hookGetPixel h05=$hookTextMetrics h02=$hookGlReadPixels pcopy=$hookPixelCopy"
        )
    }

    companion object {
        private const val TAG = "ACF-Hook"
    }
}
