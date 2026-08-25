package dev.nikko.appcanvasfaker.hook

import dev.nikko.appcanvasfaker.util.HookLog
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import dev.nikko.appcanvasfaker.core.ConfigRepository
import dev.nikko.appcanvasfaker.core.ProtectionMode
import org.json.JSONObject

/**
 * 数据桥：UI/hook 跨进程读写模块 SharedPreferences（与 ConfigRepository 共用）。
 * 注册在模块包，multiprocess=false，contentResolver.call 跨进程路由到模块进程。
 *
 * 鉴权分层（token 为公开 APK 中的静态常量，仅作混淆层，不构成安全边界）：
 * - record_hook：调用方 uid 必须真实拥有所报包名，防止任意第三方 App 伪造他人统计；
 * - read_config：只下发全局设置 + 调用方自身（经 uid 归属校验）的一条规则，
 *   不再整份下发配置，杜绝第三方枚举所有应用的 seed 与启用状态；
 * - write_config / clear_logs：仅模块自身 uid 可调用，防外部篡改配置与日志。
 */
class StatsProvider : ContentProvider() {

    private lateinit var repo: ConfigRepository

    override fun onCreate(): Boolean {
        repo = ConfigRepository(requireContext())
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (extras?.getString(KEY_TOKEN) != TOKEN) return null

        return when (method) {
            METHOD_RECORD_HOOK -> {
                // 威胁模型：token 仅混淆层；追加 uid 归属校验——
                // 只允许应用为自己的包名记录统计，注入他人数据直接忽略
                val pkg = extras.getString("packageName").orEmpty()
                val fingerprint = extras.getString("fingerprint").orEmpty()
                android.util.Log.i("ACF-Provider", "record_hook pkg=$pkg fp=$fingerprint")
                if (pkg.isNotBlank() && fingerprint.isNotBlank() && uidOwnsPackage(pkg)) {
                    repo.recordHook(
                        pkg = pkg,
                        modeName = extras.getString("mode").orEmpty(),
                        seed = extras.getLong("seed", 0L),
                        fingerprint = fingerprint,
                        timestamp = extras.getLong("timestamp", System.currentTimeMillis()),
                        enableLogging = extras.getBoolean("enable_logging", true)
                    )
                    android.util.Log.i("ACF-Provider", "record_hook done pkg=$pkg")
                }
                Bundle().apply { putBoolean("ok", true) }
            }

            METHOD_CLEAR_LOGS -> {
                // 仅模块自身 UID（UI 进程）可清日志
                if (!isCallerModule()) return null
                repo.clearLogs()
                Bundle().apply { putBoolean("ok", true) }
            }

            METHOD_WRITE_CONFIG -> {
                // 仅模块自身 UID（UI 进程）可写配置，防外部 App 篡改伪造规则
                if (!isCallerModule()) return null
                extras.getString(KEY_CONFIG_JSON)?.let { repo.writeConfigRaw(it) }
                Bundle().apply { putBoolean("ok", true) }
            }

            METHOD_READ_CONFIG -> {
                Bundle().apply { putString(KEY_CONFIG_JSON, filteredConfigFor(arg)) }
            }

            else -> null
        }
    }

    /** 调用方 uid 是否真实拥有指定包名。 */
    private fun uidOwnsPackage(pkg: String): Boolean =
        runCatching {
            requireContext().packageManager.getPackagesForUid(Binder.getCallingUid())
                ?.contains(pkg) == true
        }.getOrDefault(false)

    /**
     * 过滤后的配置视图：mode / enable_logging 全局项原样保留；
     * rules 仅在 [requestedPkg] 非空且归属校验通过时包含该包一条。
     * 包名缺失或不归属时 rules 为空对象，Hook 侧按"未启用"处理。
     */
    private fun filteredConfigFor(requestedPkg: String?): String {
        val full = runCatching { JSONObject(repo.configJson()) }.getOrElse {
            // 解析失败绝不能把原始整份配置外发，宁可让 Hook 侧重试
            return "{}"
        }
        val rules = JSONObject()
        if (!requestedPkg.isNullOrBlank() && uidOwnsPackage(requestedPkg)) {
            full.optJSONObject("rules")?.optJSONObject(requestedPkg)?.let { rules.put(requestedPkg, it) }
        }
        return JSONObject()
            .put("mode", full.optString("mode", ProtectionMode.NOISE.name))
            .put("enable_logging", full.optBoolean("enable_logging", true))
            .put("rules", rules)
            .toString()
    }

    /** 调用方必须是模块自身进程（UI）。 */
    private fun isCallerModule(): Boolean =
        Binder.getCallingUid() == requireContext().applicationInfo.uid

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "dev.nikko.appcanvasfaker.stats"

        private const val KEY_TOKEN = "token"
        private const val TOKEN = "acf_hook_stats_token_v1"
        private const val KEY_CONFIG_JSON = "config_json"
        private const val METHOD_RECORD_HOOK = "record_hook"
        private const val METHOD_CLEAR_LOGS = "clear_logs"
        private const val METHOD_WRITE_CONFIG = "write_config"
        private const val METHOD_READ_CONFIG = "read_config"

        /** hook 进程调用：回写统计（跨进程 ContentProvider call）。 */
        fun recordHook(
            context: Context?,
            packageName: String,
            mode: String,
            seed: Long,
            fingerprint: String,
            enableLogging: Boolean = true
        ) {
            context ?: return
            runCatching {
                context.contentResolver.call(
                    Uri.parse("content://$AUTHORITY"),
                    METHOD_RECORD_HOOK,
                    null,
                    Bundle().apply {
                        putString(KEY_TOKEN, TOKEN)
                        putString("packageName", packageName)
                        putString("mode", mode)
                        putLong("seed", seed)
                        putLong("timestamp", System.currentTimeMillis())
                        putString("fingerprint", fingerprint)
                        putBoolean("enable_logging", enableLogging)
                    }
                )
            }
        }

        /**
         * hook 进程调用：读取"本包专属"配置（跨进程 ContentProvider call）。
         * [packageName] 经 arg 传给 Provider 做 uid 归属校验，只返回全局项与本包规则。
         */
        fun readConfig(context: Context?, packageName: String): String {
            context ?: return "{}"
            return runCatching {
                val result = context.contentResolver.call(
                    Uri.parse("content://$AUTHORITY"),
                    METHOD_READ_CONFIG,
                    packageName,
                    Bundle().apply { putString(KEY_TOKEN, TOKEN) }
                )
                result?.getString(KEY_CONFIG_JSON) ?: "{}"
            }.getOrElse { "{}" }
        }

        fun clearLogs(context: Context?) {
            context ?: return
            runCatching {
                context.contentResolver.call(
                    Uri.parse("content://$AUTHORITY"),
                    METHOD_CLEAR_LOGS,
                    null,
                    Bundle().apply { putString(KEY_TOKEN, TOKEN) }
                )
            }
        }
    }
}
