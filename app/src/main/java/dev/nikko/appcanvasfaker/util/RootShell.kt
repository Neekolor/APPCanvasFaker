package dev.nikko.appcanvasfaker.util

import android.util.Log

/**
 * 极简 root shell：单发 `su` 命令，供 SSAID 读写与应用级系统操作（force-stop 等）使用。
 * 不引入 libsu 等依赖、不持有守护进程——只在用户显式触发的操作里调用（须在 IO 线程）。
 */
object RootShell {

    private const val TAG = "RootShell"

    data class Result(val exitCode: Int, val stdout: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    private val available by lazy { checkAvailable() }

    /** su 是否可用（首次调用后缓存；授权弹窗只出现一次）。 */
    fun isAvailable(): Boolean = available

    /**
     * 执行一条 shell 命令。命令经 su 进程的 stdin 传入（不拼 `su -c` 引号，
     * 避免 ROM 间 su 实现的引号语义差异）；无 root 或异常时返回非零码。
     */
    fun exec(command: String): Result = runCatching {
        val process = ProcessBuilder("su").start()
        process.outputStream.use { os ->
            os.write((command + "\nexit\n").toByteArray(Charsets.UTF_8))
            os.flush()
        }
        val out = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        Result(code, out)
    }.getOrElse {
        Log.w(TAG, "exec failed: ${it.javaClass.simpleName}: ${it.message}")
        Result(-1, "su error: ${it.javaClass.simpleName}: ${it.message}")
    }

    private fun checkAvailable(): Boolean {
        val r = exec("id")
        val ok = r.isSuccess && "uid=0" in r.stdout
        if (!ok) {
            Log.w(TAG, "su check failed: code=${r.exitCode} out=${r.stdout.take(120)}")
        }
        return ok
    }
}
