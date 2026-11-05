package app.carflash.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.carflash.data.Room
import app.carflash.data.Seat
import app.carflash.data.Show
import app.carflash.haechi.HaechiState
import app.carflash.ui.components.AppHead
import app.carflash.ui.components.Banner
import app.carflash.ui.components.BannerKind
import app.carflash.ui.components.BtnKind
import app.carflash.ui.components.CarMap
import app.carflash.ui.components.CfButton
import app.carflash.ui.components.Cover
import app.carflash.ui.components.HaechiView
import app.carflash.ui.theme.Cf
import app.carflash.ui.theme.CfType
import app.carflash.ui.theme.Layout
import app.carflash.ui.theme.LocalLayout
import app.carflash.vehicle.Gear

/** D1 플래시몹 초대 시작. 두 갈래만: 방 만들기 / 코드로 참가. 초대 관련 색은 청록(보조색). */
@Composable
fun GroupStartScreen(gear: Gear, onBack: () -> Unit, onHost: () -> Unit, onJoin: () -> Unit) {
    val port = LocalLayout.current == Layout.Portrait
    Column(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_group")) {
        AppHead("플래시몹 초대", gear, onBack = onBack)
        val host: @Composable (Modifier) -> Unit = { m ->
            Box(m.clip(RoundedCornerShape(Cf.rLg)).background(Brush.linearGradient(listOf(Color(0x473FD5FF), Cf.surface)))
                .clickable(role = Role.Button, onClick = onHost).testTag("group_host").padding(40.dp)) {
                HaechiView(HaechiState.HELLO, if (port) 200.dp else 300.dp, Modifier.align(Alignment.TopEnd))
                Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Rounded.Groups, null, tint = Cf.accent2, modifier = Modifier.size(64.dp))
                    Text("방 만들기", style = CfType.headline, color = Cf.text)
                    Text("내가 방장. 플래시몹을 고르고 친구 차를 기다려요", style = CfType.body, color = Cf.text2)
                }
            }
        }
        val join: @Composable (Modifier) -> Unit = { m ->
            Box(m.clip(RoundedCornerShape(Cf.rLg)).background(Cf.surface).clickable(role = Role.Button, onClick = onJoin)
                .testTag("group_join").padding(40.dp)) {
                Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Rounded.Pin, null, tint = Cf.text, modifier = Modifier.size(64.dp))
                    Text("코드로 참가", style = CfType.headline, color = Cf.text)
                    Text("방장이 알려 준 6자리 숫자", style = CfType.body, color = Cf.text2)
                }
            }
        }
        val legend: @Composable () -> Unit = {
            Text("모두 P단이어야 시작해요 · 시작 시각은 서버 시계로 맞춰요 · 근처 방 찾기(위치)는 다음 버전", style = CfType.caption, color = Cf.text3)
        }
        if (port) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                host(Modifier.fillMaxWidth().height(440.dp)); join(Modifier.fillMaxWidth().height(320.dp)); legend()
            }
        } else {
            Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { host(Modifier.weight(1f).height(560.dp)); join(Modifier.weight(1f).height(560.dp)) }
                legend()
            }
        }
    }
}

private fun seatColor(show: Show?, pos: Int): Int = show?.palette?.let { if (it.isEmpty()) null else it[pos % it.size] } ?: 0x3FD5FF

@Composable
private fun SeatSlot(seat: Seat, show: Show?, w: Dp, modifier: Modifier = Modifier) {
    val col = seatColor(show, seat.pos)
    Column(
        modifier.clip(RoundedCornerShape(Cf.rLg)).background(if (seat.me) Cf.accent2Soft else Cf.surface)
            .then(if (seat.me) Modifier.border(4.dp, Cf.accent2, RoundedCornerShape(Cf.rLg)) else Modifier)
            .testTag("seat_${seat.pos}").semantics { contentDescription = "seat:${seat.pos}:${seat.nick ?: "empty"}:${if (seat.ready) "ready" else "wait"}${if (seat.me) ":me" else ""}" }
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(Seat.NAMES[seat.pos] + if (seat.me) " · 나" else "", style = CfType.caption, color = Cf.text2)
        if (seat.empty) {
            Box(Modifier.size(w, w * 520f / 300f), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = Cf.textOff, modifier = Modifier.size(64.dp)) }
        } else {
            CarMap(listOf(col, col, null, null, null), List(4) { 0 }, null, w)
        }
        Text(seat.nick ?: "빈 자리", style = CfType.label, color = if (seat.empty) Cf.textOff else Cf.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.clip(CircleShape).background(if (seat.ready) Cf.okSoft else Cf.surface2).padding(horizontal = 18.dp, vertical = 6.dp)) {
            Text(if (seat.empty) "기다리는 중" else if (seat.ready) "준비됨" else "고르는 중", style = CfType.caption, color = if (seat.ready) Cf.ok else Cf.text2)
        }
    }
}

