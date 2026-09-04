package dev.neekolor.appcanvasfaker.ui.screen.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.neekolor.appcanvasfaker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "设置 → 关于"页的 "Copyright © 2026 Neekolor" 彩蛋：
 * - 连续点击该卡片 7 次进入彩蛋模式，关于页 logo 依次循环六张 Neeko 图；
 * - 5s 无点击自动还原原 logo 并清零连点计数（需重新连点 7 次）；
 * - 彩蛋每进程只触发一次，重启 App 后重置。
 * 逻辑刻意保持单文件、无持久化，不与业务状态耦合。
 * （历史：v0.7.0~v0.8.0 曾有"彩蛋中蛋"Leblanc 随机机制与 5 次触发/9s 还原参数，
 *   v0.8.1-dev 按用户要求完全移除，演进脉络见 docs/DEVELOPMENT.md。）
 */
object EasterEggState {
    /** 本进程内彩蛋（点击换图）是否已消耗。 */
    @Volatile
    var consumed = false
}

private val EGG_IMAGES = listOf(
    R.drawable.neeko_0,
    R.drawable.neeko_1,
    R.drawable.neeko_2,
    R.drawable.neeko_3,
    R.drawable.neeko_4,
    R.drawable.neeko_5,
)

private const val TRIGGER_CLICKS = 7
private const val RESET_AFTER_MS = 5_000L

class EasterEggHolder {
    /** 彩蛋模式中（logo 被替换）。 */
    var active by mutableStateOf(false)
        private set

    /** 当前展示的彩蛋图；null = 原logo。 */
    var currentRes by mutableStateOf<Int?>(null)
        private set

    private var clickCount = 0
    private var changeCount = 0
    private var resetJob: Job? = null

    fun onClick(scope: CoroutineScope) {
        // 彩蛋每进程只进一轮：还原后点击卡片不再有反应，重启 App 才重置
        if (EasterEggState.consumed && !active) return
        if (!active) {
            clickCount++
            if (clickCount >= TRIGGER_CLICKS) {
                active = true
                changeCount = 0
                currentRes = EGG_IMAGES[0]
                // 审计 N-21：进入彩蛋瞬间即消耗本轮（进程级一次）。
                // 旧实现放在还原时置位——用户只点一两下就走也会在超时后锁死彩蛋，
                // 且触发期间离开页面则永不置位，语义都不对。
                EasterEggState.consumed = true
            }
            scheduleReset(scope)
            return
        }
        changeCount++
        advance()
        scheduleReset(scope)
    }

    private fun advance() {
        currentRes = EGG_IMAGES[changeCount % EGG_IMAGES.size]
    }

    private fun scheduleReset(scope: CoroutineScope) {
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(RESET_AFTER_MS)
            active = false
            currentRes = null
            // 连点计数一并清零：保证"连续 7 次"的触发语义（中断即重新计数）
            clickCount = 0
            // consumed 已在进入彩蛋时置位（审计 N-21）
        }
    }
}

@Composable
fun rememberEasterEggHolder(): EasterEggHolder = remember { EasterEggHolder() }

/**
 * 版权卡文案（彩蛋入口卡标题）："Copyright © 2026 Neekolor."，其中年份比主体
 * 字号小 2sp（用户指定的混排字号）。品牌文案不翻译，双皮肤共用。
 */
fun buildCopyrightText(baseFontSize: TextUnit): AnnotatedString = buildAnnotatedString {
    append("Copyright © ")
    withStyle(SpanStyle(fontSize = (baseFontSize.value - 2f).sp)) { append("2026") }
    append(" Neekolor.")
}

/** 彩蛋激活时的 logo 渲染：尺寸完全由调用方容器决定，默认 ContentScale.Fit
 *  在容器内完整显示（不裁切、不放大）。调用方负责给容器固定尺寸以保证与原 logo 视觉等高。 */
@Composable
fun EasterEggLogoImage(
    holder: EasterEggHolder,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val isDark = isSystemInDarkTheme()
    val current = holder.currentRes
    if (current != null) {
        Image(
            painter = painterResource(id = current),
            contentDescription = null,
            contentScale = contentScale,
            colorFilter = ColorFilter.colorMatrix(eggToneMatrix(isDark)),
            modifier = modifier,
        )
    }
}

/**
 * 运行时轻度调色：降饱和 + 按深浅主题微调亮度，让外部 JPG 与界面色调融合。
 * 饱和度与亮度两步直接合并为单个矩阵（Compose 的 ColorMatrix 无 postConcat）。
 */
private fun eggToneMatrix(isDark: Boolean): ColorMatrix {
    val brightness = if (isDark) -6f else 4f
    val scale = 0.96f
    val sat = 0.72f
    // 标准亮度权重（Rec.601），sat=1 无损、sat=0 全灰
    val sr = 0.213f; val sg = 0.715f; val sb = 0.072f
    fun mix(base: Float, weight: Float) = (base + (1f - base) * weight) * scale
    fun dim(base: Float, weight: Float) = (base - base * weight) * scale
    return ColorMatrix(
        floatArrayOf(
            mix(sr, sat), dim(sg, sat), dim(sb, sat), 0f, brightness,
            dim(sr, sat), mix(sg, sat), dim(sb, sat), 0f, brightness,
            dim(sr, sat), dim(sg, sat), mix(sb, sat), 0f, brightness,
            0f, 0f, 0f, 1f, 0f,
        )
    )
}
