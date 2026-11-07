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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.carflash.data.Effect
import app.carflash.data.has
import app.carflash.data.GroupInfo
import app.carflash.data.Show
import app.carflash.data.Song
import app.carflash.haechi.HaechiState
import app.carflash.show.Frame
import app.carflash.show.SectionPlan
import app.carflash.show.BatteryGuard
import app.carflash.show.StopReason
import app.carflash.show.Timeline
import app.carflash.show.WINDOW_LIMIT
import app.carflash.ui.components.AppHead
import app.carflash.ui.components.Banner
import app.carflash.ui.components.BannerKind
import app.carflash.ui.components.BtnKind
import app.carflash.ui.components.CarMap
import app.carflash.ui.components.EffectBadge
import app.carflash.vehicle.LampMode
import app.carflash.vehicle.WiperMode
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import app.carflash.ui.components.CfButton
import app.carflash.ui.components.CodeDisplay
import app.carflash.ui.components.Cover
import app.carflash.ui.components.FxToggle
import app.carflash.ui.components.GearChip
import app.carflash.ui.components.HaechiView
import app.carflash.ui.components.ProgressBar
import app.carflash.ui.components.StopButton
import app.carflash.ui.components.icon
import app.carflash.ui.components.mmss
import app.carflash.ui.theme.AstaSans
import app.carflash.ui.theme.Cf
import app.carflash.ui.theme.CfType
import app.carflash.ui.theme.Layout
import app.carflash.ui.theme.LocalLayout
import app.carflash.vehicle.Gear
import app.carflash.vehicle.VehicleCaps

/** "조명·창문은" / "송풍은" — 받침에 맞는 조사 */
internal fun restoreNote(enabled: Set<Effect>): String {
    val names = Effect.entries.filter { it in enabled }.joinToString("·") { it.label }.ifEmpty { "차" }
    return "$names${josa(enabled)} 플래시몹 전 상태로 돌려놨어요"
}

internal fun josa(enabled: Set<Effect>): String {
    val last = Effect.entries.lastOrNull { it in enabled }?.label ?: "차"
    val ch = last.last()
    return if (ch in '가'..'힣' && (ch - '가') % 28 != 0) "은" else "는"
}

private fun capOk(e: Effect, caps: VehicleCaps?) = caps != null && caps.has(e)

private fun fxSub(e: Effect, caps: VehicleCaps?, plans: List<SectionPlan>): String = when (e) {
    Effect.LIGHT -> if ((caps?.ambientZones ?: 0) > 1) "앰비언트 ${caps!!.ambientZones}구역" else "앰비언트 조명"
    Effect.WINDOW -> "최대 ${plans.maxOfOrNull { it.window } ?: WINDOW_LIMIT}%까지 내려가요"
    Effect.WIPER -> "앞 와이퍼 · 천천히"
    Effect.FAN -> "1열 · 최대 ${plans.maxOfOrNull { it.fan } ?: 5}단"
    Effect.HEADLAMP -> "전조등·미등 깜빡임 · 정차 중에만"
}

/** 차 그림 한 장에 필요한 값. active = 이 구간에서 움직이는 효과(왼쪽 목록과 같은 기준). */
internal data class Look(val zones: List<Int?>, val windows: List<Int>, val fan: Int?, val wiper: Boolean, val lamp: LampMode?, val active: Set<Effect>)

internal fun lookOf(f: Frame?, enabled: Set<Effect>): Look {
    val lampIdle = if (Effect.HEADLAMP in enabled) LampMode.OFF else null
    if (f == null) return Look(List(5) { null }, List(4) { 0 }, null, false, lampIdle, emptySet())
    val z = if (Effect.LIGHT in enabled) f.zones.map { if (it < 0) null else it } else List(5) { null }
    val w = f.windows.map { if (it < 0) 0 else it }
    return Look(z, w, f.fan.takeIf { it > 0 }, f.wiper != WiperMode.OFF, f.lamp ?: lampIdle, f.cues.map { it.effect }.toSet())
}

