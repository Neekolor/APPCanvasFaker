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

    private val readerPool = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "acf-su-read").apply { isDaemon = true }
    }

    data class Result(val exitCode: Int, val stdout: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    /** 包名拼入 shell 前的单引号包裹（与调用方正则校验双层防御）。 */
    fun shellQuote(s: String) = "'${s.replace("'", "'\\''")}'"

    @Volatile
    private var available: Boolean? = null

    /** su 是否可用；forceRefresh 跳过缓存（补授权后重试用）。 */
    @Synchronized
    fun isAvailable(forceRefresh: Boolean = false): Boolean {
        if (forceRefresh || available == null) {
            available = checkAvailable()
        }
        return available == true
    }

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
        // 输出流必须并发消费：大输出会塞满管道阻塞 su 进程，先 waitFor 必超时
        val outFuture = readerPool.submit<String> { process.inputStream.bufferedReader().readText() }
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            outFuture.cancel(true)
            Log.w(TAG, "exec timed out after ${timeoutMs}ms")
            return Result(-1, "su timeout after ${timeoutMs}ms")
        }
        val out = runCatching { outFuture.get(10, TimeUnit.SECONDS) }.getOrDefault("")
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
