package dev.neekolor.appcanvasfaker.core

import android.util.Log
import dev.neekolor.appcanvasfaker.util.RootShell
import java.security.SecureRandom
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SSAID（Settings.Secure.ANDROID_ID 的 per-app 值）真实读写，非 Hook。
 * 明文存于 /data/system/users/0/settings_ssaid.xml（system 属主 0600，Android 12+ 为
 * ABX 二进制编码），只经 root shell 读写；SettingsProvider 有内存缓存，写后必须杀掉
 * 该进程才会重载。
 *
 * 安全与可靠性设计（对应审计 N-01/02/03/07/08）：
 * - 所有读改写经 [mutex] 串行化，杜绝并发双写丢失更新（N-07）；
 * - 新条目插入到 `</settings>` 之前并做内存层自查（N-01：AOSP SettingsProvider 解析到
 *   闭合标签即止，根外条目不会被读入、还会在系统下次写盘时静默丢失）；
 * - 写回走"同目录临时文件 → xml2abx 校验 → 属性修正 → sync → mv 原子替换"（N-02），
 *   任一步失败都不触碰原文件；
 * - 临时文件全部位于 /data/system/users/0/（shell 不可达，N-03）且带随机后缀。
 *
 * 仅支持 user 0；工作资料/双开用户为已知限制（后续轮次）。
 * 调用方负责在写入前强制停止目标应用。
 */
object SsaidManager {

