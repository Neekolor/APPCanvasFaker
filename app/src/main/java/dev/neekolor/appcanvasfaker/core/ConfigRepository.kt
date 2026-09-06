package dev.neekolor.appcanvasfaker.core

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dev.neekolor.appcanvasfaker.AppCanvasFakerApplication
import dev.neekolor.appcanvasfaker.BuildConfig
import dev.neekolor.appcanvasfaker.scanner.core.StandardCanvas
import dev.neekolor.appcanvasfaker.scanner.fingerprint.HardwareReaders
import dev.neekolor.appcanvasfaker.scanner.fingerprint.NonPixelSignals
import dev.neekolor.appcanvasfaker.scanner.fingerprint.PixelReaders
import dev.neekolor.appcanvasfaker.util.HashUtils
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI 进程数据源：官方 RemotePreferences 路线。
 * - 本地 SharedPreferences（app_canvas_faker）为主：未激活也能配规则；
 * - 服务绑定后远端（LSPosed 数据库，同组 [RemoteConfig.GROUP]）为影：
 *   读优先远端，配置写双写，绑定瞬间本地配置一次性推远端（本地胜出）。
 * - hook 进程只经远端读写（见 LibXposedInit / BitmapHooks），统计 key 与本地同名。
 * 配置 JSON 结构：{ mode, enable_logging, hook_getpixel, hook_text_metrics,
 * hook_glreadpixels, hook_pixelcopy, rules: { pkg: {enabled, seed} } }
 */
class ConfigRepository(private val context: Context) {

    private val localPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 读/单写走远端（绑定时），否则本地；配置保存与清日志额外双写不断档。 */
    private val prefs: SharedPreferences
        get() = RemoteBridge.remote() ?: localPrefs

    /**
     * 统计面（计数/hash/时间/日志/今日）：只读写本地。
     * Hook 进程的远端 prefs 只读（官方约束），统计回写走 StatsReceiver 广播落本地；
     * 远端只剩 config_json 分发。见 ADR D16。
     */
    private val stats: SharedPreferences
        get() = localPrefs

    private val pm: PackageManager get() = context.packageManager

    // ---------- 公开接口（UI 契约） ----------

    fun getRule(pkg: String): AppRule {
        val rule = config().optJSONObject("rules")?.optJSONObject(pkg)
        return AppRule(
            packageName = pkg,
            enabled = rule?.optBoolean("enabled", false) ?: false,
            seed = rule?.optLong("seed", 0L) ?: 0L,
            lastCanvasHash = stats.getString(KEY_PKG_HASH(pkg), null)
                ?.let { HashUtils.foldHash16(it) } ?: "暂无",
            hookCount = stats.getLong(KEY_PKG_COUNT(pkg), 0L),
            lastHookTime = stats.getLong(KEY_PKG_LAST_TIME(pkg), 0L)
        )
    }

    fun setHookEnabled(pkg: String, enabled: Boolean) {
        synchronized(writeLock) {
            val c = config()
            val rules = c.optJSONObject("rules") ?: JSONObject().also { c.put("rules", it) }
            val rule = rules.optJSONObject(pkg) ?: JSONObject().also { rules.put(pkg, it) }
            rule.put("enabled", enabled)
            // 首次启用时生成并持久化独立 seed
            if (rule.optLong("seed", 0L) == 0L) {
                rule.put("seed", newSeed())
            }
            saveConfig(c)
        }
    }

    fun randomizeSeed(pkg: String): Long {
        synchronized(writeLock) {
            val seed = newSeed()
            val c = config()
            val rules = c.optJSONObject("rules") ?: JSONObject().also { c.put("rules", it) }
            val rule = rules.optJSONObject(pkg) ?: JSONObject().also { rules.put(pkg, it) }
            rule.put("seed", seed)
            saveConfig(c)
            if (enableLogging()) {
                appendLogLocked(
                    JSONObject()
                        .put("ts", System.currentTimeMillis())
                        .put("level", "I")
                        .put("tag", "随机化")
                        .put("msg", pkg)
                        .put("pkg", pkg)
                )
            }
            return seed
        }
    }

    fun enabledAppCount(): Int {
        val rules = config().optJSONObject("rules") ?: return 0
        var count = 0
        val it = rules.keys()
        while (it.hasNext()) {
            val rule = rules.optJSONObject(it.next()) ?: continue
            if (rule.optBoolean("enabled", false)) count++
        }
        return count
    }

