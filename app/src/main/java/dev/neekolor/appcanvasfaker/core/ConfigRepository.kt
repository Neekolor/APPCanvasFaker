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
 * - 本地 SharedPreferences（app_canvas_faker）是唯一真实来源：UI 读一律走本地；
 * - 远端（LSPosed 数据库，同组 [RemoteConfig.GROUP]）只是分发给 Hook 进程的副本：
 *   配置写双写，绑定瞬间本地配置一次性推远端（本地胜出），UI 永不读远端
 *   （远端读可能返回过期快照，见 ADR D16）。
 * - hook 进程经远端读配置（见 LibXposedInit），统计经广播回本进程落本地。
 * 配置 JSON 结构：{ mode, enable_logging, hook_getpixel, hook_text_metrics,
 * hook_glreadpixels, hook_pixelcopy, rules: { pkg: {enabled, seed} } }
 */
class ConfigRepository(private val context: Context) {

    private val localPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * UI 侧一律读本地：本地是唯一真实来源（每次保存双写，绑定瞬间推远端，本地胜出），
     * 远端只是分发给 Hook 进程的副本——实测远端读在绑定后可能返回过期快照
     * （关日志后日志页横幅出不来的根因），UI 读远端会被带偏。远端只写不读。
     */
    private val prefs: SharedPreferences
        get() = localPrefs

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
                        .put("seed", seed)
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
     *
     * 日志富化字段全部取现成值、零新增计算：seed/path 由 Hook 侧广播带来；
     * moved 为新 hash 与上次落盘 hash 的字符串比较；count 为本次累加后的值；
     * mode 为当前保护模式。hash 只存前后各 8 位（位移判断够用，不膨胀 prefs）。
     */
    fun recordHookHit(pkg: String, fingerprint: String, seed: Long, path: String?, timestamp: Long) {
        // 广播发送方可伪造：包名/哈希格式不对直接丢，时间戳钳位，键空间才有界
        if (pkg.length > 224 || !PKG_PATTERN.matches(pkg)) return
        if (fingerprint.length != 64 || !fingerprint.all { it in '0'..'9' || it in 'a'..'f' }) return
        // 路径只收自家三条链；无规则（未启用）包不记：真流量恒有启用规则，误伤不了
        if (path != "A1" && path != "A3" && path != "A4") return
        val cfg = config()
        if (cfg.optJSONObject("rules")?.optJSONObject(pkg)?.optBoolean("enabled", false) != true) return
        val now = System.currentTimeMillis()
        val ts = if (kotlin.math.abs(timestamp - now) > 86_400_000L) now else timestamp
        synchronized(writeLock) {
            val prevHash = stats.getString(KEY_PKG_HASH(pkg), null)
            val count = stats.getLong(KEY_PKG_COUNT(pkg), 0L) + 1L
            val e = stats.edit()
            e.putLong(KEY_PKG_COUNT(pkg), count)
            e.putString(KEY_PKG_HASH(pkg), fingerprint)
            e.putLong(KEY_PKG_LAST_TIME(pkg), ts)
            e.putLong(KEY_GLOBAL_COUNT, stats.getLong(KEY_GLOBAL_COUNT, 0L) + 1L)
            val today = todayStr()
            if (stats.getString(KEY_TODAY_DATE, "") != today) {
                e.putString(KEY_TODAY_DATE, today)
                e.putLong(KEY_TODAY_COUNT, 1L)
            } else {
                e.putLong(KEY_TODAY_COUNT, stats.getLong(KEY_TODAY_COUNT, 0L) + 1L)
            }
            if (cfg.optBoolean("enable_logging", true)) {
                val log = JSONObject()
                    .put("ts", ts)
                    .put("level", "I")
                    .put("tag", "Hook")
                    .put("msg", pkg)
                    .put("pkg", pkg)
                    .put("seed", seed)
                    .put("count", count)
                    .put("mode", mode().name)
                    .put("new", fingerprint.take(8))
                    .put("path", path)
                if (prevHash != null) {
                    log.put("moved", prevHash != fingerprint)
                    log.put("old", prevHash.take(8))
                }
                appendLogLocked(log)
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

    /** 该 app 的本地模拟 hook 后指纹（A1 路径：scanner 画布 + seed 扰动）。纯本地，不走跨进程。 */
    fun simulatedFingerprint(pkg: String): String {
        val rule = getRule(pkg)
        if (rule.seed == 0L) return "暂无"
        val pixels = standardPixels()
        FingerprintEngine.applyPixels(
            pixels, StandardCanvas.WIDTH, StandardCanvas.HEIGHT, 0, StandardCanvas.WIDTH, 0, 0, mode(), rule.seed
        )
        return HashUtils.foldHash16(HashUtils.ofIntArray(pixels))
    }

    /**
     * 6 条读取路径的未污染基准指纹，用于与 hook 后指纹对比。
     * 基线是设备常量（无 seed、同画布）：算一次落盘，版本号变或手动清才重算，进页秒开。
     */
    fun standardFingerprints(): List<FingerprintValue> {
        val vc = BuildConfig.VERSION_CODE
        if (stats.getInt(KEY_BASELINE_VC, -1) == vc) {
            decodeBaseline()?.let { return it }
        }
        val items = collectFingerprints()
        saveBaseline(items, vc)
        return items
    }

    /** 清基线缓存（手动刷新入口）；下次进页重算。 */
    fun clearBaselineCache() {
        stats.edit().remove(KEY_BASELINE_JSON).remove(KEY_BASELINE_VC).apply()
    }

    private fun decodeBaseline(): List<FingerprintValue>? = runCatching {
        val arr = JSONArray(stats.getString(KEY_BASELINE_JSON, null) ?: return null)
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            FingerprintValue(o.getString("m"), o.getString("h"))
        }
    }.getOrNull()

    private fun saveBaseline(items: List<FingerprintValue>, vc: Int) {
        val arr = JSONArray()
        for (fp in items) {
            arr.put(JSONObject().put("m", fp.method).put("h", fp.hash))
        }
        stats.edit().putString(KEY_BASELINE_JSON, arr.toString()).putInt(KEY_BASELINE_VC, vc).apply()
    }

    /**
     * A1 单值试算：标准画布经真引擎扰动后哈希，供对照卡展示新旧 seed 效果。
     * 调的是生产代码本尊（非仿写），不存在与 Hook 漂移。
     */
    fun trialA1(seed: Long): String {
        val pixels = standardPixels()
        FingerprintEngine.applyPixels(
            pixels, StandardCanvas.WIDTH, StandardCanvas.HEIGHT, 0, StandardCanvas.WIDTH, 0, 0, mode(), seed
        )
        return foldIfHash(HashUtils.ofIntArray(pixels))
    }

    /**
     * 按固定方法计算 6 种标准化指纹：方法复用 scanner 采集器，
     * 统一 320×160 标准画布，与配套扫描器算出的值可直接对比（A4b 为 A4 同链 JPEG 形态，并入 A4 行）：
     * - A1 getPixels / A3 copyPixelsToBuffer / A4 compress(PNG) / A4b 同链
     * - A2 getPixel 单点（A2）/ E1 Paint 文本度量（E1）/ D1 glReadPixels（D1）
     * 全为本机实测基线（模块自身不可被 Hook），不做任何 seed 扰动。
     */
    private fun collectFingerprints(): List<FingerprintValue> {
        val std = runCatching { StandardCanvas.createBitmap() }.getOrNull() ?: return emptyList()
        try {
            val error = { e: Throwable -> "异常: ${e.javaClass.simpleName}" }
            val a1 = runCatching { PixelReaders.getPixels(std) }.getOrElse(error)
            val a3 = runCatching { PixelReaders.copyPixelsToBuffer(std) }.getOrElse(error)
            val a4 = runCatching { PixelReaders.compressPng(std) }.getOrElse(error)
            val a2 = runCatching { PixelReaders.getPixel(std) }.getOrElse(error)
            val e1 = runCatching { NonPixelSignals.fontMetrics() }.getOrElse(error)
            val d1 = runCatching { HardwareReaders.glReadPixels() }.getOrElse(error)
            return listOf(
                FingerprintValue("A1", foldIfHash(a1)),
                FingerprintValue("A3", foldIfHash(a3)),
                FingerprintValue("A4", foldIfHash(a4)),
                FingerprintValue("A2", foldIfHash(a2)),
                FingerprintValue("E1", foldIfHash(e1)),
                FingerprintValue("D1", foldIfHash(d1)),
            )
        } finally {
            std.recycle()   // finally 内回收：异常路径也不泄漏位图
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

    /** scanner 采集器成功时返回 64 位 SHA-256 hex，折叠为 16 位；失败文本原样透出。判定前统一转小写，避免大小写混写误判。 */
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
                        packageName = o.optString("pkg").ifBlank { null },
                        path = o.optString("path").ifBlank { null },
                        seed = if (o.has("seed")) o.optLong("seed") else null,
                        moved = if (o.has("moved")) o.optBoolean("moved") else null,
                        oldHash = o.optString("old").ifBlank { null },
                        newHash = o.optString("new").ifBlank { null },
                        hitCount = if (o.has("count")) o.optLong("count") else null,
                        mode = o.optString("mode").ifBlank { null },
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

    /**
     * 清空 Hook 统计：各包计数/hash/末次时间 + 全局/今日计数。
     * 规则表与日志不动（日志有自己的清空入口）。
     */
    fun clearStats() {
        synchronized(writeLock) {
            val e = stats.edit()
            val rules = config().optJSONObject("rules")
            if (rules != null) {
                val it = rules.keys()
                while (it.hasNext()) {
                    val pkg = it.next()
                    e.remove(KEY_PKG_COUNT(pkg))
                    e.remove(KEY_PKG_HASH(pkg))
                    e.remove(KEY_PKG_LAST_TIME(pkg))
                }
            }
            e.remove(KEY_GLOBAL_COUNT)
            e.remove(KEY_TODAY_COUNT)
            e.remove(KEY_TODAY_DATE)
            e.apply()
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
                        rule = getRule(pkgInfo.packageName),
                        applicationInfo = appInfo
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
        // 配置双写：本地同步不断档；远端 binder IPC 不许占主线程，丢单线程池异步保序
        val snapshot = c.toString()
        synchronized(writeLock) {
            localPrefs.edit().putString(KEY_CONFIG_JSON, snapshot).apply()
        }
        configIO.execute {
            runCatching {
                RemoteBridge.remote()?.edit()?.putString(KEY_CONFIG_JSON, snapshot)?.apply()
            }
        }
    }

    /**
     * 绑定瞬间把本地配置推远端（本地胜出）：覆盖"未激活时配好规则、
     * 激活后远端还是空"的断档。只推 config_json；统计面本就只在本地。
     * 与 saveConfig 同锁防旧覆盖新；远端瞬时失败补试两次。
     */
    fun pushLocalConfigToRemote() {
        synchronized(writeLock) {
            val remote = RemoteBridge.remote() ?: return
            val local = localPrefs.getString(KEY_CONFIG_JSON, null) ?: return
            repeat(3) {
                val ok = runCatching {
                    remote.edit()?.putString(KEY_CONFIG_JSON, local)?.apply()
                }.isSuccess
                if (ok) return
            }
            android.util.Log.w("ConfigRepository", "pushLocalConfigToRemote failed after retries")
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
        // 统计 key 名与远端同组（以 RemoteConfig 为准），但只存本地：
        // Hook 侧远端只读，统计回写走 StatsReceiver 广播落本地
        private const val KEY_LOGS = RemoteConfig.KEY_LOGS
        private const val KEY_GLOBAL_COUNT = RemoteConfig.KEY_GLOBAL_COUNT
        private const val KEY_TODAY_COUNT = RemoteConfig.KEY_TODAY_COUNT
        private const val KEY_TODAY_DATE = RemoteConfig.KEY_TODAY_DATE
        private const val MAX_LOGS = 1000
        private const val KEY_BASELINE_JSON = "baseline_json"
        private const val KEY_BASELINE_VC = "baseline_vc"

        /** 统计上报包名合法性：与 root shell 白名单同口径，伪造广播先拦格式。 */
        private val PKG_PATTERN = Regex("^[A-Za-z0-9_.]+$")

        /**
         * 写锁：配置 JSON 与日志数组都是"读出→内存修改→整份写回"模式。
         * 本类被多处实例化（各 ViewModel/Provider/AboutScreen），
         * 锁必须是全局单例——实例级锁锁不住跨实例的并发读改写。
         */
        private val writeLock = Any()

        /** 远端写单线程池：binder IPC 不占调用方线程，先后顺序与调用一致。 */
        private val configIO = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "acf-config").apply { isDaemon = true }
        }

        private fun KEY_PKG_COUNT(pkg: String) = RemoteConfig.pkgCount(pkg)
        private fun KEY_PKG_HASH(pkg: String) = RemoteConfig.pkgHash(pkg)
        private fun KEY_PKG_LAST_TIME(pkg: String) = RemoteConfig.pkgLastTime(pkg)
    }
}