    private const val TAG = "SsaidManager"
    private const val SSAID_PATH = "/data/system/users/0/settings_ssaid.xml"
    private const val TMP_DIR = "/data/system/users/0"
    private const val PROVIDER_PKG = "com.android.providers.settings"
    private const val CLOSE_TAG = "</settings>"
    private val SETTING_LINE = Regex("""<setting\s[^>]*name="([^"]+)"[^>]*/>""")
    private val VALUE_LINE = Regex("""value="([^"]*)"""")

    /** 读改写互斥：同一时间只允许一个 SSAID 操作（审计 N-07）。 */
    private val mutex = Mutex()

    data class SsaidEntry(val packageName: String, val value: String)

    /** 写入结果：written=文件已原子替换；reloaded=SettingsProvider 缓存已确认刷新。 */
    data class WriteResult(val written: Boolean, val reloaded: Boolean) {
        val isSuccess: Boolean get() = written && reloaded
    }

    /** 读取全部条目。读取失败（无 root/文件异常）返回 null。写回经 mv 原子替换，读无需加锁。 */
    suspend fun listEntries(): List<SsaidEntry>? = mutex.withLock {
        readFile()?.let { content ->
            SETTING_LINE.findAll(content).map { m ->
                // MIUI 等定制 ROM 的条目 name 可能是数字 userId（双开/用户映射），
                // 真实包名在 package 属性——优先取 package，缺失回退 name
                val name = m.groupValues[1]
                val pkg = Regex("""package="([^"]*)"""").find(m.value)?.groupValues?.get(1).orEmpty()
                val value = VALUE_LINE.find(m.value)?.groupValues?.get(1).orEmpty()
                SsaidEntry(packageName = pkg.ifEmpty { name }, value = value)
            }.toList()
        }
    }

    /** 随机化指定应用的 SSAID（无条目时新建）。 */
    suspend fun randomize(packageName: String): WriteResult = mutate(packageName, isDelete = false)

    /** 删除指定应用的 SSAID 条目。 */
    suspend fun delete(packageName: String): WriteResult = mutate(packageName, isDelete = true)

    /**
     * 统一读改写入口。替换/删除命中现有条目；新建时插入到 `</settings>` 之前。
     * 全部通过内存层自查后才原子写回。
     */
    private suspend fun mutate(packageName: String, isDelete: Boolean): WriteResult =
        mutex.withLock {
            val original = readFile()
                ?: return@withLock WriteResult(written = false, reloaded = false)
            val lines = original.lines().toMutableList()
            val index = lines.indexOfFirst { lineMatches(it, packageName) }
            when {
                // 已有条目：原位替换或整行删除
                index >= 0 && isDelete -> lines.removeAt(index)
                index >= 0 -> lines[index] =
                    lines[index].replaceFirst(VALUE_LINE, """value="${newSsaid()}"""")

                // 无条目：仅允许"随机化"新建，插入点必须在 </settings> 之前（审计 N-01）
                isDelete -> return@withLock WriteResult(written = false, reloaded = false)
                else -> {
                    val closeIdx = lines.indexOfFirst { it.trim() == CLOSE_TAG }
                    if (closeIdx < 0) {
                        Log.w(TAG, "malformed ssaid file: no closing tag")
                        return@withLock WriteResult(false, false)
                    }
                    val maxId = SETTING_LINE.findAll(original)
                        .mapNotNull {
                            Regex("""id="(\d+)"""").find(it.value)?.groupValues?.get(1)?.toLongOrNull()
                        }.maxOrNull() ?: 0L
                    lines.add(closeIdx, buildSettingLine(maxId + 1, packageName))
                }
            }
            // 内存层自查：目标行必须存在（删除时必须不存在）且位于 </settings> 之前
            val closeIdx = lines.indexOfFirst { it.trim() == CLOSE_TAG }
            val targetIdx = lines.indexOfFirst { lineMatches(it, packageName) }
            val targetValid = if (isDelete) targetIdx < 0 else targetIdx in 0..(closeIdx - 1)
            if (!targetValid || closeIdx < 0) {
                Log.w(TAG, "mutate self-check failed for $packageName")
                return@withLock WriteResult(false, false)
            }
            if (!writeBack(lines.joinToString("\n", postfix = "\n"))) {
                return@withLock WriteResult(written = false, reloaded = false)
            }
            WriteResult(written = true, reloaded = reloadProvider())
        }

    /** 条目匹配：name 属性或 package 属性任一命中即可（MIUI 数字 name / 标准 name 两形态）。 */
    private fun lineMatches(line: String, packageName: String): Boolean {
        val name = SETTING_LINE.find(line)?.groupValues?.get(1) ?: return false
        if (name == packageName) return true
        val pkg = Regex("""package="([^"]*)"""").find(line)?.groupValues?.get(1)
        return pkg == packageName && pkg.isNotEmpty()
    }

    private fun newSsaid(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun randomSuffix(): String {
        val bytes = ByteArray(4)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun buildSettingLine(id: Long, packageName: String): String =
        """<setting id="$id" name="$packageName" value="${newSsaid()}" tag="null" package="$packageName" />"""

    /**
     * 读取并解码当前文件为 XML 文本。Android 12+ 为 ABX 二进制，须经系统 abx2xml
     * 解码（临时文件放在系统目录内，shell 不可达）；旧纯文本 ROM 上 abx2xml 失败则直接
     * cat 原文件。失败返回 null。
     */
    private fun readFile(): String? {
        val rand = randomSuffix()
        val tmp = "$TMP_DIR/.acf_ssaid_read_$rand.xml"
        val r = RootShell.exec(
            "rm -f $tmp; " +
                "if abx2xml $SSAID_PATH $tmp 2>/dev/null; then cat $tmp; else cat $SSAID_PATH; fi; " +
                "rm -f $tmp"
        )
        if (!r.isSuccess || !r.stdout.contains("<settings")) {
            Log.w(TAG, "read ssaid failed: code=${r.exitCode} out=${r.stdout.take(80)}")
            return null
        }
        return r.stdout
    }

    /**
     * 原子写回（审计 N-02/N-03）：XML 文本落同目录临时文件 → xml2abx 校验 →
     * 属性修正（mv 前完成，失败即放弃且原文件未动）→ sync → mv 原子替换。
     */
    private fun writeBack(content: String): Boolean {
        val rand = randomSuffix()
        val tmpXml = "$TMP_DIR/.acf_ssaid_$rand.xml"
        val tmpAbx = "$TMP_DIR/.acf_ssaid_$rand.abx"
        val write = RootShell.exec(
            "rm -f $tmpXml $tmpAbx; " +
                "cat > $tmpXml << 'ACF_EOF'\n$content" +
                "ACF_EOF\n" +
                "if ! xml2abx $tmpXml $tmpAbx 2>/dev/null; then echo ACF_VERIFY_FAILED; rm -f $tmpXml $tmpAbx; exit 1; fi; " +
                "chown system:system $tmpAbx && chmod 600 $tmpAbx || { echo ACF_PERM_FAILED; rm -f $tmpXml $tmpAbx; exit 1; }; " +
                "sync; " +
                "mv -f $tmpAbx $SSAID_PATH || { echo ACF_MOVE_FAILED; rm -f $tmpXml $tmpAbx; exit 1; }; " +
                "rm -f $tmpXml"
        )
        if (!write.isSuccess) {
            Log.w(TAG, "write ssaid failed: code=${write.exitCode} ${write.stdout.take(200)}")
            return false
        }
        return true
    }

    /** 让 SettingsProvider 重载文件缓存（审计 N-08：am kill 只杀缓存态进程，失败升级强杀）。 */
    private fun reloadProvider(): Boolean {
        val kill = RootShell.exec("am kill $PROVIDER_PKG")
        if (kill.isSuccess) return true
        Log.w(TAG, "am kill provider failed (code=${kill.exitCode}), escalating to force-stop")
        val force = RootShell.exec("am force-stop $PROVIDER_PKG")
        if (!force.isSuccess) {
            Log.w(TAG, "force-stop provider failed: code=${force.exitCode}")
        }
        return force.isSuccess
    }
}