    /**
     * Hook 统计：全部已配置规则包的次数与末次时间，按次数降序。
     * 读本地统计面（毫秒级），调用方仍建议放后台线程。
     */
    fun hookStats(): List<HookStat> {
        val rules = config().optJSONObject("rules") ?: return emptyList()
        val out = ArrayList<HookStat>()
        val it = rules.keys()
        while (it.hasNext()) {
            val pkg = it.next()
            val rule = rules.optJSONObject(pkg)
            out.add(
                HookStat(
                    packageName = pkg,
                    enabled = rule?.optBoolean("enabled", false) ?: false,
                    count = stats.getLong(KEY_PKG_COUNT(pkg), 0L),
                    lastTime = stats.getLong(KEY_PKG_LAST_TIME(pkg), 0L)
                )
            )
        }
        return out.sortedByDescending { it.count }
    }

    /**
     * Hook 命中落盘（供 StatsReceiver，调用方为 UI 进程广播线程）：
     * 包计数/hash/时间 + 全局/今日 + 可选日志，全部写本地统计面。
     */
    fun recordHookHit(pkg: String, fingerprint: String, timestamp: Long) {
        synchronized(writeLock) {
            val e = stats.edit()
            e.putLong(KEY_PKG_COUNT(pkg), stats.getLong(KEY_PKG_COUNT(pkg), 0L) + 1L)
            e.putString(KEY_PKG_HASH(pkg), fingerprint)
            e.putLong(KEY_PKG_LAST_TIME(pkg), timestamp)
            e.putLong(KEY_GLOBAL_COUNT, stats.getLong(KEY_GLOBAL_COUNT, 0L) + 1L)
            val today = todayStr()
            if (stats.getString(KEY_TODAY_DATE, "") != today) {
                e.putString(KEY_TODAY_DATE, today)
                e.putLong(KEY_TODAY_COUNT, 1L)
            } else {
                e.putLong(KEY_TODAY_COUNT, stats.getLong(KEY_TODAY_COUNT, 0L) + 1L)
            }
            if (config().optBoolean("enable_logging", true)) {
                appendLogLocked(
                    JSONObject()
                        .put("ts", timestamp)
                        .put("level", "I")
                        .put("tag", "Hook")
                        .put("msg", pkg)
                        .put("pkg", pkg)
                )
            }
            e.apply()
        }
    }

    /** 同步快速读取：应用版本名（BuildConfig，无 IO）。 */
    fun versionName(): String = BuildConfig.VERSION_NAME

    /** 同步快速读取：已启用规则数（仅解析本地 JSON，主线程可承受）。 */
    fun hookedAppCountQuick(): Int = enabledAppCount()

    /** 同步快速读取：累计 hook 次数。 */
    fun totalHookCount(): Long = prefs.getLong(KEY_GLOBAL_COUNT, 0L)

    fun mode(): ProtectionMode {
        val name = config().optString("mode", ProtectionMode.NOISE.name)
        return runCatching { ProtectionMode.valueOf(name) }.getOrDefault(ProtectionMode.NOISE)
    }

    fun setMode(mode: ProtectionMode) {
        synchronized(writeLock) {
            val c = config()
            c.put("mode", mode.name)
            saveConfig(c)
        }
    }

    /** 该 app 的本地模拟 hook 后指纹（A1 口径，scanner 画布 + seed 扰动）。纯本地，不走跨进程。 */
    fun simulatedFingerprint(pkg: String): String {
        val rule = getRule(pkg)
        if (rule.seed == 0L) return "暂无"
        val pixels = standardPixels()
        FingerprintEngine.applyPixels(
            pixels, StandardCanvas.WIDTH, StandardCanvas.HEIGHT, 0, StandardCanvas.WIDTH, 0, 0, mode(), rule.seed
        )
        return HashUtils.foldHash16(HashUtils.ofIntArray(pixels))
    }

    /** 7 条读取路径的未污染基准指纹，用于与 hook 后指纹对比。 */
    fun standardFingerprints(): List<FingerprintValue> = collectFingerprints(seed = null)

    /** 该 app 套当前模式扰动后的 7 条读取路径指纹。 */
    fun simulatedFingerprints(pkg: String): List<FingerprintValue> {
        val rule = getRule(pkg)
        if (rule.seed == 0L) return emptyList()
        return collectFingerprints(seed = rule.seed)
    }

