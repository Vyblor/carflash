package app.carflash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.carflash.data.Effect
import app.carflash.haechi.HaechiState
import app.carflash.ui.components.AppHead
import app.carflash.ui.components.BtnKind
import app.carflash.ui.components.CfButton
import app.carflash.ui.components.HaechiView
import app.carflash.ui.components.icon
import app.carflash.ui.theme.Cf
import app.carflash.ui.theme.CfType
import app.carflash.ui.theme.Layout
import app.carflash.ui.theme.LocalLayout
import app.carflash.vehicle.Gear
import app.carflash.vehicle.VehicleCaps

/** A1 첫 실행: 안전 약속 3줄 + 이 차에서 쓸 수 있는 효과. 버튼은 하나. */
@Composable
fun OnboardingScreen(gear: Gear, caps: VehicleCaps?, onDone: () -> Unit) {
    val port = LocalLayout.current == Layout.Portrait
    Column(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_onboard")) {
        AppHead("카플래시", gear)
        if (port) {
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Promises()
                CapsPanel(caps)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    CfButton("알겠어요, 시작", Modifier.width(420.dp).testTag("onboard_start"), BtnKind.Primary, large = true, onClick = onDone)
                    Spacer(Modifier.weight(1f))
                    HaechiView(HaechiState.HELLO, 240.dp)
                }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 38.dp, bottom = 48.dp)) {
                Column(Modifier.width(760.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    Promises()
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        CfButton("알겠어요, 시작", Modifier.width(420.dp).testTag("onboard_start"), BtnKind.Primary, large = true, onClick = onDone)
                        Spacer(Modifier.weight(1f))
                        HaechiView(HaechiState.HELLO, 320.dp, Modifier.padding(bottom = 0.dp))
                    }
                }
                Spacer(Modifier.width(72.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    CapsPanel(caps)
                }
            }
        }
    }
}

@Composable
private fun Promises() {
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        Text("차가 무대가 돼요", style = CfType.headline, color = Cf.text)
        Promise(1, "정차(P) 중에만 플래시몹을 틀어요", "주행 중에는 열리지 않아요")
        Promise(2, "기어를 바꾸면 1초 안에 멈춰요", "조명·창문·송풍은 플래시몹 전 상태로 돌아가요")
        Promise(3, "창문이 움직일 수 있어요", "플래시몹마다 미리 알려 드리고, 끌 수 있어요")
    }
}

@Composable
private fun Promise(n: Int, title: String, sub: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Cf.accentSoft), contentAlignment = Alignment.Center) {
            Text("$n", style = CfType.title, color = Cf.accent)
        }
        Column {
            Text(title, style = CfType.titleSm, color = Cf.text)
            Text(sub, style = CfType.caption, color = Cf.text2)
        }
    }
}

@Composable
fun CapsPanel(caps: VehicleCaps?) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("caps_panel")) {
        Text("이 차에서 쓸 수 있는 효과", style = CfType.title, color = Cf.text)
        if (caps == null) {
            Text("차량 기능을 점검하고 있어요…", style = CfType.body, color = Cf.text2)
            return@Column
        }
        Effect.entries.forEach { e ->
            val (ok, detail) = when (e) {
                Effect.LIGHT -> caps.light to (if (caps.ambientZones > 1) "앰비언트 ${caps.ambientZones}구역" else "앰비언트 조명")
                Effect.WINDOW -> caps.window to "창문 ${caps.windows}개"
                Effect.WIPER -> caps.wiper to "앞 와이퍼"
                Effect.FAN -> caps.fan to "송풍 1~${caps.fanMax}단"
                Effect.HEADLAMP -> caps.headlamp to "전조등·미등"
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Cf.rMd)).background(if (ok) Cf.okSoft else Cf.surface2)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .testTag("cap_${e.name.lowercase()}").semantics { contentDescription = "cap:${e.name}:${if (ok) "on" else "off"}" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                androidx.compose.material3.Icon(e.icon(), null, tint = if (ok) Cf.ok else Cf.text3, modifier = Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.label, style = CfType.body, color = Cf.text)
                    Text(if (ok) detail else "이 차에서는 쓸 수 없어요", style = CfType.caption, color = Cf.text2)
                }
                Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RemoveCircleOutline, null, tint = if (ok) Cf.ok else Cf.text3, modifier = Modifier.size(40.dp))
            }
        }
    }
}
