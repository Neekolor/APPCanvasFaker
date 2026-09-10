package dev.neekolor.appcanvasfaker.core

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新（设置 → 关于 → 检查更新，+ 启动后自动检查）。
 *
 * 来源：GitHub Releases（https://github.com/Neekolor/APPCanvasFaker/releases）：
 * 查 releases/latest 拿最新 tag → 与本地版本归一化比对 → 有新版弹"是否下载" →
 * 下载 APK 到缓存 → 调系统安装器（需用户点确认，Xposed 模块无静默升级能力；
 * 按包名覆盖安装，LSPosed 作用域勾选保留）。
 *
 * 单例 + application context，无泄漏；关于页手动触发与启动自动检查共用同一状态，
 * 避免重复下载。网络失败只落 Failed 状态、不抛不崩（api.github.com 国内直连不稳）。
 */
object UpdateCenter {

    const val RELEASES_API = "https://api.github.com/repos/Neekolor/APPCanvasFaker/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000

    sealed interface UiState {
        data object Idle : UiState
        data object Checking : UiState
        data class Latest(val current: String) : UiState
        data class Available(val tag: String, val sizeText: String, val apkUrl: String) : UiState
        data class Failed(val reason: String) : UiState
        data class Downloading(val progress: Float) : UiState
        data class ReadyToInstall(val file: File, val tag: String) : UiState
    }

    private val _ui = MutableStateFlow<UiState>(UiState.Idle)
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun dismiss() {
        _ui.value = UiState.Idle
    }

    /**
     * 检查新版。manual = 关于页手动点（无新版也弹"已是最新"）；
     * 自动检查仅在有新版时弹窗；无新版/失败都静默，避免每次启动都打断用户。
     * 注：自动检查的失败也静默，只记 logcat。
     */
    suspend fun check(app: Application, manual: Boolean) {
        if (_ui.value is UiState.Checking || _ui.value is UiState.Downloading) return
        _ui.value = UiState.Checking
        val current = app.versionName()
        // 调用方是 Main dispatcher 的 scope，网络必须切 IO（否则 NetworkOnMainThreadException）
        val result: UiState.Available? = withContext(Dispatchers.IO) {
            runCatching { fetchLatest(current) }.getOrElse {
                val reason = it.message ?: it.javaClass.simpleName
                android.util.Log.w("UpdateCenter", "check failed: $reason")
                if (manual) _ui.value = UiState.Failed(reason) else _ui.value = UiState.Idle
                return@withContext null
            }
        }
        // 检查期间用户可能已关闭弹窗：只在仍是 Checking 时落结果，不复活已关闭的弹窗
        if (_ui.value is UiState.Checking) {
            _ui.value = result ?: if (manual) UiState.Latest(current) else UiState.Idle
        }
    }

    /** null = 已是最新；Available = 有新版（含 tag/大小/直链）。 */
    private fun fetchLatest(currentVersion: String): UiState.Available? {
        val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ACF-UpdateChecker")
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP $code")
            }
            if (conn.url.protocol.lowercase() != "https") {
                throw IllegalStateException("redirect to non-https")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "").trim()
            if (tag.isEmpty()) throw IllegalStateException("empty tag_name")
            // 只判相等会诱导降级：远端必须严格大于本地才算新版
            val remote = normalizeVersion(tag)
            val local = normalizeVersion(currentVersion)
            if (remote == local || !isNewerThan(remote, local)) return null
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkSize = 0L
            if (assets != null) {
                // 多个 APK 附件时优先 release 包，其次第一个 .apk
                var fallbackUrl: String? = null
                var fallbackSize = 0L
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val name = a.optString("name", "")
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    val url = a.optString("browser_download_url", "")
                    if (url.isEmpty()) continue
                    if (fallbackUrl == null) {
                        fallbackUrl = url
                        fallbackSize = a.optLong("size", 0L)
                    }
                    if ("release" in name.lowercase()) {
                        apkUrl = url
                        apkSize = a.optLong("size", 0L)
                        break
                    }
                }
                if (apkUrl == null) {
                    apkUrl = fallbackUrl
                    apkSize = fallbackSize
                }
            }
            if (apkUrl.isNullOrEmpty()) throw IllegalStateException("release 无 APK 附件")
            return UiState.Available(tag, formatSize(apkSize), apkUrl)
        } finally {
            conn.disconnect()
        }
    }

    /** 版本归一化：去首尾空白、前导 v/V、-dev/-beta 等后缀；0.8.7-dev 与 v0.8.7 视为同版。 */
    private fun normalizeVersion(v: String): String {
        var s = v.trim()
        if (s.startsWith("v", ignoreCase = true)) s = s.substring(1)
        val dash = s.indexOf('-')
        if (dash > 0) s = s.substring(0, dash)
        return s
    }

    /** 数值逐段比较（1.10 > 1.9 必须按数字而非字符串）；段数不同缺位按 0。 */
    private fun isNewerThan(remote: String, local: String): Boolean {
        val r = remote.split('.')
        val l = local.split('.')
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            val lv = l.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "?"
        val mb = bytes / 1048576.0
        return if (mb >= 1) "%.1f MB".format(mb) else "%d KB".format(bytes / 1024)
    }

    private fun Application.versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** 下载 APK 到缓存（前台协程 + 进度回调，无需 DownloadManager/额外 Receiver）。 */
    suspend fun download(app: Application, url: String, tag: String) {
        _ui.value = UiState.Downloading(0f)
        val file = withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "update").apply { mkdirs() }
            val out = File(dir, "ACF-${tag.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk")
            // 缓存目录只留当前版本：旧包不自动清会越堆越多（系统回收 cache 不可靠）
            dir.listFiles()?.forEach { if (it.isFile && it.name != out.name) runCatching { it.delete() } }
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "ACF-UpdateChecker")
                instanceFollowRedirects = true
            }
            try {
                val total = conn.contentLengthLong.takeIf { it > 0 }
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total != null) {
                                _ui.value = UiState.Downloading((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                        // 服务端声明长度与实际不符即截断/注水，不进安装器
                        if (total != null && done != total) {
                            throw IllegalStateException("size mismatch ($done != $total)")
                        }
                    }
                }
            } catch (t: Throwable) {
                out.delete()
                throw t
            } finally {
                conn.disconnect()
            }
            out
        }
        _ui.value = UiState.ReadyToInstall(file, tag)
    }

    /** 下载失败时落 Failed（调用方 runCatching 包住 download）。 */
    suspend fun downloadGuarded(app: Application, url: String, tag: String) {
        runCatching { download(app, url, tag) }.onFailure {
            _ui.value = UiState.Failed(it.message ?: it.javaClass.simpleName)
        }
    }

    /** 调系统安装器（未知来源授权由系统引导一次）。 */
    fun install(app: Application, file: File) {
        val uri = FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.startActivity(intent)
        _ui.value = UiState.Idle
    }
}
