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
 * 安全与可靠性设计：
 * - 所有读改写经 [mutex] 串行化，杜绝并发双写丢失更新；
 * - 新条目插入到 `</settings>` 之前并做内存层自查（AOSP SettingsProvider 解析到
 *   闭合标签即止，根外条目不会被读入、还会在系统下次写盘时静默丢失）；
 * - 写回走"同目录临时文件 → xml2abx 校验 → 属性修正 → sync → mv 原子替换"，
 *   任一步失败都不触碰原文件；
 * - 临时文件全部位于 /data/system/users/0/（shell 不可达）且带随机后缀。
 *
 * 仅支持 user 0；工作资料/双开用户暂不支持。
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

    /** 读改写互斥：同一时间只允许一个 SSAID 操作。 */
    private val mutex = Mutex()

    data class SsaidEntry(val packageName: String, val value: String)

    /** 写入结果：written=文件已原子替换；reloaded=重载命令已下发成功（provider 实际重载由系统完成，不在此确认）。 */
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
     * 统一读改写入口。按条目（非按行）替换/删除命中项，同行多条目与重复条目逐个处理；
     * 无条目时仅允许"随机化"新建（插入到 `</settings>` 之前）。
     * 全部通过内存层自查后才原子写回。
     */
    private suspend fun mutate(packageName: String, isDelete: Boolean): WriteResult =
        mutex.withLock {
            val original = readFile()
                ?: return@withLock WriteResult(written = false, reloaded = false)
            // 非自闭合写法（跨行条目）本解析器看不见：拒绝而不是写坏
            val openCount = Regex("""<setting[\s>]""").findAll(original).count()
            if (SETTING_LINE.findAll(original).count() != openCount) {
                Log.w(TAG, "unsupported ssaid entry shape for $packageName")
                return@withLock WriteResult(written = false, reloaded = false)
            }
            val hits = SETTING_LINE.findAll(original).filter { entryMatches(it, packageName) }.toList()
            val updated = when {
                hits.isNotEmpty() && isDelete -> spliceOut(original, hits)
                hits.isNotEmpty() -> {
                    val v = newSsaid()
                    spliceReplace(original, hits, v)
                }
                // 无条目：仅允许"随机化"新建
                isDelete -> return@withLock WriteResult(written = false, reloaded = false)
                else -> insertBeforeClose(original, packageName)
                    ?: return@withLock WriteResult(written = false, reloaded = false)
            }
            // 内存层自查：目标条目必须存在（删除时必须不存在）且位于 </settings> 之前
            val closeAt = updated.indexOf(CLOSE_TAG)
            val remain = SETTING_LINE.findAll(updated).filter { entryMatches(it, packageName) }.toList()
            val targetValid = closeAt > 0 &&
                if (isDelete) remain.isEmpty() && hits.isNotEmpty()
                else remain.any { it.range.first < closeAt }
            if (!targetValid) {
                Log.w(TAG, "mutate self-check failed for $packageName")
                return@withLock WriteResult(false, false)
            }
            if (!writeBack(updated)) {
                return@withLock WriteResult(written = false, reloaded = false)
            }
            WriteResult(written = true, reloaded = reloadProvider())
        }

    private fun spliceOut(content: String, hits: List<MatchResult>): String {
        val sb = StringBuilder()
        var pos = 0
        for (m in hits.sortedBy { it.range.first }) {
            sb.append(content, pos, m.range.first)
            pos = m.range.last + 1
        }
        return sb.append(content, pos, content.length).toString()
    }

    private fun spliceReplace(content: String, hits: List<MatchResult>, value: String): String {
        val sb = StringBuilder()
        var pos = 0
        for (m in hits.sortedBy { it.range.first }) {
            sb.append(content, pos, m.range.first)
            sb.append(m.value.replaceFirst(VALUE_LINE, """value="$value""""))
            pos = m.range.last + 1
        }
        return sb.append(content, pos, content.length).toString()
    }

    private fun insertBeforeClose(content: String, packageName: String): String? {
        val closeAt = content.indexOf(CLOSE_TAG)
        if (closeAt < 0) {
            Log.w(TAG, "malformed ssaid file: no closing tag")
            return null
        }
        val maxId = SETTING_LINE.findAll(content)
            .mapNotNull {
                Regex("""id="(\d+)"""").find(it.value)?.groupValues?.get(1)?.toLongOrNull()
            }.maxOrNull() ?: 0L
        return content.substring(0, closeAt) +
            buildSettingLine(maxId + 1, packageName) + "\n" +
            content.substring(closeAt)
    }

    /** 条目匹配：name 属性或 package 属性任一命中即可（MIUI 数字 name / 标准 name 两形态）。 */
    private fun entryMatches(m: MatchResult, packageName: String): Boolean {
        val name = m.groupValues[1]
        if (name == packageName) return true
        val pkg = Regex("""package="([^"]*)"""").find(m.value)?.groupValues?.get(1)
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
        if (!r.isSuccess || !r.stdout.contains("<settings") || !r.stdout.contains("</settings")) {
            Log.w(TAG, "read ssaid failed: code=${r.exitCode} out=${r.stdout.take(80)}")
            return null
        }
        return r.stdout
    }

    /**
     * 原子写回：XML 文本经 base64 管道落同目录临时文件 → xml2abx 校验 →
     * 属性修正（mv 前完成，失败即放弃且原文件未动）→ sync → mv 原子替换。
     * base64 单行只含 [A-Za-z0-9+/=]，heredoc 定界符碰撞不复存在。
     */
    private fun writeBack(content: String): Boolean {
        val rand = randomSuffix()
        val tmpXml = "$TMP_DIR/.acf_ssaid_$rand.xml"
        val tmpAbx = "$TMP_DIR/.acf_ssaid_$rand.abx"
        val b64 = android.util.Base64.encodeToString(
            content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
        )
        val write = RootShell.exec(
            "rm -f $TMP_DIR/.acf_ssaid_*.xml $TMP_DIR/.acf_ssaid_*.abx; " +
                "echo '$b64' | base64 -d > $tmpXml || { echo ACF_DECODE_FAILED; rm -f $tmpXml $tmpAbx; exit 1; }; " +
                "if ! xml2abx $tmpXml $tmpAbx 2>/dev/null; then echo ACF_VERIFY_FAILED; rm -f $tmpXml $tmpAbx; exit 1; fi; " +
                "chown system:system $tmpAbx && chmod 600 $tmpAbx || { echo ACF_PERM_FAILED; rm -f $tmpXml $tmpAbx; exit 1; }; " +
                "sync; " +
                "mv -f $tmpAbx $SSAID_PATH || { echo ACF_MOVE_FAILED; rm -f $tmpXml $tmpAbx; exit 1; }; " +
                "restorecon $SSAID_PATH 2>/dev/null || echo ACF_RESTORECON_FAILED; " +
                "rm -f $tmpXml"
        )
        if (!write.isSuccess) {
            Log.w(TAG, "write ssaid failed: code=${write.exitCode} ${write.stdout.take(200)}")
            return false
        }
        if ("ACF_RESTORECON_FAILED" in write.stdout) {
            Log.w(TAG, "write ssaid ok but restorecon failed, provider may deny the file")
        }
        return true
    }

    /** 让 SettingsProvider 重载文件缓存：先 am kill（只杀缓存态进程），失败再升级强杀。 */
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
