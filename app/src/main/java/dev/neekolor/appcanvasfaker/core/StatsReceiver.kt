package dev.neekolor.appcanvasfaker.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Hook 命中接收器（hook → UI 统计通道）。
 *
 * 背景：远端 prefs 在被 Hook 进程是只读实现（官方 API 注明，v0.8.3 切通道后
 * Hook 侧写入全灭、计数恒 0，见 ADR D16），Hook 进程也无其他可写共享通道，
 * 故统计回写改走本接收器：Hook 侧发显式广播，本进程收后写本地 prefs。
 *
 * 到达性说明：显式广播可唤醒本进程；本应用被强停期间的命中会丢失
 * （与既有"允许轻度丢失"口径一致）。伪造说明：Hook 代码即目标应用本身，
 * 任何可写通道都无法区分"真实命中"与"目标伪造"，计数仅供参考；
 * 破坏面仅限计数/哈希展示，无安全边界可越。
 */
class StatsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_HOOK_HIT) return
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return
        val hash = intent.getStringExtra(EXTRA_HASH) ?: return
        val ts = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
        runCatching {
            ConfigRepository(context.applicationContext).recordHookHit(pkg, hash, ts)
        }
    }

    companion object {
        const val ACTION_HOOK_HIT = "dev.neekolor.appcanvasfaker.action.HOOK_HIT"
        const val EXTRA_PKG = "pkg"
        const val EXTRA_HASH = "hash"
        const val EXTRA_SEED = "seed"
        const val EXTRA_TIME = "time"
    }
}