    /**
     * 按固定方法计算 7 种标准化指纹（审计 N-12/N-13：全部口径复用 scanner 采集器，
     * 统一 320×160 标准画布，与配套扫描器应用三方互比；A4b 更正为 JPEG 压缩口径）：
     * - A1 getPixels / A3 copyPixelsToBuffer / A4 compress(PNG) / A4b compress(JPEG)
     * - A2 getPixel 单点（A2）/ E1 Paint 文本度量（E1）/ D1 glReadPixels（D1）
     * [seed] 非空时先对画布像素施加扰动，模拟 hook 后状态。
     */
    private fun collectFingerprints(seed: Long?): List<FingerprintValue> {
        val std = runCatching { StandardCanvas.createBitmap() }.getOrNull() ?: return emptyList()
        try {
            if (seed != null) {
                val pixels = IntArray(StandardCanvas.WIDTH * StandardCanvas.HEIGHT)
                std.getPixels(pixels, 0, StandardCanvas.WIDTH, 0, 0, StandardCanvas.WIDTH, StandardCanvas.HEIGHT)
                FingerprintEngine.applyPixels(
                    pixels, StandardCanvas.WIDTH, StandardCanvas.HEIGHT, 0, StandardCanvas.WIDTH, 0, 0, mode(), seed
                )
                std.setPixels(pixels, 0, StandardCanvas.WIDTH, 0, 0, StandardCanvas.WIDTH, StandardCanvas.HEIGHT)
            }
            val error = { e: Throwable -> "异常: ${e.javaClass.simpleName}" }
            val a1 = runCatching { PixelReaders.getPixels(std) }.getOrElse(error)
            val a3 = runCatching { PixelReaders.copyPixelsToBuffer(std) }.getOrElse(error)
            val a4 = runCatching { PixelReaders.compressPng(std) }.getOrElse(error)
            val a4b = runCatching { PixelReaders.compressJpeg(std) }.getOrElse(error)
            val a2 = runCatching { PixelReaders.getPixel(std) }.getOrElse(error)
            val e1 = runCatching { NonPixelSignals.fontMetrics() }.getOrElse(error)
            val d1 = runCatching { HardwareReaders.glReadPixels() }.getOrElse(error)
            return listOf(
                FingerprintValue("A1", "像素直读（getPixels）", foldIfHash(a1)),
                FingerprintValue("A3", "缓冲拷贝（copyPixelsToBuffer）", foldIfHash(a3)),
                FingerprintValue("A4", "PNG 压缩（compress）", foldIfHash(a4)),
                FingerprintValue("A4b", "JPEG 压缩（compress）", foldIfHash(a4b)),
                FingerprintValue("A2", "单点读取（getPixel）", foldIfHash(a2)),
                FingerprintValue("E1", "文本度量（Paint）", foldIfHash(e1)),
                FingerprintValue("D1", "GL 直读（glReadPixels）", foldIfHash(d1)),
            )
        } finally {
            std.recycle()   // 审计 N-15：异常路径也确保回收
        }
    }

    private fun standardPixels(): IntArray {
        val bmp = StandardCanvas.createBitmap()
        val pixels = IntArray(StandardCanvas.WIDTH * StandardCanvas.HEIGHT)
        try {
            bmp.getPixels(pixels, 0, StandardCanvas.WIDTH, 0, 0, StandardCanvas.WIDTH, StandardCanvas.HEIGHT)
        } finally {
            bmp.recycle()
        }
        return pixels
    }

    /** scanner 采集器成功时返回 64 位 SHA-256 hex，折叠为 16 位；失败文本原样透出（审计 N-14：大小写判定统一）。 */
    private fun foldIfHash(raw: String): String {
        val lower = raw.lowercase()
        return if (lower.length == 64 && lower.all { it in "0123456789abcdef" }) {
            HashUtils.foldHash16(lower)
        } else {
            raw
        }
    }

    fun enableLogging(): Boolean = config().optBoolean("enable_logging", true)

    fun setEnableLogging(enabled: Boolean) {
        synchronized(writeLock) {
            val c = config()
            c.put("enable_logging", enabled)
            saveConfig(c)
        }
    }

    // ---------- v0.6.0 Hook 扩展开关（全局项，随 read_config 最小下发） ----------

    /** A2 getPixel 单点读取：现存裸露缺口（scanner A2），默认开。 */
    fun hookGetPixel(): Boolean = config().optBoolean("hook_getpixel", true)

    fun setHookGetPixel(enabled: Boolean) {
        synchronized(writeLock) {
            val c = config()
            c.put("hook_getpixel", enabled)
            saveConfig(c)
        }
    }

