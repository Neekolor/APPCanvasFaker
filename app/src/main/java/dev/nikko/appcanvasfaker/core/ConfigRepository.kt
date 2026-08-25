package dev.nikko.appcanvasfaker.core

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import dev.nikko.appcanvasfaker.AppCanvasFakerApplication
import dev.nikko.appcanvasfaker.BuildConfig
import dev.nikko.appcanvasfaker.util.HashUtils
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI 进程数据源：模块 SharedPreferences（app_canvas_faker）。
 * 配置 JSON 结构：{ mode, enable_logging, rules: { pkg: {enabled, seed} } }
 * 统计（hook 计数/日志/哈希）存同文件独立 key，由 StatsProvider 经 ContentProvider 回写。
 */
class ConfigRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val pm: PackageManager get() = context.packageManager

    /**
     * 写锁：配置 JSON 与日志数组都是"读出→内存修改→整份写回"模式，
     * UI 线程与 Provider binder 线程并发执行时后写者会覆盖先写者的修改
     * （丢失更新）。所有读改写序列必须持有同一把锁。
     */
    private val writeLock = Any()

    // ---------- 公开接口（UI 契约） ----------

    fun getRule(pkg: String): AppRule {
        val rule = config().optJSONObject("rules")?.optJSONObject(pkg)
        return AppRule(
            packageName = pkg,
            enabled = rule?.optBoolean("enabled", false) ?: false,
            seed = rule?.optLong("seed", 0L) ?: 0L,
            lastCanvasHash = prefs.getString(KEY_PKG_HASH(pkg), null)
                ?.let { HashUtils.foldHash16(it) } ?: "暂无",
            hookCount = prefs.getLong(KEY_PKG_COUNT(pkg), 0L),
            lastHookTime = prefs.getLong(KEY_PKG_LAST_TIME(pkg), 0L)
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

    /** 该 app 的本地模拟 hook 后指纹：标准画布 + 该 app 的 seed 按当前模式套扰动 → 16 位折叠短哈希。纯本地，不走跨进程。 */
    fun simulatedFingerprint(pkg: String): String {
        val rule = getRule(pkg)
        if (rule.seed == 0L) return "暂无"
        val pixels = renderStandardCanvas()
        FingerprintEngine.applyPixels(pixels, STD_W, STD_H, 0, STD_W, 0, 0, mode(), rule.seed)
        return HashUtils.foldHash16(HashUtils.ofIntArray(pixels))
    }

    /** 4 种读取路径的标准画布指纹（未污染基准），用于与 hook 后指纹对比。 */
    fun standardFingerprints(): List<FingerprintValue> {
        val pixels = renderStandardCanvas()
        return fingerprintValues(pixels)
    }

    /** 该 app 套当前模式扰动后的 4 种读取路径指纹。 */
    fun simulatedFingerprints(pkg: String): List<FingerprintValue> {
        val rule = getRule(pkg)
        if (rule.seed == 0L) return emptyList()
        val pixels = renderStandardCanvas()
        FingerprintEngine.applyPixels(pixels, STD_W, STD_H, 0, STD_W, 0, 0, mode(), rule.seed)
        return fingerprintValues(pixels)
    }

    /**
     * 按固定方法计算 4 种标准化指纹，覆盖常见的 Canvas 指纹读取路径：
     * - A1：getPixels 像素数组直读
     * - A3：copyPixelsToBuffer 按字节缓冲拷贝
     * - A4：compress 压缩后字节（PNG 编码字节）
     * - A4b：getImageSizes 尺寸 + 抽样像素
     */
    private fun fingerprintValues(pixels: IntArray): List<FingerprintValue> {
        val a1 = HashUtils.ofIntArray(pixels)
        val a3 = HashUtils.ofBytes(IntArrayToRgba(pixels))
        val a4 = HashUtils.ofBytes(compressPng(pixels))
        val a4b = HashUtils.ofString("$STD_W:$STD_H:" + HashUtils.foldHash16(a1))
        return listOf(
            FingerprintValue("A1", "像素直读（getPixels）", HashUtils.foldHash16(a1)),
            FingerprintValue("A3", "缓冲拷贝（copyPixelsToBuffer）", HashUtils.foldHash16(a3)),
            FingerprintValue("A4", "压缩读取（compress）", HashUtils.foldHash16(a4)),
            FingerprintValue("A4b", "尺寸采样（getImageSizes）", HashUtils.foldHash16(a4b)),
        )
    }

    /** ARGB8888 像素 → RGBA 字节序（模拟 copyPixelsToBuffer 的落盘格式）。 */
    private fun IntArrayToRgba(pixels: IntArray): ByteArray {
        val out = ByteArray(pixels.size * 4)
        for (i in pixels.indices) {
            val p = pixels[i]
            val o = i * 4
            out[o] = ((p ushr 16) and 0xFF).toByte()
            out[o + 1] = ((p ushr 8) and 0xFF).toByte()
            out[o + 2] = (p and 0xFF).toByte()
            out[o + 3] = ((p ushr 24) and 0xFF).toByte()
        }
        return out
    }

    /** 像素 → PNG 编码字节（模拟 compress 读取路径）。 */
    private fun compressPng(pixels: IntArray): ByteArray {
        return runCatching {
            val bmp = Bitmap.createBitmap(STD_W, STD_H, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, STD_W, 0, 0, STD_W, STD_H)
            val out = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
            out.toByteArray()
        }.getOrDefault(ByteArray(0))
    }

    fun enableLogging(): Boolean = config().optBoolean("enable_logging", true)

    fun setEnableLogging(enabled: Boolean) {
        synchronized(writeLock) {
            val c = config()
            c.put("enable_logging", enabled)
            saveConfig(c)
        }
    }

    /** 追加一条日志并裁剪到上限。调用方必须已持有 [writeLock]。 */
    private fun appendLogLocked(entry: JSONObject) {
        val arr = logsArray()
        arr.put(entry)
        while (arr.length() > MAX_LOGS) arr.remove(0)
        prefs.edit().putString(KEY_LOGS, arr.toString()).apply()
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
        prefs.edit().putString(KEY_LOGS, "[]").apply()
    }

    fun snapshot(): ModuleSnapshot {
        val standard = renderStandardCanvas()
        // 展示层统一折叠为 16 位短哈希（与扫描器 foldHash16 同算法）
        val canvasHash = HashUtils.foldHash16(HashUtils.ofIntArray(standard))
        // 本地标准画布套用噪声后的哈希（预览用独立随机 seed，避免与任何目标 app 关联）
        val previewSeed = prefs.getLong(KEY_PREVIEW_SEED, 0L).takeIf { it != 0L }
            ?: newSeed().also { prefs.edit().putLong(KEY_PREVIEW_SEED, it).apply() }
        val randomized = standard.copyOf()
        FingerprintEngine.applyPixels(randomized, STD_W, STD_H, 0, STD_W, 0, 0, ProtectionMode.NOISE, previewSeed)
        val randomizedHash = HashUtils.foldHash16(HashUtils.ofIntArray(randomized))
        return ModuleSnapshot(
            moduleActive = isFrameworkActive(),
            frameworkName = "libxposed",
            frameworkApi = "102",
            totalHookCount = prefs.getLong(KEY_GLOBAL_COUNT, 0L),
            todayHookCount = todayCount(),
            canvasHash = canvasHash,
            randomizedHash = randomizedHash,
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

    // ---------- 供 StatsProvider（ContentProvider 桥）使用 ----------

    fun configJson(): String =
        prefs.getString(KEY_CONFIG_JSON, null)?.takeIf { it.isNotBlank() }
            ?: defaultConfig().toString()

    fun writeConfigRaw(json: String) {
        synchronized(writeLock) {
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
            if (!isValidConfig(obj)) return
            prefs.edit().putString(KEY_CONFIG_JSON, obj.toString()).apply()
        }
    }

    /** schema 校验：不合法拒绝并保留旧配置。 */
    private fun isValidConfig(c: JSONObject): Boolean {
        // mode 必须能 valueOf
        val modeOk = runCatching { ProtectionMode.valueOf(c.optString("mode", "")) }.isSuccess
        if (!modeOk) return false
        // rules 必须是 JSONObject
        val rules = c.opt("rules") as? JSONObject ?: return false
        val it = rules.keys()
        while (it.hasNext()) {
            // 每个规则值必须是 JSONObject
            val rule = rules.opt(it.next()) as? JSONObject ?: return false
            // enabled 必须是 boolean，seed 必须是 number
            if (!rule.has("enabled") || rule.get("enabled") !is Boolean) return false
            if (!rule.has("seed") || rule.get("seed") !is Number) return false
        }
        return true
    }

    fun recordHook(
        pkg: String,
        modeName: String,
        seed: Long,
        fingerprint: String,
        timestamp: Long,
        enableLogging: Boolean
    ) {
        synchronized(writeLock) {
            val editor = prefs.edit()
                .putLong(KEY_PKG_COUNT(pkg), prefs.getLong(KEY_PKG_COUNT(pkg), 0L) + 1L)
                .putString(KEY_PKG_HASH(pkg), fingerprint)
                .putLong(KEY_PKG_LAST_TIME(pkg), timestamp)
                .putLong(KEY_GLOBAL_COUNT, prefs.getLong(KEY_GLOBAL_COUNT, 0L) + 1L)
            rollToday(editor)
            if (enableLogging) {
                val arr = logsArray()
                arr.put(
                    JSONObject()
                        .put("ts", timestamp)
                        .put("level", "I")
                        .put("tag", "Hook")
                        .put("msg", pkg)
                        .put("pkg", pkg)
                )
                while (arr.length() > MAX_LOGS) arr.remove(0)
                editor.putString(KEY_LOGS, arr.toString())
            }
            // recordHook 来自跨进程 call（hook 进程 → 模块进程），进程可能在写后立刻退出，
            // 用 commit() 同步落盘，避免 apply() 异步丢失
            editor.commit()
        }
    }

    // ---------- 内部 ----------

    private fun config(): JSONObject {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return defaultConfig()
        return runCatching { JSONObject(raw) }.getOrElse { defaultConfig() }
    }

    private fun saveConfig(c: JSONObject) {
        prefs.edit().putString(KEY_CONFIG_JSON, c.toString()).apply()
    }

    private fun defaultConfig(): JSONObject = JSONObject().apply {
        put("mode", ProtectionMode.NOISE.name)
        put("enable_logging", true)
        put("rules", JSONObject())
    }

    private fun logsArray(): JSONArray = runCatching {
        JSONArray(prefs.getString(KEY_LOGS, "[]"))
    }.getOrElse { JSONArray() }

    private fun todayCount(): Long {
        val today = todayStr()
        if (prefs.getString(KEY_TODAY_DATE, "") != today) {
            prefs.edit().putString(KEY_TODAY_DATE, today).putLong(KEY_TODAY_COUNT, 0L).apply()
            return 0L
        }
        return prefs.getLong(KEY_TODAY_COUNT, 0L)
    }

    private fun rollToday(editor: SharedPreferences.Editor) {
        val today = todayStr()
        // 跨日：count=1（旧计数作废）；同日：count=旧值+1；只用局部变量保证只 put 一次
        val count = if (prefs.getString(KEY_TODAY_DATE, "") != today) {
            editor.putString(KEY_TODAY_DATE, today)
            1L
        } else {
            prefs.getLong(KEY_TODAY_COUNT, 0L) + 1L
        }
        editor.putLong(KEY_TODAY_COUNT, count)
    }

    private fun todayStr(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())

    private fun newSeed(): Long {
        var s = SecureRandom().nextLong()
        if (s == 0L) s = 0x9E3779B97F4A7C15uL.toLong()
        return s
    }

    /** 本地标准画布：固定图案的 64x64 ARGB_8888 位图，用于复算本地指纹。 */
    private fun renderStandardCanvas(): IntArray {
        return runCatching {
            val bmp = Bitmap.createBitmap(STD_W, STD_H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.BLACK
            canvas.drawRect(4f, 4f, 20f, 20f, paint)
            canvas.drawCircle(STD_W / 2f, STD_H / 2f, 12f, paint)
            canvas.drawLine(0f, 0f, STD_W.toFloat(), STD_H.toFloat(), paint)
            val pixels = IntArray(STD_W * STD_H)
            bmp.getPixels(pixels, 0, STD_W, 0, 0, STD_W, STD_H)
            bmp.recycle()
            pixels
        }.getOrDefault(IntArray(STD_W * STD_H))
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
        const val KEY_CONFIG_JSON = "config_json"
        private const val KEY_LOGS = "logs"
        private const val KEY_GLOBAL_COUNT = "global_hook_count"
        private const val KEY_TODAY_COUNT = "today_hook_count"
        private const val KEY_TODAY_DATE = "today_date"
        private const val KEY_PREVIEW_SEED = "preview_seed"
        private const val MAX_LOGS = 1000
        private const val STD_W = 64
        private const val STD_H = 64

        private fun KEY_PKG_COUNT(pkg: String) = "pkg_${pkg}_count"
        private fun KEY_PKG_HASH(pkg: String) = "pkg_${pkg}_last_hash"
        private fun KEY_PKG_LAST_TIME(pkg: String) = "pkg_${pkg}_last_time"
    }
}