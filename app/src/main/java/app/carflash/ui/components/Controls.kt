package app.carflash.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.carflash.ui.theme.Cf
import app.carflash.ui.theme.CfType

/** 효과 토글 한 줄(높이 100). unsupported면 스위치를 숨기고 이유를 쓴다. */
@Composable
fun FxToggle(
    icon: ImageVector, label: String, sub: String, on: Boolean, tag: String,
    modifier: Modifier = Modifier, unsupported: Boolean = false, onToggle: (Boolean) -> Unit,
) {
    val state = if (unsupported) "unsupported" else if (on) "on" else "off"
    Row(
        modifier.fillMaxWidth().heightIn(min = Cf.touchLg).clip(RoundedCornerShape(Cf.rMd)).background(Cf.surface2)
            .alpha(if (unsupported) 0.5f else 1f)
            .clickable(enabled = !unsupported, role = Role.Switch) { onToggle(!on) }
            .padding(horizontal = 24.dp)
            .testTag(tag).semantics { contentDescription = "$tag:$state" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(icon, null, tint = Cf.text, modifier = Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = CfType.body, color = Cf.text)
            Text(sub, style = CfType.caption, color = Cf.text2)
        }
        if (!unsupported) Switch(on)
    }
}

@Composable
fun Switch(on: Boolean) {
    val x by animateDpAsState(if (on) 38.dp else 6.dp, label = "sw")
    Box(Modifier.size(80.dp, 48.dp).clip(CircleShape).background(if (on) Cf.accent else Cf.surface3)) {
        Box(Modifier.offset(x, 6.dp).size(36.dp).clip(CircleShape).background(if (on) Color.White else Cf.text2))
    }
}

enum class BannerKind { Warn, Stop, Ok, Info }

@Composable
fun Banner(title: String, sub: String?, kind: BannerKind = BannerKind.Warn, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val (bg, fg) = when (kind) {
        BannerKind.Warn -> Cf.warnSoft to Cf.warn
        BannerKind.Stop -> Cf.stopSoft to Cf.stop
        BannerKind.Ok -> Cf.okSoft to Cf.ok
        BannerKind.Info -> Cf.accent2Soft to Cf.accent2
    }
    val ic = icon ?: when (kind) { BannerKind.Ok -> Icons.Rounded.CheckCircle; BannerKind.Info -> Icons.Rounded.Info; else -> Icons.Rounded.Warning }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Cf.rMd)).background(bg).padding(horizontal = 32.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(ic, null, tint = fg, modifier = Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = CfType.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Cf.text)
            if (sub != null) Text(sub, style = CfType.caption, color = Cf.text2)
        }
    }
}

/** 정지 버튼. 높이 140, 플래시몹 중 한 번에 맞혀야 하는 버튼. */
@Composable
fun StopButton(text: String = "플래시몹 멈추기", modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(Cf.touchXl).widthIn(min = 340.dp).clip(CircleShape).background(Cf.stopFill)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 48.dp).testTag("stop"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(Color.White))
        Text(text, style = CfType.title, color = Color.White)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    // 너비를 레이아웃으로 바꾸면 100ms마다 접근성 서브트리 변경 이벤트가 나간다. 그리기만 바꾼다.
    val f = fraction.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(24.dp).clip(CircleShape).background(Cf.surface3).testTag("progress")
        .drawBehind {
            if (f > 0f) drawRoundRect(Cf.accent, size = androidx.compose.ui.geometry.Size(size.width * f, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        })
}

fun mmss(ms: Long): String { val s = (ms / 1000).toInt(); return "%d:%02d".format(s / 60, s % 60) }

@Composable
fun CueChip(text: String, color: Int? = null, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(Cf.rMd)).background(Cf.surface2).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (color != null) Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or color)))
        if (icon != null) Icon(icon, null, tint = Cf.text, modifier = Modifier.size(32.dp))
        Text(text, style = CfType.label, color = Cf.text)
    }
}

@Composable
fun CodeDisplay(code: String, modifier: Modifier = Modifier) {
    Text("${code.take(3)} ${code.drop(3)}", style = CfType.display, color = Cf.text,
        modifier = modifier.testTag("show_code").semantics { contentDescription = "code:$code" })
}

@Composable
fun Spacer16() = Box(Modifier.width(16.dp))