@Composable
internal fun LookMap(l: Look, enabled: Set<Effect>, width: Dp, modifier: Modifier = Modifier, describe: Boolean = true, badges: Boolean = true) =
    CarMap(l.zones, l.windows, l.fan, width, modifier, describe = describe, wiper = l.wiper, lamp = l.lamp,
        badges = if (badges) enabled else emptySet(), active = l.active)

/**
 * 재생 화면 왼쪽 한 줄: 아이콘 배지(차 그림 위 배지와 같은 모양) · 효과 이름 · 지금 하는 일.
 * 움직이지 않는 효과도 "쉬는 중"으로 남겨 둬서, 차 그림의 어느 부분이 무엇인지 늘 짝지어 볼 수 있다.
 */
@Composable
private fun EffectRow(e: Effect, cue: String?, color: Int?) {
    val on = cue != null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Cf.rMd)).background(if (on) Cf.surface2 else Cf.surface)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("cue_${e.name}").semantics { contentDescription = "cue:${e.name}:${cue ?: "idle"}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EffectBadge(e, on, 48.dp)
        Column(Modifier.weight(1f)) {
            Text(e.label, style = CfType.caption, color = Cf.text2)
            Text(cue ?: "쉬는 중", style = CfType.label, color = if (on) Cf.text else Cf.text3)
        }
        if (on && color != null) Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or color)))
    }
}

/** A3 플래시몹 준비 (B2 친구 플래시몹·차종 차이 포함). 창문 같은 움직임을 먼저 알리고, 끌 수 있게 한다. */
@Composable
fun PrepScreen(
    show: Show, song: Song, plans: List<SectionPlan>, caps: VehicleCaps?, gear: Gear, durationLabel: String,
    battery: Int? = null,
    onBack: () -> Unit, onStart: (Set<Effect>, Boolean) -> Unit,
) {
    val lowBattery = BatteryGuard.low(battery)
    val criticalBattery = BatteryGuard.critical(battery)
    val port = LocalLayout.current == Layout.Portrait
    val inShow = Effect.entries.filter { it in show.effects }
    var enabled by remember(show.id, caps, lowBattery) {
        mutableStateOf(inShow.filter { capOk(it, caps) && !(lowBattery && (it == Effect.WINDOW || it == Effect.FAN)) }.toSet())
    }
    var sound by remember { mutableStateOf(true) }
    val unsupported = inShow.filter { caps != null && !capOk(it, caps) }
    val parked = gear == Gear.P
    val first = remember(show.id, enabled) { Timeline.frame(song, plans, 0, enabled) }

    val left: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val origin = when (show.origin) { Show.Origin.FRIEND -> "친구 플래시몹 · "; Show.Origin.MINE -> "내 플래시몹 · "; else -> "" }
            Text("$origin$durationLabel · 자체 음원 「${song.title}」 · ${song.bpm} BPM", style = CfType.caption, color = Cf.text2,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
            if (unsupported.isNotEmpty()) {
                Banner("이 차에서는 ${unsupported.joinToString("·") { it.label }}를 쓸 수 없어요", "나머지 효과로 틀어요", BannerKind.Info,
                    Modifier.testTag("banner_unsupported"))
            }
            // B2: 친구 차와 조명 구역 수가 다르면 한 줄로 알린다(친구 플래시몹은 5구역 차에서 만든 것으로 둔다)
            val zones = caps?.ambientZones ?: 5
            if (show.origin == Show.Origin.FRIEND && Effect.LIGHT in enabled && zones in 1..4 &&
                plans.any { it.pattern == app.carflash.show.Pattern.ALTERNATE || it.pattern == app.carflash.show.Pattern.RAINBOW }) {
                Banner("친구 차와 효과가 조금 달라요", "친구 차는 조명 5구역, 이 차는 ${zones}구역이에요. 색 흐름을 한 구역에 차례로 틀어요",
                    BannerKind.Info, Modifier.testTag("banner_friend"))
            }
            if (criticalBattery) {
                Banner("배터리가 너무 부족해요 (${battery}%)", "${BatteryGuard.CRITICAL}% 아래에서는 플래시몹을 틀지 않아요. 충전한 뒤에 다시 틀어 주세요",
                    BannerKind.Stop, Modifier.testTag("banner_battery"), icon = Icons.Rounded.BatteryAlert)
            } else if (lowBattery) {
                Banner("배터리가 부족해요 (${battery}%)", "창문·송풍은 끄고 조명 밝기를 ${BatteryGuard.DIM_MAX}%로 낮춰서 틀어요",
                    BannerKind.Warn, Modifier.testTag("banner_battery"), icon = Icons.Rounded.BatteryAlert)
            }
            inShow.forEach { e ->
                val unsup = e in unsupported
                val saved = lowBattery && (e == Effect.WINDOW || e == Effect.FAN)
                FxToggle(e.icon(), e.label, if (unsup) "이 차에서는 쓸 수 없어요" else if (saved) "배터리 보호로 꺼짐" else fxSub(e, caps, plans), e in enabled,
                    tag = "fx_${e.name.lowercase()}", unsupported = unsup || (lowBattery && (e == Effect.WINDOW || e == Effect.FAN))) { on ->
                    enabled = if (on) enabled + e else enabled - e
                }
            }
            FxToggle(Icons.Rounded.MusicNote, "소리", "차 스피커 · 크기 중간", sound, tag = "fx_sound") { sound = it }
            Spacer(Modifier.weight(1f))
            if (!parked) {
                Banner("정차(P)에서만 시작할 수 있어요", "기어를 P로 바꾸면 시작 버튼이 켜져요", BannerKind.Stop, Modifier.testTag("banner_gear"))
            } else if (Effect.WINDOW in enabled && (caps?.windows ?: 0) > 0) {
                Banner("창문 ${caps!!.windows}개가 내려가요", "밖에 사람이나 물건이 없는지 확인하세요", BannerKind.Warn,
                    Modifier.testTag("banner_window"), icon = Icons.Rounded.Window)
            }
            CfButton("플래시몹 시작", Modifier.fillMaxWidth().testTag("prep_start"), BtnKind.Primary, large = true,
                enabled = parked && caps != null && !criticalBattery, icon = Icons.Rounded.PlayArrow) { onStart(enabled, sound) }
        }
    }
    Column(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_prep")) {
        AppHead(show.title, gear, onBack = onBack)
        if (port) {
            // 시작 버튼이 늘 화면 안에 있도록 스크롤 없이 쌓는다(분할 화면 높이 약 1100)
            Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(Cf.rLg))) {
                    Cover(show.cover, show.gradient, Modifier.fillMaxSize())
                }
                left(Modifier.fillMaxWidth().weight(1f))
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                left(Modifier.width(780.dp).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("첫 장면 미리보기", style = CfType.label, color = Cf.text2)
                        LookMap(lookOf(first, enabled), enabled, 400.dp)
                    }
                    HaechiView(HaechiState.HELLO, 240.dp, Modifier.align(Alignment.BottomEnd))
                }
            }
        }
    }
}

