package app.carflash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.carflash.data.Effect
import app.carflash.data.Show
import app.carflash.ui.components.AppHead
import app.carflash.ui.components.Banner
import app.carflash.ui.components.BannerKind
import app.carflash.ui.theme.Cf
import app.carflash.ui.theme.CfType
import app.carflash.ui.theme.Layout
import app.carflash.ui.theme.LocalLayout
import app.carflash.vehicle.Gear
import app.carflash.vehicle.VehicleGateway.Companion.log
import kotlinx.coroutines.delay

/**
 * B1 코드로 불러오기. 숫자 6자리 키패드만(키 높이 120). 6번째 숫자에서 바로 찾는다(확인 버튼 없음).
 * 찾으면 이름을 보여 주고 1초 뒤 준비 화면으로. landscape에서는 키패드를 왼쪽(운전석 쪽)에 둔다.
 */
@Composable
fun CodeScreen(
    gear: Gear, recentCodes: List<String>,
    /** 코드 → (찾았다는 제목, 한 줄 설명). 없으면 null. */
    lookup: (String) -> Pair<String, String>?, onBack: () -> Unit, onFound: (String) -> Unit,
    title: String = "코드로 불러오기", prompt: String = "친구가 보낸 6자리 숫자", screenTag: String = "screen_code",
    missingText: String = "그 코드의 플래시몹이 없어요",
) {
    val port = LocalLayout.current == Layout.Portrait
    var code by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<Pair<String, String>?>(null) }
    var missing by remember { mutableStateOf(false) }
    val clip = LocalClipboardManager.current

    fun set(c: String) {
        code = c.filter { it.isDigit() }.take(6)
        found = null; missing = false
        if (code.length == 6) {
            val s = lookup(code)
            found = s; missing = s == null
        }
    }
    LaunchedEffect(found) {
        found ?: return@LaunchedEffect
        delay(1000)
        log("CODE found $code")
        onFound(code)
    }

    val keypad: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("지우기", "0", "붙여넣기")).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { k ->
                        val fn = k.length > 1
                        val tag = when (k) { "지우기" -> "key_del"; "붙여넣기" -> "key_paste"; else -> "key_$k" }
                        Box(
                            Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(Cf.rMd)).background(if (fn) Cf.surface else Cf.surface2)
                                .clickable(role = Role.Button) {
                                    when (k) {
                                        "지우기" -> set(code.dropLast(1))
                                        "붙여넣기" -> set(clip.getText()?.text ?: "")
                                        else -> if (code.length < 6) set(code + k)
                                    }
                                }.testTag(tag),
                            contentAlignment = Alignment.Center,
                        ) { Text(k, style = if (fn) CfType.body else CfType.headline, color = Cf.text) }
                    }
                }
            }
        }
    }
    val boxes: @Composable () -> Unit = {
        Row(Modifier.testTag("code_boxes").semantics { contentDescription = "entered:$code" }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (0 until 6).forEach { i ->
                val ch = code.getOrNull(i)
                Box(
                    Modifier.size(96.dp, 120.dp).clip(RoundedCornerShape(20.dp))
                        .background(if (i == code.length) Cf.accentSoft else Cf.surface2),
                    contentAlignment = Alignment.Center,
                ) { Text(ch?.toString() ?: "·", style = CfType.headline, color = if (ch != null) Cf.text else Cf.textOff) }
                if (i == 2) Box(Modifier.width(8.dp))
            }
        }
    }
    val result: @Composable () -> Unit = {
        val s = found
        if (s != null) {
            Banner(s.first, s.second, BannerKind.Ok, Modifier.testTag("code_found"))
        } else if (missing) {
            Banner(missingText, "숫자를 다시 확인해 주세요", BannerKind.Stop, Modifier.testTag("code_missing"), icon = Icons.Rounded.SearchOff)
        }
    }
    val chips: @Composable () -> Unit = {
        if (recentCodes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                recentCodes.forEach { c ->
                    Box(
                        Modifier.height(Cf.touchMin).clip(CircleShape).background(Cf.surface2).clickable(role = Role.Button) { set(c) }
                            .padding(horizontal = 28.dp).testTag("chip_code_$c"),
                        contentAlignment = Alignment.Center,
                    ) { Text("${if (port) "최근 " else ""}${c.take(3)} ${c.drop(3)}", style = CfType.label, color = Cf.text) }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Cf.bg).testTag(screenTag)) {
        AppHead(title, gear, onBack = onBack, showGear = !port)
        if (port) {
            Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(prompt, style = CfType.body, color = Cf.text2)
                boxes()
                result()
                keypad(Modifier.fillMaxWidth())
                chips()
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 32.dp, end = 48.dp, bottom = 32.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                keypad(Modifier.width(720.dp).fillMaxHeight())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    Text(prompt, style = CfType.body, color = Cf.text2)
                    boxes()
                    result()
                    if (recentCodes.isNotEmpty()) Text("최근 코드", style = CfType.label, color = Cf.text2, textAlign = TextAlign.Start)
                    chips()
                }
            }
        }
    }
}
