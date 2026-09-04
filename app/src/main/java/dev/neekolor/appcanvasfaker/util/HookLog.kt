package dev.neekolor.appcanvasfaker.util

import android.util.Log
import dev.neekolor.appcanvasfaker.BuildConfig

/**
 * Hook 侧分级日志：
 * - 流程类 [i]：仅 debug 构建输出，release 静默（避免向 logcat 泄露 hook 活动轨迹）；
 * - [w]/[e]：异常与告警始终输出，是线上排障的唯一线索，任何情况下保留。
 */
object HookLog {

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (tr == null) Log.w(tag, msg) else Log.w(tag, msg, tr)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr == null) Log.e(tag, msg) else Log.e(tag, msg, tr)
    }
}
