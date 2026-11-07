package app.carflash.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carflash.data.Effect
import app.carflash.ui.theme.AstaSans
import app.carflash.ui.theme.Cf
import app.carflash.vehicle.LampMode

private val LampOn = Color(0xFFFFF4C2)
private val LampTail = Color(0xFFFFB547)
private val LampRear = Color(0xFFFF3B30)

/**
 * 효과 아이콘 배지. 재생 화면 왼쪽 목록과 차 그림 위에 같은 모양으로 놓여, 어느 줄이 차의 어느 부분인지 이어 준다.
 * active = 지금 움직이는 중(청록으로 채움), 아니면 테두리만.
 */
@Composable
fun EffectBadge(e: Effect, active: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (active) Cf.accent2 else Cf.surface)
            .border(2.dp, if (active) Cf.accent2 else Cf.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(e.icon(), null, tint = if (active) Cf.bg else Cf.text3, modifier = Modifier.size(size * 0.58f))
    }
}

/** 차 그림 위 배지 자리(차 그림 너비·높이에 대한 비율). 앞이 위쪽이다. */
private fun badgeAt(e: Effect): Pair<Float, Float> = when (e) {
    Effect.HEADLAMP -> 0.5f to 0.095f   // 앞 범퍼, 두 전조등 사이
    Effect.WIPER -> 0.5f to 0.205f      // 앞유리 가운데
    Effect.LIGHT -> 0.5f to 0.55f       // 실내 가운데(앰비언트는 실내 전체)
    Effect.WINDOW -> 0.11f to 0.54f     // 왼쪽 앞·뒤 창문 사이
    Effect.FAN -> 0.5f to 0.79f         // 송풍
}

/**
 * 위에서 본 차. 앰비언트 5구역(대시보드, 1열 좌·우, 2열 좌·우), 창문 4개(열린 만큼 청록), 송풍 단,
 * 앞 와이퍼(움직이면 청록 날), 전조등·미등(앞 흰빛 / 앞 호박색 + 뒤 빨강).
 * zones 값 null = 꺼짐. windows = 0~100 %. lamp null = 그리지 않음(전조등을 쓰지 않음).
 * badges: 이 효과들의 아이콘을 해당 부분 위에 얹는다. active: 그중 지금 움직이는 것.
 */