/** A4 카운트다운. 정지 버튼은 처음부터 있다. */
@Composable
fun CountdownScreen(show: Show, secondsLeft: Int, enabled: Set<Effect>, gear: Gear, group: GroupInfo? = null, onStop: () -> Unit) {
    val port = LocalLayout.current == Layout.Portrait
    val tint = if (group != null) 0x403FD5FF else 0x40A05CFF
    Box(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_countdown")
        .semantics { contentDescription = if (group != null) "countdown:group:${group.cars}:${group.mySlot}" else "countdown:solo" }) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(tint), Color.Transparent), radius = 900f)))
        Column(Modifier.fillMaxSize()) {
            AppHead(if (group != null) "${show.title} · ${group.cars}대" else show.title, gear)
            Row(Modifier.weight(1f).padding(horizontal = 48.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (group != null) "${group.cars}대가 함께 시작해요" else "곧 시작해요", style = CfType.title, color = Cf.text2)
                    Text("$secondsLeft", style = TextStyle(fontFamily = AstaSans, fontWeight = FontWeight.ExtraBold, fontSize = 360.sp, lineHeight = 360.sp),
                        color = if (group != null) Cf.accent2 else Cf.text, modifier = Modifier.testTag("countdown_num").semantics { contentDescription = "count:$secondsLeft" })
                    if (group != null) Text("서버 시각 ${group.serverStartLabel}에 시작 · 이 차 지연 보정 +${group.latencyMs}ms", style = CfType.caption, color = Cf.text2)
                    if (Effect.WINDOW in enabled) Text("창문이 곧 내려가요", style = CfType.body, color = Cf.text2)
                }
                if (group != null && !port) {
                    // 시작 순간 모든 차의 해치가 같은 서버 시각에 춤을 시작한다
                    HaechiView(HaechiState.WAIT, 300.dp, Modifier.align(Alignment.CenterVertically))
                    Spacer(Modifier.width(24.dp))
                    Row(Modifier.align(Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        group.seats.filter { !it.empty }.forEach { s ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CarMap(List(5) { null }, List(4) { 0 }, null, 170.dp)
                                Text(app.carflash.data.Seat.NAMES[s.pos] + if (s.me) " · 나" else "", style = CfType.caption, color = Cf.text2)
                            }
                        }
                    }
                } else {
                    HaechiView(HaechiState.WAIT, if (port) 300.dp else 380.dp, Modifier.align(Alignment.CenterVertically))
                    if (!port) {
                        Spacer(Modifier.width(48.dp))
                        LookMap(lookOf(null, enabled), enabled, 340.dp, Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
            StopButton(if (group != null) "내 차만 멈추기" else "플래시몹 멈추기", modifier = Modifier.padding(32.dp), onClick = onStop)
        }
    }
}

/** A5 재생. 자동으로 아무것도 넘기지 않는다. 화면은 지금 움직이는 것만 보여 준다. */
@Composable
fun PlayingScreen(show: Show, song: Song, posMs: Long, frame: Frame?, enabled: Set<Effect>, gear: Gear, group: GroupInfo? = null, onStop: () -> Unit) {
    val port = LocalLayout.current == Layout.Portrait
    val total = (song.durationSec * 1000).toLong()
    val look = lookOf(frame, enabled)
    val z = look.zones
    val c0 = z.firstOrNull() ?: 0xA05CFF
    val c1 = z.getOrNull(3) ?: 0x00E5FF
    Box(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_playing")
        .semantics { contentDescription = "playing:${show.id}" }) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x66000000 or c0), Color.Transparent), center = androidx.compose.ui.geometry.Offset(1300f, 450f), radius = 700f)))
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x55000000 or c1), Color.Transparent), center = androidx.compose.ui.geometry.Offset(1500f, 900f), radius = 500f)))
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(Cf.headH).padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (group != null) "${show.title} · ${group.cars}대 함께" else show.title, style = CfType.title, color = Cf.text, modifier = Modifier.weight(1f))
                GearChip(gear)
            }
            Row(Modifier.weight(1f).padding(horizontal = 48.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("지금 차에서 움직이는 것", style = CfType.label, color = Cf.text2)
                    Effect.entries.filter { it in enabled }.forEach { e ->
                        val cue = frame?.cues?.firstOrNull { it.effect == e }?.text
                        EffectRow(e, cue, if (e == Effect.LIGHT) z.firstOrNull() else null)
                    }
                    Spacer(Modifier.weight(1f))
                    HaechiView(HaechiState.DANCE, if (port) 320.dp else 400.dp, Modifier.align(Alignment.CenterHorizontally), bpm = song.bpm)
                }
                if (!port) {
                    Spacer(Modifier.width(48.dp))
                    LookMap(look, enabled, 430.dp, Modifier.align(Alignment.CenterVertically), describe = false)
                }
            }
            Row(Modifier.fillMaxWidth().padding(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                StopButton(if (group != null) (if (port) "내 차만" else "내 차만 멈추기") else if (port) "멈추기" else "플래시몹 멈추기", onClick = onStop)
                Column(Modifier.weight(1f)) {
                    ProgressBar(posMs.toFloat() / total.coerceAtLeast(1))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(mmss(posMs), style = CfType.label, color = Cf.text2, modifier = Modifier.weight(1f).testTag("time_pos"))
                        Text(mmss(total), style = CfType.label, color = Cf.text2)
                    }
                }
            }
        }
    }
}

