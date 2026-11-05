package app.carflash.ui.components

import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.carflash.app
import app.carflash.data.Effect
import app.carflash.haechi.HaechiState
import app.carflash.ui.theme.Cf
import app.carflash.ui.theme.CfType
import app.carflash.vehicle.Gear

fun Effect.icon(): ImageVector = when (this) {
    Effect.LIGHT -> Icons.Rounded.Lightbulb
    Effect.WINDOW -> Icons.Rounded.Window
    Effect.WIPER -> Icons.Rounded.Sensors
    Effect.FAN -> Icons.Rounded.Air
}

/** 앱 상단줄(높이 112). 제목 + 기어 칩 + 오른쪽 버튼. */
@Composable
fun AppHead(title: String, gear: Gear, onBack: (() -> Unit)? = null, showGear: Boolean = true, right: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(Cf.headH).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (onBack != null) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", Modifier.testTag("back"), onClick = onBack)
        }
        Text(title, style = CfType.headline, color = Cf.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (showGear) GearChip(gear)
        right()
    }
}

@Composable
fun GearChip(gear: Gear) {
    val parked = gear == Gear.P
    val fg = if (parked) Cf.ok else Cf.stop
    val bg = if (parked) Cf.okSoft else Cf.stopSoft
    val g = when (gear) { Gear.P -> "P"; Gear.R -> "R"; Gear.N -> "N"; Gear.D -> "D"; Gear.UNKNOWN -> "?" }
    val label = when (gear) { Gear.P -> "정차 중"; Gear.UNKNOWN -> "확인 중"; else -> "주행 기어" }
    Row(
        Modifier.height(56.dp).clip(CircleShape).background(bg).padding(horizontal = 24.dp)
            .testTag("gear_chip").semantics { contentDescription = "gear:$g" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(fg), contentAlignment = Alignment.Center) {
            Text(g, style = CfType.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = Cf.bg)
        }
        Text(label, style = CfType.label, color = fg)
    }
}

enum class BtnKind { Primary, Plain, Sync, Ghost }

@Composable
fun CfButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Plain,
    large: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg = when (kind) {
        BtnKind.Primary -> if (pressed) Cf.accentPress else Cf.accent
        BtnKind.Sync -> Cf.accent2
        BtnKind.Ghost -> Color.Transparent
        BtnKind.Plain -> if (pressed) Cf.surface3 else Cf.surface2
    }
    val fg = when (kind) { BtnKind.Primary -> Cf.onAccent; BtnKind.Sync -> Color(0xFF061218); else -> Cf.text }
    Row(
        modifier
            .heightIn(min = if (large) Cf.touchLg else Cf.touchMin)
            .widthIn(min = Cf.touchMin)
            .scale(if (pressed) 0.97f else 1f)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(bg)
            .clickable(src, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (large) 48.dp else 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(if (large) 48.dp else 40.dp))
        Text(text, style = if (large) CfType.title else CfType.label, color = fg, maxLines = 1)
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, modifier: Modifier = Modifier, plain: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.size(Cf.touchMin).clip(CircleShape).background(if (plain) Color.Transparent else Cf.surface2)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Cf.text, modifier = Modifier.size(40.dp)) }
}

/** 홈 두 번째 줄 타일 버튼(높이 168). */
@Composable
fun TileButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier.height(168.dp).alpha(if (enabled) 1f else 0.38f).clip(RoundedCornerShape(Cf.rMd)).background(Cf.surface2)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Cf.text, modifier = Modifier.size(48.dp))
        Text(text, style = CfType.label, color = Cf.text, maxLines = 1)
    }
}

/** 커버: assets 그림이 있으면 그림, 없으면 그라데이션. */
@Composable
fun Cover(path: String?, gradient: Pair<Long, Long>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp = remember(path) {
        path?.let { p -> runCatching { ctx.assets.open(p).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull() }
    }
    if (bmp != null) {
        Image(bmp, null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(Brush.linearGradient(listOf(Color(gradient.first), Color(gradient.second)))))
    }
}

@Composable
fun EffectBadges(effects: Set<Effect>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Effect.entries.filter { it in effects }.forEach { e ->
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x73000000)).padding(4.dp)) {
                Icon(e.icon(), e.label, tint = Color.White)
            }
        }
    }
}

/**
 * 해치 3D. 앱 전체에서 WebView 하나를 옮겨 붙인다.
 * 상태를 정하면 HaechiHost가 JS로 넘긴다. 접근성 트리에서는 장식으로 숨긴다.
 */
@Composable
fun HaechiView(state: HaechiState, size: Dp, modifier: Modifier = Modifier, bpm: Int? = null, bust: Boolean = false) {
    val ctx = LocalContext.current
    val host = ctx.app.haechi
    AndroidView(
        modifier = modifier.size(size).testTag("haechi"),
        factory = { c ->
            FrameLayout(c).apply {
                val w = host.view()
                addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        },
        update = {
            host.setFraming(bust)
            host.setState(state, bpm)
        },
    )
    DisposableEffect(Unit) { onDispose { } }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun FillBox(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize()) { content() }
