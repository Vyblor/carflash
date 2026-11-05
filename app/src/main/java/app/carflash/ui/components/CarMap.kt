package app.carflash.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.carflash.ui.theme.AstaSans
import app.carflash.ui.theme.Cf

/**
 * 위에서 본 차. 앰비언트 5구역(대시보드, 1열 좌·우, 2열 좌·우), 창문 4개(열린 만큼 청록), 송풍 단.
 * zones 값 null = 꺼짐. windows = 0~100 %.
 */
@Composable
fun CarMap(zones: List<Int?>, windows: List<Int>, fan: Int?, width: Dp, modifier: Modifier = Modifier, describe: Boolean = true) {
    val tm = rememberTextMeasurer()
    val desc = "car:zones=" + zones.joinToString(",") { it?.let { c -> "%06X".format(c and 0xFFFFFF) } ?: "off" } +
        ";win=" + windows.joinToString(",") + ";fan=" + (fan ?: 0)
    // 재생 중에는 박자마다 그림이 바뀐다. 설명까지 바뀌면 접근성 이벤트가 쏟아지므로 고정 문구를 쓴다.
    val label = if (describe) desc else "car:live"
    Canvas(modifier.size(width, width * 520f / 300f).testTag("car_map").semantics { contentDescription = label }) {
        val w = size.width; val h = size.height
        fun col(i: Int): Color = zones.getOrNull(i)?.let { Color(0xFF000000.toInt() or it) } ?: Cf.showOff
        // 차체
        val bodyL = w * 0.14f; val bodyR = w * 0.86f; val bodyT = h * 0.03f; val bodyB = h * 0.97f
        drawRoundRect(Cf.surface2, Offset(bodyL, bodyT), Size(bodyR - bodyL, bodyB - bodyT), CornerRadius(w * 0.2f))
        drawRoundRect(Cf.line, Offset(bodyL, bodyT), Size(bodyR - bodyL, bodyB - bodyT), CornerRadius(w * 0.2f), style = Stroke(3f))
        // 앞유리·뒷유리
        drawRoundRect(Cf.surface3, Offset(w * 0.24f, h * 0.17f), Size(w * 0.52f, h * 0.07f), CornerRadius(12f))
        drawRoundRect(Cf.surface3, Offset(w * 0.26f, h * 0.86f), Size(w * 0.48f, h * 0.05f), CornerRadius(12f))
        // 대시보드 조명(0)
        drawRoundRect(col(0), Offset(w * 0.24f, h * 0.27f), Size(w * 0.52f, h * 0.025f), CornerRadius(8f))
        // 좌석 + 조명 띠 (1열 좌·우 = 1,2 / 2열 좌·우 = 3,4)
        val seatW = w * 0.2f; val seatH = h * 0.14f
        val rows = listOf(h * 0.36f, h * 0.6f)
        rows.forEachIndexed { r, y ->
            listOf(w * 0.27f, w * 0.53f).forEachIndexed { s, x ->
                drawRoundRect(Cf.surface3, Offset(x, y), Size(seatW, seatH), CornerRadius(16f))
                val z = 1 + r * 2 + s
                val stripX = if (s == 0) x - w * 0.05f else x + seatW + w * 0.025f
                drawRoundRect(col(z), Offset(stripX, y), Size(w * 0.025f, seatH), CornerRadius(6f))
            }
        }
        // 창문(좌앞, 우앞, 좌뒤, 우뒤)
        val winH = h * 0.16f
        listOf(Triple(0, bodyL - w * 0.05f, h * 0.34f), Triple(1, bodyR + w * 0.01f, h * 0.34f),
            Triple(2, bodyL - w * 0.05f, h * 0.58f), Triple(3, bodyR + w * 0.01f, h * 0.58f)).forEach { (i, x, y) ->
            val open = (windows.getOrNull(i) ?: 0).coerceIn(0, 100) / 100f
            drawRoundRect(Cf.surface3, Offset(x, y), Size(w * 0.04f, winH), CornerRadius(6f))
            if (open > 0) drawRoundRect(Cf.accent2, Offset(x, y + winH * (1 - open)), Size(w * 0.04f, winH * open), CornerRadius(6f))
        }
        // 송풍
        if (fan != null && fan > 0) {
            val r = tm.measure("$fan", TextStyle(fontFamily = AstaSans, fontSize = (w * 0.09f / density).sp, color = Cf.text))
            drawCircle(Cf.accent2Soft, w * 0.08f, Offset(w * 0.5f, h * 0.79f))
            drawText(r, topLeft = Offset(w * 0.5f - r.size.width / 2f, h * 0.79f - r.size.height / 2f))
        }
    }
}