/** A6 플래시몹 끝 + 코드, 그리고 E2 멈춘 이유(stopReason != null). */
@Composable
fun ResultScreen(
    show: Show, song: Song, enabled: Set<Effect>, gear: Gear, durationLabel: String,
    stopReason: StopReason?, stoppedAtMs: Long = 0, restored: String = "",
    onBack: () -> Unit, onPrimary: () -> Unit, onSecondary: () -> Unit,
) {
    val port = LocalLayout.current == Layout.Portrait
    val ended = stopReason == null
    val fx = Effect.entries.filter { it in enabled }.joinToString(" · ") { it.label }
    Column(Modifier.fillMaxSize().background(Cf.bg).testTag(if (ended) "screen_end" else "screen_stopped")
        .semantics { contentDescription = "result:${stopReason?.name ?: "END"}" }) {
        AppHead(if (ended) "플래시몹 끝" else stopReason!!.title, gear, onBack = onBack)
        val main: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (ended) {
                    Text(show.title, style = CfType.headline, color = Cf.text)
                    Text("$durationLabel · $fx", style = CfType.body, color = Cf.text2)
                    Text(restoreNote(enabled), style = CfType.caption, color = Cf.text2, modifier = Modifier.testTag("restore_note"))
                } else {
                    // E2: 멈춘 이유 → 어디서 멈췄나 → 무엇을 되돌렸나
                    Text("「${show.title}」 ${mmss(stoppedAtMs)}에서 멈췄어요", style = CfType.body, color = Cf.text2)
                    Text(stopReason!!.detail, style = CfType.caption, color = Cf.text2, modifier = Modifier.testTag("stop_detail"))
                    Banner("차 상태를 플래시몹 전으로 되돌렸어요", restored.ifEmpty { "바뀐 것 없음" }, BannerKind.Ok,
                        Modifier.testTag("banner_restored").semantics { contentDescription = "restored:$restored" })
                }
                // 전조등: 차가 AUTO를 앱에 OFF로 알려 주고(에뮬레이터 시험), 주행 중에는 복원을 거부한다(50001). 운전자가 직접 확인하게 한다.
                if (Effect.HEADLAMP in enabled) {
                    if (stopReason == StopReason.GEAR) Banner("전조등 스위치를 직접 확인해 주세요", "주행 중에는 차가 전조등 되돌리기를 막아요. P로 바꾸면 앱이 다시 되돌려요",
                        BannerKind.Warn, Modifier.testTag("note_headlamp"))
                    else Banner("전조등 스위치를 확인해 주세요", "AUTO였다면 다시 AUTO로 맞춰 주세요", BannerKind.Info, Modifier.testTag("note_headlamp"))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    HaechiView(if (ended) HaechiState.CHEER else HaechiState.REST, if (port) 260.dp else 360.dp, Modifier.align(Alignment.Center))
                }
                CfButton(if (ended) "다시 보기" else "처음부터 다시", Modifier.fillMaxWidth().testTag("result_primary"), BtnKind.Primary, large = true,
                    enabled = gear == Gear.P, icon = Icons.Rounded.Replay, onClick = onPrimary)
                CfButton(if (ended) "다른 플래시몹 고르기" else "닫기", Modifier.fillMaxWidth().testTag("result_secondary"), large = true, onClick = onSecondary)
            }
        }
        val share: @Composable () -> Unit = {
            val code = show.code
            if (code != null) {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("친구 차에서도 틀기", style = CfType.title, color = Cf.text)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Cf.rLg)).background(Cf.surface).padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("플래시몹 코드", style = CfType.caption, color = Cf.text2)
                        CodeDisplay(code)
                        Text("친구가 카플래시 홈 → 코드로 불러오기에 넣으면 같은 플래시몹이 열려요", style = CfType.body, color = Cf.text2)
                    }
                }
            }
        }
        if (port) {
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (ended) share()
                main(Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 38.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(72.dp)) {
                main(Modifier.width(700.dp).fillMaxHeight())
                Box(Modifier.weight(1f)) { if (ended) share() }
            }
        }
    }
}
