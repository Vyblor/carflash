package app.carflash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carflash.R

/**
 * 카플래시 디자인 토큰 v0.2 (ideas/carflash-design/tokens.css)를 Compose로 옮긴 것.
 * Pleos Connect는 160dpi라 1dp = 1px. 와이어프레임 숫자를 그대로 dp로 쓴다.
 */
object Cf {
    val bg = Color(0xFF131417)
    val surface = Color(0xFF1D1E21)
    val surface2 = Color(0xFF313236)
    val surface3 = Color(0xFF44464E)
    val line = Color(0x1AFFFFFF)
    val dim = Color(0xB8131417)
    val text = Color.White
    val text2 = Color(0xD6FFFFFF)
    val text3 = Color(0xA3FFFFFF)
    val textOff = Color(0x52FFFFFF)
    val onAccent = Color(0xFF131417)
    val accent = Color(0xFFFF4D8D)
    val accentPress = Color(0xFFE63D7A)
    val accentSoft = Color(0x29FF4D8D)
    val accent2 = Color(0xFF3FD5FF)
    val accent2Soft = Color(0x243FD5FF)
    val stop = Color(0xFFFF4C28)
    val stopFill = Color(0xFFC62828)
    val warn = Color(0xFFFFB547)
    val ok = Color(0xFF00E074)
    val info = Color(0xFF0082FF)
    val warnSoft = Color(0x24FFB547)
    val stopSoft = Color(0x24FF4C28)
    val okSoft = Color(0x2400E074)

    // 플래시몹 색(앰비언트로 나가는 콘텐츠 색)
    val showRed = Color(0xFFFF2D55)
    val showOrange = Color(0xFFFF8A00)
    val showYellow = Color(0xFFFFD60A)
    val showGreen = Color(0xFF30D158)
    val showCyan = Color(0xFF00E5FF)
    val showBlue = Color(0xFF2F6BFF)
    val showViolet = Color(0xFFA05CFF)
    val showPink = Color(0xFFFF4FD8)
    val showOff = Color(0xFF313236)

    // 크기 (Pleos 버튼 Small 64 · Medium 80 · Large 100, 카플래시 140)
    val touchMin = 64.dp
    val touchMd = 80.dp
    val touchLg = 100.dp
    val touchXl = 140.dp
    val rMd = 24.dp
    val rLg = 32.dp
    val headH = 112.dp
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun asta(weight: Int) = Font(
    R.font.asta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val AstaSans = FontFamily(asta(400), asta(500), asta(700), asta(800))

object CfType {
    val display = TextStyle(fontFamily = AstaSans, fontWeight = FontWeight.ExtraBold, fontSize = 80.sp, lineHeight = 96.sp)
    val headline = TextStyle(fontFamily = AstaSans, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 64.sp)
    val title = TextStyle(fontFamily = AstaSans, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 52.sp)
    val titleSm = TextStyle(fontFamily = AstaSans, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp)
    val body = TextStyle(fontFamily = AstaSans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp)
    val bodySm = TextStyle(fontFamily = AstaSans, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp)
    val label = TextStyle(fontFamily = AstaSans, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 28.sp)
    val caption = TextStyle(fontFamily = AstaSans, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 24.sp)
}

/** 창 폭으로 정한 배치: 넓으면 landscape(앱 영역 전체), 좁으면 portrait(분할 840). */
enum class Layout { Landscape, Portrait }

val LocalLayout = staticCompositionLocalOf { Layout.Landscape }

@Composable
fun CarflashTheme(layout: Layout, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cf.accent, onPrimary = Cf.onAccent, background = Cf.bg, surface = Cf.surface,
            onBackground = Cf.text, onSurface = Cf.text,
        ),
    ) {
        CompositionLocalProvider(LocalLayout provides layout) { content() }
    }
}