/** D2 방장 대기실(host) / 참가자 대기(joiner). 방 코드는 크게, 3+3 띄어 쓴다. */
@Composable
fun GroupLobbyScreen(
    gear: Gear, room: Room, show: Show?, durationLabel: String,
    onBack: () -> Unit, onChangeShow: () -> Unit, onStart: () -> Unit,
) {
    val port = LocalLayout.current == Layout.Portrait
    val empty = room.seats.count { it.empty }
    val canStart = room.hostIsMe && room.readyCount >= 2 && gear == Gear.P
    Column(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_lobby").semantics { contentDescription = "lobby:${if (room.hostIsMe) "host" else "guest"}" }) {
        AppHead(if (room.hostIsMe) "플래시몹 초대 · 방장" else "플래시몹 참가 · 대기", gear, onBack = onBack)
        val left: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Cf.rLg)).background(Cf.surface).padding(28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (room.hostIsMe) "방 코드 · 친구에게 불러 주세요" else "방 코드", style = CfType.caption, color = Cf.text2)
                    Text("${room.code.take(3)} ${room.code.drop(3)}", style = CfType.display, color = Cf.accent2,
                        modifier = Modifier.testTag("room_code").semantics { contentDescription = "room:${room.code}" })
                }
                if (show != null) {
                    Row(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(Cf.rMd)).background(Cf.surface)
                        .then(if (room.hostIsMe) Modifier.clickable(role = Role.Button, onClick = onChangeShow) else Modifier).testTag("lobby_show")
                        .semantics { contentDescription = "lobby_show:${show.id}" },
                        verticalAlignment = Alignment.CenterVertically) {
                        Cover(show.cover, show.gradient, Modifier.size(120.dp))
                        Column(Modifier.padding(horizontal = 24.dp)) {
                            Text(show.title, style = CfType.body, color = Cf.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(durationLabel + if (room.hostIsMe) " · 눌러서 바꾸기" else "", style = CfType.caption, color = Cf.text2)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { HaechiView(HaechiState.WAIT, if (port) 200.dp else 260.dp, Modifier.align(Alignment.Center)) }
                Text("${room.readyCount}대 준비됨" + if (empty > 0) " · ${empty}자리 비어 있음" else "", style = CfType.label, color = Cf.text2,
                    modifier = Modifier.testTag("lobby_count"))
                if (room.hostIsMe) {
                    if (gear != Gear.P) Banner("정차(P)에서만 시작할 수 있어요", null, BannerKind.Stop)
                    CfButton("모두 함께 시작", Modifier.fillMaxWidth().testTag("group_start"), BtnKind.Sync, large = true, enabled = canStart, icon = Icons.Rounded.Sync, onClick = onStart)
                } else {
                    Banner("방장이 시작하면 모든 차가 함께 시작해요", "기어는 P에 두세요", BannerKind.Info, Modifier.testTag("guest_wait"))
                }
            }
        }
        val seats: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    room.seats.forEach { SeatSlot(it, show, if (port) 120.dp else 150.dp, Modifier.weight(1f)) }
                }
                Text("색이 왼쪽 → 가운데 → 오른쪽으로 물결처럼 넘어가요", style = CfType.caption, color = Cf.text2)
            }
        }
        if (port) {
            Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                seats(Modifier.fillMaxWidth()); left(Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                left(Modifier.width(640.dp).fillMaxHeight())
                seats(Modifier.weight(1f).padding(top = 24.dp))
            }
        }
    }
}

/** D3 참가자 자리 고르기. 찬 자리는 꺼짐. 차 이름은 별명만. */
@Composable
fun GroupSeatScreen(gear: Gear, room: Room, show: Show?, onBack: () -> Unit, onReady: (pos: Int, nick: String) -> Unit) {
    val free = room.seats.filter { it.empty }.map { it.pos }
    var pos by remember { mutableStateOf(free.firstOrNull()) }
    val nicks = listOf("하얀 차", "우리 차", "3번 차")
    var nick by remember { mutableStateOf(nicks.first()) }
    Column(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_group_seat")) {
        AppHead("플래시몹 참가", gear, onBack = onBack)
        Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Banner("방 ${room.code.take(3)} ${room.code.drop(3)}에 들어왔어요", "플래시몹: ${show?.title ?: "?"} · 방장 차 포함 ${room.carCount}대", BannerKind.Ok, Modifier.testTag("joined"))
            Text("내 차 자리", style = CfType.title, color = Cf.text)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                room.seats.forEach { s ->
                    val taken = !s.empty && !s.me
                    val sel = pos == s.pos
                    Column(
                        Modifier.weight(1f).height(140.dp).alpha(if (taken) 0.38f else 1f).clip(RoundedCornerShape(Cf.rMd)).background(Cf.surface2)
                            .then(if (sel) Modifier.border(4.dp, Cf.accent2, RoundedCornerShape(Cf.rMd)) else Modifier)
                            .testTag("pick_${s.pos}").semantics { contentDescription = "pick:${s.pos}:${if (taken) "taken" else if (sel) "on" else "off"}" }
                            .clickable(enabled = !taken, role = Role.RadioButton) { pos = s.pos },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(Seat.NAMES[s.pos], style = CfType.title, color = Cf.text)
                        if (taken) Text("찼음 · ${s.nick}", style = CfType.caption, color = Cf.text2)
                    }
                }
            }
            Text("차 별명", style = CfType.title, color = Cf.text)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                nicks.forEachIndexed { i, n ->
                    Box(Modifier.height(Cf.touchMin).clip(CircleShape).background(if (nick == n) Cf.accent2Soft else Cf.surface2)
                        .then(if (nick == n) Modifier.border(3.dp, Cf.accent2, CircleShape) else Modifier)
                        .clickable(role = Role.RadioButton) { nick = n }.padding(horizontal = 28.dp).testTag("nick_$i"),
                        contentAlignment = Alignment.Center) { Text(n, style = CfType.label, color = Cf.text) }
                }
            }
            Spacer(Modifier.weight(1f))
            CfButton("준비됐어요", Modifier.fillMaxWidth().testTag("group_ready"), BtnKind.Sync, large = true, enabled = pos != null && gear == Gear.P) {
                pos?.let { onReady(it, nick) }
            }
        }
    }
}