    /** E1 Paint 文本度量族：结构性逃逸口（scanner E1），默认开；排版异常时可关。 */
    fun hookTextMetrics(): Boolean = config().optBoolean("hook_text_metrics", true)

    fun setHookTextMetrics(enabled: Boolean) {
        synchronized(writeLock) {
            val c = config()
            c.put("hook_text_metrics", enabled)
            saveConfig(c)
        }
    }

    /** D1 GLES20.glReadPixels GPU 直读（scanner D1）：默认关——会扰动目标应用自身的 GL 读回（游戏录像/推流等）。 */
    fun hookGlReadPixels(): Boolean = config().optBoolean("hook_glreadpixels", false)

    fun setHookGlReadPixels(enabled: Boolean) {
        synchronized(writeLock) {
            val c = config()
            c.put("hook_glreadpixels", enabled)
            saveConfig(c)
        }
    }

    /** C2 PixelCopy.request 监听器包装（scanner C2 延迟持有例外）：默认开；截图分享类场景异常时可关。 */
    fun hookPixelCopy(): Boolean = config().optBoolean("hook_pixelcopy", true)

    fun setHookPixelCopy(enabled: Boolean) {
        synchronized(writeLock) {
            val c = config()
            c.put("hook_pixelcopy", enabled)
            saveConfig(c)
        }
    }

    /** 追加一条日志并裁剪到上限。调用方必须已持有 [writeLock]。 */
    private fun appendLogLocked(entry: JSONObject) {
        val arr = logsArray()
        arr.put(entry)
        while (arr.length() > MAX_LOGS) arr.remove(0)
        stats.edit().putString(KEY_LOGS, arr.toString()).apply()
    }

    fun getLogs(): List<LogEntry> {
        val arr = logsArray()
        val out = ArrayList<LogEntry>(arr.length())
        for (i in 0 until arr.length()) {
            runCatching {
                val o = arr.getJSONObject(i)
                out.add(
                    LogEntry(
                        timestamp = o.optLong("ts", 0L),
                        level = o.optString("level", "I"),
                        tag = o.optString("tag", "ACF-Hook"),
                        message = o.optString("msg", ""),
                        packageName = o.optString("pkg").ifBlank { null }
                    )
                )
            }
        }
        return out
    }

    fun clearLogs() {
        synchronized(writeLock) {
            // 统计面只在本地：清本地即全清（远端不存统计）
            localPrefs.edit().putString(KEY_LOGS, "[]").apply()
        }
    }

    fun snapshot(): ModuleSnapshot {
        return ModuleSnapshot(
            moduleActive = isFrameworkActive(),
            frameworkName = "libxposed",
            frameworkApi = "102",
            totalHookCount = stats.getLong(KEY_GLOBAL_COUNT, 0L),
            todayHookCount = todayCount(),
            widevineId = widevineId(),
            versionName = BuildConfig.VERSION_NAME,
            buildType = BuildConfig.BUILD_TYPE,
            mode = mode(),
            logs = getLogs().map { formatLog(it) }
        )
    }