@Composable
fun CarMap(
    zones: List<Int?>, windows: List<Int>, fan: Int?, width: Dp, modifier: Modifier = Modifier, describe: Boolean = true,
    wiper: Boolean = false, lamp: LampMode? = null, badges: Set<Effect> = emptySet(), active: Set<Effect> = emptySet(),
) {
    val tm = rememberTextMeasurer()
    val desc = "car:zones=" + zones.joinToString(",") { it?.let { c -> "%06X".format(c and 0xFFFFFF) } ?: "off" } +
        ";win=" + windows.joinToString(",") + ";fan=" + (fan ?: 0) + ";wiper=$wiper;lamp=${lamp ?: "-"}"
    // 재생 중에는 박자마다 그림이 바뀐다. 설명까지 바뀌면 접근성 이벤트가 쏟아지므로 고정 문구를 쓴다.
    val label = if (describe) desc else "car:live"
    val height = width * 520f / 300f
    Box(modifier.size(width, height).testTag("car_map").semantics { contentDescription = label }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            fun col(i: Int): Color = zones.getOrNull(i)?.let { Color(0xFF000000.toInt() or it) } ?: Cf.showOff
            // 차체
            val bodyL = w * 0.14f; val bodyR = w * 0.86f; val bodyT = h * 0.03f; val bodyB = h * 0.97f
            drawRoundRect(Cf.surface2, Offset(bodyL, bodyT), Size(bodyR - bodyL, bodyB - bodyT), CornerRadius(w * 0.2f))
            drawRoundRect(Cf.line, Offset(bodyL, bodyT), Size(bodyR - bodyL, bodyB - bodyT), CornerRadius(w * 0.2f), style = Stroke(3f))
            // 앞유리·뒷유리
            drawRoundRect(Cf.surface3, Offset(w * 0.24f, h * 0.17f), Size(w * 0.52f, h * 0.07f), CornerRadius(12f))
            drawRoundRect(Cf.surface3, Offset(w * 0.26f, h * 0.86f), Size(w * 0.48f, h * 0.05f), CornerRadius(12f))
            if (lamp != null) lamps(lamp, w, h)
            wipers(wiper, w, h)
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
            // 송풍: 배지가 있으면 배지 옆에 단 수, 없으면 동그라미 안에 단 수
            if (fan != null && fan > 0) {
                val fanBadge = Effect.FAN in badges && width >= 200.dp
                val r = tm.measure(if (fanBadge) "${fan}단" else "$fan", TextStyle(fontFamily = AstaSans, fontSize = (w * (if (fanBadge) 0.06f else 0.09f) / density).sp, color = Cf.text))
                if (fanBadge) drawText(r, topLeft = Offset(w * 0.58f, h * 0.79f - r.size.height / 2f))
                else {
                    drawCircle(Cf.accent2Soft, w * 0.08f, Offset(w * 0.5f, h * 0.79f))
                    drawText(r, topLeft = Offset(w * 0.5f - r.size.width / 2f, h * 0.79f - r.size.height / 2f))
                }
            }
        }
        // 작은 그림(함께 카운트다운 170 dp 등)에서는 배지가 너무 작아 생략한다
        if (width >= 200.dp) {
            val b = width * 0.11f
            badges.forEach { e ->
                val (fx, fy) = badgeAt(e)
                EffectBadge(e, e in active, b, Modifier.offset(x = width * fx - b / 2, y = height * fy - b / 2).testTag("map_badge_${e.name}"))
            }
        }
    }
}

/** 앞 전조등 2개(위쪽)와 뒤 미등 2개(아래쪽). LOW: 앞 흰빛 + 빛 번짐, TAIL: 앞 호박색, 둘 다 뒤 빨강. */
private fun DrawScope.lamps(lamp: LampMode, w: Float, h: Float) {
    val front = when (lamp) { LampMode.LOW -> LampOn; LampMode.TAIL -> LampTail; LampMode.OFF -> Cf.surface3 }
    val rear = if (lamp == LampMode.OFF) Cf.surface3 else LampRear
    listOf(w * 0.2f, w * 0.64f).forEach { x ->
        val c = Offset(x + w * 0.08f, h * 0.06f)
        if (lamp == LampMode.LOW) drawCircle(Brush.radialGradient(listOf(LampOn.copy(alpha = 0.55f), Color.Transparent), c, w * 0.16f), w * 0.16f, c)
        drawRoundRect(front, Offset(x, h * 0.048f), Size(w * 0.16f, h * 0.024f), CornerRadius(10f))
        val rc = Offset(x + w * 0.08f, h * 0.94f)
        if (lamp != LampMode.OFF) drawCircle(Brush.radialGradient(listOf(LampRear.copy(alpha = 0.35f), Color.Transparent), rc, w * 0.1f), w * 0.1f, rc)
        drawRoundRect(rear, Offset(x, h * 0.928f), Size(w * 0.16f, h * 0.022f), CornerRadius(10f))
    }
}

/** 앞유리 아래 와이퍼 날 2개. 움직이면 비스듬히 선 청록, 아니면 누운 회색. */
private fun DrawScope.wipers(on: Boolean, w: Float, h: Float) {
    val y = h * 0.232f
    val c = if (on) Cf.accent2 else Cf.line
    val sw = w * 0.012f
    listOf(w * 0.29f, w * 0.52f).forEach { x ->
        val end = if (on) Offset(x + w * 0.15f, h * 0.18f) else Offset(x + w * 0.19f, y)
        drawLine(c, Offset(x, y), end, sw, StrokeCap.Round)
    }
}
