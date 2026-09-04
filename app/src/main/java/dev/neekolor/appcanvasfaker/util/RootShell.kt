package dev.neekolor.appcanvasfaker.util

import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * 极简 root shell：单发 `su` 命令，供 SSAID 读写与应用级系统操作（force-stop 等）使用。
 * 不引入 libsu 等依赖、不持有守护进程——只在用户显式触发的操作里调用（须在 IO 线程）。
 */
object RootShell {

    private const val TAG = "RootShell"

    /** su 授权弹窗无人应答或 ROM 异常时的强杀上限。 */
    private const val DEFAULT_TIMEOUT_MS = 20_000L

    data class Result(val exitCode: Int, val stdout: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    private val available by lazy { checkAvailable() }

    /** su 是否可用（首次调用后缓存；首次失败需重启应用重试，UI 应给出指引）。 */
    fun isAvailable(): Boolean = available

    /**
     * 执行一条 shell 命令。命令经 su 进程的 stdin 传入（不拼 `su -c` 引号，
     * 避免 ROM 间 su 实现的引号语义差异）；stderr 合并进 stdout 一起排空，
     * 防止管道缓冲死锁；超时强杀兜底，避免授权弹窗无人应答时永久挂起。
     */
    fun exec(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result = runCatching {
        val process = ProcessBuilder("su")
            .redirectErrorStream(true)
            .start()
        process.outputStream.use { os ->
            os.write((command + "\nexit\n").toByteArray(Charsets.UTF_8))
            os.flush()
        }
        // 本项目命令输出均为 KB 级（远小于管道缓冲），先限时等待再读流是安全的
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            Log.w(TAG, "exec timed out after ${timeoutMs}ms")
            return Result(-1, "su timeout after ${timeoutMs}ms")
        }
        val out = process.inputStream.bufferedReader().readText()
        Result(process.exitValue(), out)
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
