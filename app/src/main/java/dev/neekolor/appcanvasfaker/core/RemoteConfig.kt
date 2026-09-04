package dev.neekolor.appcanvasfaker.core

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService

/**
 * 远端配置通道契约（官方 RemotePreferences 路线，见 libxposed/service + example）：
 * - UI 进程经 [RemoteBridge.remote]（XposedService.getRemotePreferences）写，
 *   hook 进程经 XposedModule.getRemotePreferences（同 [GROUP]）读，
 *   LSPosed 数据库中转，不再依赖目标应用对模块包的可见性。
 * - 配置沿用整份 JSON（[KEY_CONFIG_JSON]，schema 与本地一致），原子读写。
 * - 统计 key 与本地同名（见下方），远端/本地互读不漂移。
 * - 安全说明：token 混淆层随旧 Provider 退役；隔离靠"hook 侧只读自包规则"
 *   的约定（见 LibXposedInit），与旧方案等效，不构成强边界。
 */
object RemoteConfig {
    const val GROUP = "acf_config"
    const val KEY_CONFIG_JSON = "config_json"

    const val KEY_GLOBAL_COUNT = "global_hook_count"
    const val KEY_TODAY_COUNT = "today_hook_count"
    const val KEY_TODAY_DATE = "today_date"
    const val KEY_LOGS = "logs"

    fun pkgCount(pkg: String) = "pkg_${pkg}_count"
    fun pkgHash(pkg: String) = "pkg_${pkg}_last_hash"
    fun pkgLastTime(pkg: String) = "pkg_${pkg}_last_time"
}

/**
 * 框架服务集中持有：Application 在 onServiceBind/onServiceDied 更新，
 * UI 与 hook 侧统一经 [remote] 取远端 prefs（null = 服务未绑定）。
 */
object RemoteBridge {
    @Volatile
    var service: XposedService? = null

    fun remote(): SharedPreferences? =
        runCatching { service?.getRemotePreferences(RemoteConfig.GROUP) }.getOrNull()
}
