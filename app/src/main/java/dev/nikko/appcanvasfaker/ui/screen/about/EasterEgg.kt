package dev.nikko.appcanvasfaker.ui.screen.about

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
import dev.nikko.appcanvasfaker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random


object EasterEggState {

    @Volatile
    var consumed = false


    @Volatile
    var midEggUsed = false
}

private val EGG_IMAGES = listOf(
    R.drawable.neeko_0,
    R.drawable.neeko_1,
    R.drawable.neeko_2,
    R.drawable.neeko_3,
    R.drawable.neeko_4,
    R.drawable.neeko_5,
)
private val MID_EGG_FIRST = R.drawable.leblanc_5
private val MID_EGG_SECOND = R.drawable.leblanc_4

private const val TRIGGER_CLICKS = 5
private const val RESET_AFTER_MS = 9_000L
private const val MID_EGG_PROBABILITY = 0.05f

class EasterEggHolder {

    var active by mutableStateOf(false)
        private set


    var currentRes by mutableStateOf<Int?>(null)
        private set

    private var clickCount = 0
    private var changeCount = 0
    private var showingMidEgg = false
    private var resetJob: Job? = null

    fun onClick(scope: CoroutineScope) {

        if (EasterEggState.consumed && !active) return
        if (showingMidEgg) {

            scheduleReset(scope)
            return
        }
        if (!active) {
            clickCount++
            if (clickCount >= TRIGGER_CLICKS) {
                active = true
                changeCount = 0
                currentRes = EGG_IMAGES[0]


                EasterEggState.consumed = true
            }
            scheduleReset(scope)
            return
        }
        changeCount++


        if (!EasterEggState.midEggUsed && Random.nextFloat() < MID_EGG_PROBABILITY) {
            EasterEggState.midEggUsed = true
            showingMidEgg = true
            scope.launch {
                currentRes = MID_EGG_FIRST
                delay(Random.nextLong(3_000L, 5_001L))
                currentRes = MID_EGG_SECOND
                delay(200L)
                showingMidEgg = false
                advance()
            }
        } else {
            advance()
        }
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

        }
    }
}

@Composable
fun rememberEasterEggHolder(): EasterEggHolder = remember { EasterEggHolder() }


@Composable
fun EasterEggLogoImage(
    holder: EasterEggHolder,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val current = holder.currentRes
    if (current != null) {
        Image(
            painter = painterResource(id = current),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(eggToneMatrix(isDark)),
            modifier = modifier,
        )
    }
}


private fun eggToneMatrix(isDark: Boolean): ColorMatrix {
    val brightness = if (isDark) -6f else 4f
    val scale = 0.96f
    val sat = 0.72f

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