    fun getInstalledApps(
        query: String,
        showSystem: Boolean,
        reverse: Boolean,
        sortMode: String
    ): List<InstalledApp> {
        return runCatching {
            val list = pm.getInstalledPackages(0)
                .filter { pkgInfo ->
                    val flags = pkgInfo.applicationInfo?.flags ?: 0
                    showSystem || (flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }
                .mapNotNull { pkgInfo ->
                    val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                    InstalledApp(
                        label = pm.getApplicationLabel(appInfo).toString(),
                        packageName = pkgInfo.packageName,
                        isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        firstInstallTime = pkgInfo.firstInstallTime,
                        lastUpdateTime = pkgInfo.lastUpdateTime,
                        rule = getRule(pkgInfo.packageName)
                    )
                }
                .filter {
                    query.isBlank() ||
                        it.label.contains(query, true) ||
                        it.packageName.contains(query, true)
                }
            val sorted = list.sortedWith { a, b ->
                val rankA = if (a.rule.enabled) 0 else 1
                val rankB = if (b.rule.enabled) 0 else 1
                if (rankA != rankB) {
                    // 有 Hook 的永远排最前，不受倒序影响（同上游 ROOT 管理器的置顶逻辑）
                    rankA - rankB
                } else {
                    val cmp = when (sortMode) {
                        "package_name" -> a.packageName.compareTo(b.packageName)
                        "install_time" -> a.firstInstallTime.compareTo(b.firstInstallTime)
                        "update_time" -> a.lastUpdateTime.compareTo(b.lastUpdateTime)
                        else -> a.label.lowercase(Locale.ROOT).compareTo(b.label.lowercase(Locale.ROOT))
                    }
                    if (reverse) -cmp else cmp
                }
            }
            sorted
        }.getOrElse { emptyList() }
    }

    // ---------- 内部 ----------

    private fun config(): JSONObject {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return defaultConfig()
        return runCatching { JSONObject(raw) }.getOrElse { defaultConfig() }
    }

    private fun saveConfig(c: JSONObject) {
        // 配置双写：本地不断档，远端（若绑定）即时同步供 hook 侧读取
        localPrefs.edit().putString(KEY_CONFIG_JSON, c.toString()).apply()
        RemoteBridge.remote()?.edit()?.putString(KEY_CONFIG_JSON, c.toString())?.apply()
    }

    /**
     * 绑定瞬间把本地配置推远端（本地胜出）：覆盖"未激活时配好规则、
     * 激活后远端还是空"的断档。只推 config_json；统计面本就只在本地。
     */
    fun pushLocalConfigToRemote() {
        val remote = RemoteBridge.remote() ?: return
        val local = localPrefs.getString(KEY_CONFIG_JSON, null) ?: return
        runCatching {
            remote.edit()?.putString(KEY_CONFIG_JSON, local)?.apply()
        }
    }

    private fun defaultConfig(): JSONObject = JSONObject().apply {
        put("mode", ProtectionMode.NOISE.name)
        put("enable_logging", true)
        put("hook_getpixel", true)
        put("hook_text_metrics", true)
        put("hook_glreadpixels", false)
        put("hook_pixelcopy", true)
        put("rules", JSONObject())
    }

    private fun logsArray(): JSONArray = runCatching {
        JSONArray(stats.getString(KEY_LOGS, "[]"))
    }.getOrElse { JSONArray() }

    private fun todayCount(): Long = synchronized(writeLock) {
        val today = todayStr()
        if (stats.getString(KEY_TODAY_DATE, "") != today) {
            stats.edit().putString(KEY_TODAY_DATE, today).putLong(KEY_TODAY_COUNT, 0L).apply()
            return@synchronized 0L
        }
        stats.getLong(KEY_TODAY_COUNT, 0L)
    }

    private fun todayStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    private fun newSeed(): Long {
        var s = SecureRandom().nextLong()
        if (s == 0L) s = 0x9E3779B97F4A7C15uL.toLong()
        return s
    }

    private fun isFrameworkActive(): Boolean {
        val app = runCatching {
            context.applicationContext as? AppCanvasFakerApplication
        }.getOrNull()
        return app?.xposedService != null
    }

    private fun widevineId(): String = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val m = clazz.getMethod("get", String::class.java)
        m.invoke(null, "ro.boot.widevine_id") as? String
    }.getOrNull() ?: "unknown"

    private fun formatLog(e: LogEntry): String {
        val time = runCatching {
            SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(Date(e.timestamp))
        }.getOrDefault(e.timestamp.toString())
        return "$time | ${e.tag} | ${e.message}"
    }

    companion object {
        const val PREFS_NAME = "app_canvas_faker"
        const val KEY_CONFIG_JSON = RemoteConfig.KEY_CONFIG_JSON
        // 统计 key 名与远端同组（RemoteConfig 唯一口径），但只存本地：
        // Hook 侧远端只读，统计回写走 StatsReceiver 广播落本地（ADR D16）
        private const val KEY_LOGS = RemoteConfig.KEY_LOGS
        private const val KEY_GLOBAL_COUNT = RemoteConfig.KEY_GLOBAL_COUNT
        private const val KEY_TODAY_COUNT = RemoteConfig.KEY_TODAY_COUNT
        private const val KEY_TODAY_DATE = RemoteConfig.KEY_TODAY_DATE
        private const val MAX_LOGS = 1000

        /**
         * 写锁：配置 JSON 与日志数组都是"读出→内存修改→整份写回"模式。
         * 审计 N-09：本类被各 ViewModel/Provider/AboutScreen 多实例化，
         * 锁必须是全局单例——实例级锁锁不住跨实例的并发读改写。
         */
        private val writeLock = Any()

        private fun KEY_PKG_COUNT(pkg: String) = RemoteConfig.pkgCount(pkg)
        private fun KEY_PKG_HASH(pkg: String) = RemoteConfig.pkgHash(pkg)
        private fun KEY_PKG_LAST_TIME(pkg: String) = RemoteConfig.pkgLastTime(pkg)
    }
}