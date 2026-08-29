package dev.nikko.appcanvasfaker.core

import android.util.Log
import dev.nikko.appcanvasfaker.util.RootShell
import java.security.SecureRandom

/**
 * SSAID（Settings.Secure.ANDROID_ID 的 per-app 值）真实读写，非 Hook。
 * 明文存于 /data/system/users/0/settings_ssaid.xml（system 属主 0600），
 * 只能经 root shell 修改；SettingsProvider 有内存缓存，改完必须 kill 该进程才会重载。
 *
 * 返回值约定：readSsaid null=读取失败（无 root/文件不存在）；""=该应用无 SSAID 条目。
 * 调用方负责在写入前强制停止目标应用，避免其进程内缓存旧值。
 */
object SsaidManager {

    private const val TAG = "SsaidManager"
    private const val SSAID_PATH = "/data/system/users/0/settings_ssaid.xml"
    private const val TMP_XML = "/data/local/tmp/acf_ssaid.xml"
    private const val TMP_ABX = "/data/local/tmp/acf_ssaid.abx"
    private const val PROVIDER_PKG = "com.android.providers.settings"
    private val SETTING_LINE = Regex("""<setting\s[^>]*name="([^"]+)"[^>]*/>""")

    /**
     * Android 12+ 的 settings_ssaid.xml 为 ABX 二进制编码（MIUI Android 14 实测），
     * 读须经系统自带 abx2xml 转文本、写经 xml2abx 转回；旧纯文本 ROM 上
     * abx2xml 会失败，此时回退直接按文本读（写仍尝试原样写回）。
     */

    /** 读取目标应用的 SSAID；不存在返回空串，读取失败返回 null。 */
    fun readSsaid(packageName: String): String? {
        val content = readFile() ?: return null
        val line = SETTING_LINE.findAll(content)
            .firstOrNull { it.groupValues[1] == packageName }?.value ?: return ""
        return Regex("""value="([^"]*)"""").find(line)?.groupValues?.get(1) ?: ""
    }

    /** 随机化 SSAID：生成新 16 位 hex 并写回。成功返回 true。 */
    fun randomize(packageName: String): Boolean {
        val newValue = newSsaid()
        return mutate(packageName) { line ->
            line.replaceFirst(Regex("""value="[^"]*""""), """value="$newValue"""")
        }
    }

    /** 删除该应用的 SSAID 条目。成功返回 true。 */
    fun delete(packageName: String): Boolean = mutate(packageName) { null }

    private fun newSsaid(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 修改/删除/追加 [packageName] 的 setting 行并写回。
     * [transform] 收到原行内容，返回替换后的行；返回 null 表示删除该行。
     * 原条目不存在且 transform 返回替换行时按追加处理（随机化路径）。
     * 返回 writeBack 是否成功。
     */
    private fun mutate(packageName: String, transform: (String) -> String?): Boolean {
        val original = readFile() ?: return false
        val lines = original.lines().toMutableList()
        val index = lines.indexOfFirst { line ->
            SETTING_LINE.find(line)?.groupValues?.get(1) == packageName
        }
        val newLines: List<String> = if (index >= 0) {
            val replaced = transform(lines[index])
            if (replaced == null) {
                lines.toMutableList().apply { removeAt(index) }
            } else {
                lines.toMutableList().apply { set(index, replaced) }
            }
        } else {
            // 新条目：id 取现有最大值 +1，保证属性顺序与 AOSP 写入格式一致
            val maxId = SETTING_LINE.findAll(original)
                .mapNotNull { Regex("""id="(\d+)"""").find(it.value)?.groupValues?.get(1)?.toLongOrNull() }
                .maxOrNull() ?: 0L
            lines.filter { it.isNotBlank() } + buildSettingLine(maxId + 1, packageName)
        }
        return writeBack(newLines.joinToString("\n", postfix = "\n"))
    }

    private fun buildSettingLine(id: Long, packageName: String): String =
        """<setting id="$id" name="$packageName" value="${newSsaid()}" tag="null" package="$packageName" />"""

    private fun readFile(): String? {
        // 先尝试 ABX 解码（Android 12+）；失败则按纯文本直接读（旧 ROM）
        val decode = RootShell.exec(
            "abx2xml $SSAID_PATH $TMP_XML 2>/dev/null && cat $TMP_XML && rm -f $TMP_XML" +
                " || cat $SSAID_PATH"
        )
        if (!decode.isSuccess || !decode.stdout.contains("<settings")) {
            Log.w(TAG, "read ssaid failed: code=${decode.exitCode} out=${decode.stdout.take(80)}")
            return null
        }
        return decode.stdout
    }

    private fun writeBack(content: String): Boolean {
        // XML 文本落 /data/local/tmp → xml2abx 转回二进制（旧 ROM 上 xml2abx 失败时
        // 直接 cp 文本，保持与读取端同一套编解码假设）→ 覆盖原文件并恢复属主/权限
        // → kill SettingsProvider 迫使其重载缓存
        val write = RootShell.exec(
            "rm -f $TMP_XML $TMP_ABX; " +
                "cat > $TMP_XML << 'ACF_EOF'\n$content" +
                "ACF_EOF\n" +
                "if xml2abx $TMP_XML $TMP_ABX 2>/dev/null; then" +
                " cp $TMP_ABX $SSAID_PATH;" +
                " else cp $TMP_XML $SSAID_PATH; fi && " +
                "chown system:system $SSAID_PATH && chmod 600 $SSAID_PATH && rm -f $TMP_XML $TMP_ABX"
        )
        if (!write.isSuccess) {
            Log.w(TAG, "write ssaid failed: code=${write.exitCode} ${write.stdout.take(200)}")
            return false
        }
        val reload = RootShell.exec("am kill $PROVIDER_PKG")
        if (!reload.isSuccess) {
            // am kill 失败不致命：SettingsProvider 重启后也会重读文件
            Log.w(TAG, "reload provider failed: code=${reload.exitCode}")
        }
        return true
    }
}
