package app.carflash.ui.screens

import android.media.MediaPlayer
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.carflash.data.Effect
import app.carflash.data.has
import app.carflash.data.Show
import app.carflash.data.Song
import app.carflash.haechi.HaechiState
import app.carflash.show.LampPattern
import app.carflash.show.Pattern
import app.carflash.show.SectionPlan
import app.carflash.show.Timeline
import app.carflash.show.WINDOW_LIMIT
import app.carflash.ui.components.AppHead
import app.carflash.ui.components.Banner
import app.carflash.ui.components.BannerKind
import app.carflash.ui.components.BtnKind
import app.carflash.ui.components.CfButton
import app.carflash.ui.components.CodeDisplay
import app.carflash.ui.components.HaechiView
import app.carflash.ui.components.IconBtn
import app.carflash.ui.components.icon
import app.carflash.ui.theme.Cf
import app.carflash.ui.theme.CfType
import app.carflash.ui.theme.Layout
import app.carflash.ui.theme.LocalLayout
import app.carflash.vehicle.Gear
import app.carflash.vehicle.VehicleCaps
import app.carflash.vehicle.VehicleGateway.Companion.log
import app.carflash.vehicle.WiperMode

/** 만들기 흐름(C1 → C2 → C3)이 들고 다니는 초안. 재생·미리보기 화면을 다녀와도 남도록 화면 밖에 둔다. */
class Draft(show: Show, plans: List<SectionPlan>) {
    var show by mutableStateOf(show)
    var plans by mutableStateOf(plans)
    var page by mutableIntStateOf(0)
    var sel by mutableStateOf<Pair<Effect, Int>?>(Effect.LIGHT to 0)

    /** 채워진 칸이 있는 효과. 저장할 때 플래시몹 효과가 된다. */
    fun usedEffects(): Set<Effect> = buildSet {
        if (plans.any { it.pattern != Pattern.NONE }) add(Effect.LIGHT)
        if (plans.any { it.window > 0 }) add(Effect.WINDOW)
        if (plans.any { it.wiper != WiperMode.OFF }) add(Effect.WIPER)
        if (plans.any { it.fan > 0 }) add(Effect.FAN)
        if (plans.any { it.lamp != LampPattern.NONE }) add(Effect.HEADLAMP)
    }

    fun set(bar: Int, f: (SectionPlan) -> SectionPlan) {
        plans = plans.mapIndexed { i, p -> if (i == bar) f(p) else p }
    }
}

// ---------------------------------------------------------------- C1

/** C1 곡과 시작 방법 고르기. 곡은 카플래시 자체 곡만. 행마다 미리듣기. 기본은 "추천 플래시몹에서 시작". */
@Composable
fun SongScreen(gear: Gear, songs: List<Song>, onBack: () -> Unit, onNext: (Song, Boolean) -> Unit) {
    val port = LocalLayout.current == Layout.Portrait
    val ctx = LocalContext.current
    var picked by remember { mutableStateOf(songs.firstOrNull()) }
    var fromPreset by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    fun stopPreview() { player[0]?.let { runCatching { it.stop() }; it.release() }; player[0] = null; playing = null }
    fun preview(s: Song) {
        val was = playing
        stopPreview()
        if (was == s.id) return
        // 가장 신나는 구간(드롭·후렴)부터 8초
        val hi = s.sections.firstOrNull { it.name.startsWith("drop") || it.name.startsWith("chorus") } ?: s.sections.first()
        val mp = MediaPlayer()
        ctx.assets.openFd(s.asset).use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        mp.prepare(); mp.seekTo((hi.bar * 4 * 60000 / s.bpm)); mp.start()
        player[0] = mp; playing = s.id
        log("PREVIEW song=${s.id} at bar=${hi.bar}")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ if (player[0] === mp) stopPreview() }, 8000)
    }
    DisposableEffect(Unit) { onDispose { stopPreview() } }

    val list: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("1. 곡 고르기 · 카플래시 자체 곡", style = CfType.label, color = Cf.text2, modifier = Modifier.padding(start = 8.dp))
            songs.forEach { s ->
                val sel = picked?.id == s.id
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Cf.touchLg).clip(RoundedCornerShape(Cf.rMd)).background(Cf.surface2)
                        .then(if (sel) Modifier.border(4.dp, Cf.accent, RoundedCornerShape(Cf.rMd)) else Modifier)
                        .testTag("song_${s.id}").semantics { contentDescription = "song:${s.id}:${if (sel) "on" else "off"}" }
                        .clickable(role = Role.RadioButton) { picked = s }.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Cf.text, modifier = Modifier.size(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = CfType.body, color = Cf.text)
                        Text("${s.bpm} BPM · ${mmssSec(s.durationSec)}", style = CfType.caption, color = Cf.text2)
                    }
                    IconBtn(if (playing == s.id) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, "미리듣기", Modifier.testTag("song_play_${s.id}")) { preview(s) }
                }
            }
        }
    }
    val method: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("2. 시작 방법", style = CfType.label, color = Cf.text2)
            listOf(true to ("추천 플래시몹에서 시작" to "박자에 맞춘 조명이 미리 깔려 있어요. 바꾸고 싶은 칸만 고쳐요"),
                false to ("빈 플래시몹에서 시작" to "모든 칸이 비어 있어요")).forEach { (v, t) ->
                val sel = fromPreset == v
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Cf.rLg)).background(Cf.surface)
                        .then(if (sel) Modifier.border(4.dp, Cf.accent, RoundedCornerShape(Cf.rLg)) else Modifier)
                        .testTag(if (v) "start_recommended" else "start_blank").semantics { contentDescription = "start:${if (sel) "on" else "off"}" }
                        .clickable(role = Role.RadioButton) { fromPreset = v }.padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(t.first, style = CfType.title, color = Cf.text)
                    Text(t.second, style = CfType.caption, color = Cf.text2)
                }
            }
            Text("원곡 음원은 넣지 않아요. 권리가 정리된 자체 곡만 써요.", style = CfType.caption, color = Cf.text3)
        }
    }
    val next: @Composable (Modifier) -> Unit = { m ->
        CfButton("다음 · 편집하기", m.testTag("songs_next"), BtnKind.Primary, large = true, enabled = picked != null) {
            stopPreview(); picked?.let { onNext(it, fromPreset) }
        }
    }
    Column(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_songs")) {
        AppHead("새 플래시몹 만들기", gear, onBack = { stopPreview(); onBack() })
        if (port) {
            // 다음 버튼은 늘 보이게 아래에 고정하고 나머지만 스크롤한다
            Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    list(Modifier.fillMaxWidth())
                    method(Modifier.fillMaxWidth())
                }
                next(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Column(Modifier.width(820.dp).fillMaxHeight()) {
                    list(Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    next(Modifier.fillMaxWidth())
                }
                method(Modifier.weight(1f).padding(top = 24.dp))
            }
        }
    }
}

private fun mmssSec(d: Double): String { val s = d.toInt(); return "%d:%02d".format(s / 60, s % 60) }

// ---------------------------------------------------------------- C2

private data class Block(val key: String, val label: String, val swatch: List<Int>, val apply: (SectionPlan) -> SectionPlan)

private fun blocksFor(e: Effect, palette: List<Int>): List<Block> {
    val p = palette.ifEmpty { listOf(0xFF4D8D, 0x3FD5FF) }
    fun light(key: String, label: String, pat: Pattern, cols: List<Int>) = Block("light_$key", label, cols) { it.copy(pattern = pat, colors = cols) }
    return when (e) {
        Effect.LIGHT -> listOf(
            light("rainbow", "무지개 물결", Pattern.RAINBOW, listOf(0xFF2D55, 0xFFD60A, 0x30D158, 0x2F6BFF, 0xA05CFF)),
            light("heart", "심장 박동", Pattern.PULSE, listOf(0xFF4FD8)),
            light("alternate", "두 색 번갈아", Pattern.ALTERNATE, p.take(2)),
            light("strobe", "반짝반짝", Pattern.STROBE, p.take(2)),
            light("palette", "테마 색 돌리기", Pattern.RAINBOW, p),
            light("solid_cyan", "한 색 청록", Pattern.SOLID, listOf(0x00E5FF)),
            light("solid_violet", "한 색 보라", Pattern.SOLID, listOf(0xA05CFF)),
            light("solid_orange", "한 색 주황", Pattern.SOLID, listOf(0xFF8A00)),
            light("off", "끄기", Pattern.OFF, listOf(0x000000)),
            Block("light_none", "비우기", emptyList()) { it.copy(pattern = Pattern.NONE, colors = emptyList()) },
        )
        Effect.WINDOW -> listOf(
            Block("window_0", "닫기", emptyList()) { it.copy(window = 0) },
            Block("window_30", "조금 열기 30%", emptyList()) { it.copy(window = 30) },
            Block("window_60", "크게 열기 ${WINDOW_LIMIT}%", emptyList()) { it.copy(window = WINDOW_LIMIT) },
        )
        Effect.WIPER -> listOf(
            Block("wiper_off", "끄기", emptyList()) { it.copy(wiper = WiperMode.OFF) },
            Block("wiper_low", "천천히", emptyList()) { it.copy(wiper = WiperMode.LOW) },
        )
        Effect.FAN -> listOf(
            Block("fan_0", "그대로", emptyList()) { it.copy(fan = 0) },
            Block("fan_2", "약하게 2단", emptyList()) { it.copy(fan = 2) },
            Block("fan_5", "강하게 5단", emptyList()) { it.copy(fan = 5) },
        )
        Effect.HEADLAMP -> listOf(
            Block("lamp_none", "그대로", emptyList()) { it.copy(lamp = LampPattern.NONE) },
            Block("lamp_tail", "미등 켜기", emptyList()) { it.copy(lamp = LampPattern.TAIL) },
            Block("lamp_low", "전조등 켜기", emptyList()) { it.copy(lamp = LampPattern.LOW) },
            Block("lamp_blink", "전조등 깜빡", emptyList()) { it.copy(lamp = LampPattern.BLINK) },
            Block("lamp_tail_blink", "미등 깜빡", emptyList()) { it.copy(lamp = LampPattern.TAIL_BLINK) },
        )
    }
}

private fun cellDesc(e: Effect, p: SectionPlan): String = when (e) {
    Effect.LIGHT -> "${p.pattern.name}:" + (p.colors.firstOrNull()?.let { "%06X".format(it and 0xFFFFFF) } ?: "-")
    Effect.WINDOW -> "${p.window}"
    Effect.WIPER -> p.wiper.name
    Effect.FAN -> "${p.fan}"
    Effect.HEADLAMP -> p.lamp.name
}

private fun swatchBrush(cols: List<Int>): Brush? = when {
    cols.isEmpty() -> null
    cols.size == 1 -> Brush.linearGradient(listOf(Color(0xFF000000.toInt() or cols[0]), Color(0xFF000000.toInt() or cols[0])))
    else -> Brush.horizontalGradient(cols.map { Color(0xFF000000.toInt() or it) })
}

@Composable
private fun Cell(e: Effect, p: SectionPlan, selected: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val filled = when (e) {
        Effect.LIGHT -> p.pattern != Pattern.NONE
        Effect.WINDOW -> p.window > 0
        Effect.WIPER -> p.wiper != WiperMode.OFF
        Effect.FAN -> p.fan > 0
        Effect.HEADLAMP -> p.lamp != LampPattern.NONE
    }
    val bg: Modifier = when {
        e == Effect.LIGHT && p.pattern == Pattern.OFF -> Modifier.background(Color.Black)
        e == Effect.LIGHT && filled -> swatchBrush(p.colors)?.let { Modifier.background(it) } ?: Modifier.background(Cf.surface3)
        filled -> Modifier.background(Cf.accent2Soft)
        else -> Modifier.background(Cf.surface)
    }
    Box(
        modifier.height(104.dp).clip(RoundedCornerShape(16.dp)).then(bg)
            .then(if (selected) Modifier.border(5.dp, Color.White, RoundedCornerShape(16.dp)) else Modifier)
            // 설명은 탭 노드에 붙어야 한다(clickable 뒤에 두면 자식 노드로 떨어진다)
            .testTag("cell_${e.name}_${p.bar}").semantics { contentDescription = "cell:${e.name}:${p.bar}:${cellDesc(e, p)}" }
            .clickable(role = Role.Button, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        val t = when (e) {
            Effect.LIGHT -> when (p.pattern) { Pattern.NONE -> ""; Pattern.OFF -> "끔"; Pattern.PULSE -> "박동"; Pattern.STROBE -> "반짝"; Pattern.ALTERNATE -> "번갈아"; else -> "" }
            Effect.WINDOW -> if (p.window > 0) "${p.window}%" else ""
            Effect.WIPER -> if (p.wiper != WiperMode.OFF) "천천히" else ""
            Effect.FAN -> if (p.fan > 0) "${p.fan}단" else ""
            Effect.HEADLAMP -> when (p.lamp) { LampPattern.NONE -> ""; LampPattern.TAIL -> "미등"; LampPattern.LOW -> "켜기"; LampPattern.BLINK -> "깜빡"; LampPattern.TAIL_BLINK -> "미등 깜빡" }
        }
        if (t.isNotEmpty()) Text(t, style = CfType.label, color = Cf.text)
        else if (!filled) Text("·", style = CfType.title, color = Cf.textOff)
    }
}

/**
 * C2 편집기. 한 쪽 = 4마디. 칸(효과 × 마디)을 탭해 고르고 아래 블록을 탭하면 채워진다. 끌기·늘이기 없음.
 * 트랙은 이 차가 지원하는 효과만. 미리보기는 지금 쪽 4마디를 실제 차로 튼다(정지 버튼이 함께 뜬다).
 */
@Composable
fun EditorScreen(
    gear: Gear, song: Song, caps: VehicleCaps?, draft: Draft,
    onBack: () -> Unit, onPreview: (fromBar: Int, bars: Int) -> Unit, onSave: () -> Unit,
) {
    val port = LocalLayout.current == Layout.Portrait
    val tracks = Effect.entries.filter { e ->
        caps == null || caps.has(e)
    }
    val pages = (song.bars + 3) / 4
    val page = draft.page.coerceIn(0, pages - 1)
    val from = page * 4
    val bars = (from until minOf(from + 4, song.bars)).toList()
    val sel = draft.sel
    val selBar = sel?.second ?: from
    val used = draft.usedEffects()
    fun range(pg: Int) = "${pg * 4 + 1}~${minOf(pg * 4 + 4, song.bars)}마디"

    val grid: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PageChip("◀ ${if (page > 0) range(page - 1) else "처음"}", page > 0, "page_prev") { draft.page = page - 1; draft.sel = null }
                Box(Modifier.height(Cf.touchMin).clip(CircleShape).background(Cf.accentSoft).padding(horizontal = 28.dp)
                    .testTag("page_now").semantics { contentDescription = "page:$page" }, contentAlignment = Alignment.Center) {
                    Text(range(page), style = CfType.label, color = Cf.text)
                }
                PageChip("${if (page < pages - 1) range(page + 1) else "끝"} ▶", page < pages - 1, "page_next") { draft.page = page + 1; draft.sel = null }
                Spacer(Modifier.weight(1f))
                if (!port) Text("${song.bpm} BPM · ${mmssSec(song.durationSec)}", style = CfType.caption, color = Cf.text2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.width(if (port) 120.dp else 180.dp))
                bars.forEach { b ->
                    Text("${b + 1} · ${song.sectionAtBar(b).name}", style = CfType.caption, color = Cf.text3, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                repeat(4 - bars.size) { Spacer(Modifier.weight(1f)) }
            }
            tracks.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.width(if (port) 120.dp else 180.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(e.icon(), null, tint = Cf.text, modifier = Modifier.size(40.dp))
                        if (!port) Text(e.label, style = CfType.label, color = Cf.text)
                    }
                    bars.forEach { b ->
                        Cell(e, draft.plans[b], sel == e to b, Modifier.weight(1f)) { draft.sel = e to b; log("EDIT select ${e.name} bar=$b") }
                    }
                    repeat(4 - bars.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    val sheet: @Composable (Modifier) -> Unit = { m ->
        if (sel != null && sel.first in tracks) {
            val (e, b) = sel
            Column(m.clip(RoundedCornerShape(topStart = Cf.rLg, topEnd = Cf.rLg)).background(Cf.surface).padding(24.dp).testTag("sheet"),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("${b + 1}마디 · ${e.label} 칸에 넣을 블록 · 넣으면 다음 마디로 넘어가요", style = CfType.label, color = Cf.text2)
                blocksFor(e, draft.show.palette).chunked(if (port) 3 else 5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { bl ->
                            Row(
                                Modifier.weight(1f).height(Cf.touchMd).clip(RoundedCornerShape(Cf.rMd)).background(Cf.surface2)
                                    .clickable(role = Role.Button) {
                                        draft.set(b, bl.apply)
                                        log("EDIT bar=$b track=${e.name} block=${bl.key}")
                                        val nb = b + 1
                                        if (nb < song.bars) { draft.sel = e to nb; if (nb >= from + 4) draft.page = page + 1 }
                                    }.padding(horizontal = 16.dp).testTag("block_${bl.key}"),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                swatchBrush(bl.swatch)?.let { Box(Modifier.size(36.dp).clip(CircleShape).background(it)) }
                                Text(bl.label, style = CfType.label, color = Cf.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat((if (port) 3 else 5) - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    val actions: @Composable (Modifier) -> Unit = { m ->
        CfButton("미리보기", m.testTag("editor_preview"), large = true, enabled = gear == Gear.P && used.isNotEmpty(), icon = Icons.Rounded.PlayArrow) {
            onPreview(from, bars.size)
        }
    }
    val save: @Composable (Modifier) -> Unit = { m ->
        CfButton("저장", m.testTag("editor_save"), BtnKind.Primary, large = true, enabled = used.isNotEmpty(), icon = Icons.Rounded.Save, onClick = onSave)
    }

    Column(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_editor")) {
        AppHead("${draft.show.title} · 편집", gear, onBack = onBack, showGear = !port)
        if (port) {
            Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Banner("넓게 펼치면 차 미리보기와 함께 편집해요", "창 제목 줄을 끌어 넓히세요", BannerKind.Info)
                    grid(Modifier.fillMaxWidth())
                    sheet(Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { actions(Modifier.weight(1f)); save(Modifier.weight(1f)) }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.width(340.dp).fillMaxHeight().padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val f = Timeline.frame(song, draft.plans, selBar * 4, tracks.toSet())
                    LookMap(lookOf(f, tracks.toSet()), tracks.toSet(), 230.dp)
                    Text("${selBar + 1}마디 첫 박의 차 모습", style = CfType.caption, color = Cf.text2)
                    Spacer(Modifier.weight(1f))
                    if (used.isEmpty()) Text("칸을 하나 이상 채우면 저장할 수 있어요", style = CfType.caption, color = Cf.text3)
                    actions(Modifier.fillMaxWidth())
                    save(Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    grid(Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    sheet(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PageChip(text: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.height(Cf.touchMin).clip(CircleShape).background(Cf.surface2).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = CfType.label, color = if (enabled) Cf.text else Cf.textOff) }
}

// ---------------------------------------------------------------- C3

/** C3 저장과 코드 발급. A6과 같은 공유 카드. */
@Composable
fun SavedScreen(gear: Gear, show: Show, durationLabel: String, onBack: () -> Unit, onPlay: () -> Unit, onEdit: () -> Unit) {
    val port = LocalLayout.current == Layout.Portrait
    val fx = Effect.entries.filter { it in show.effects }.joinToString(" · ") { it.label }
    Column(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_saved")) {
        AppHead("저장했어요", gear, onBack = onBack)
        val main: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(show.title, style = CfType.headline, color = Cf.text)
                Text("$durationLabel · $fx", style = CfType.body, color = Cf.text2)
                Text("내 플래시몹에 저장했어요. 코드는 친구 차에서도 열려요", style = CfType.caption, color = Cf.text2)
                Box(Modifier.weight(1f).fillMaxWidth()) { HaechiView(HaechiState.CHEER, if (port) 240.dp else 340.dp, Modifier.align(Alignment.Center)) }
                CfButton("바로 틀어 보기", Modifier.fillMaxWidth().testTag("saved_play"), BtnKind.Primary, large = true, icon = Icons.Rounded.PlayArrow, onClick = onPlay)
                CfButton("계속 편집", Modifier.fillMaxWidth().testTag("saved_edit"), large = true, icon = Icons.Rounded.Edit, onClick = onEdit)
            }
        }
        val share: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Banner("코드를 만들었어요", null, BannerKind.Ok)
                Text("새 플래시몹 코드", style = CfType.title, color = Cf.text)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Cf.rLg)).background(Cf.surface).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("플래시몹 코드", style = CfType.caption, color = Cf.text2)
                    show.code?.let { CodeDisplay(it) }
                    Text("친구가 카플래시 홈 → 코드로 불러오기에 넣으면 같은 플래시몹이 열려요", style = CfType.body, color = Cf.text2)
                }
            }
        }
        if (port) {
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                share(); main(Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 38.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(72.dp)) {
                main(Modifier.width(700.dp).fillMaxHeight())
                Box(Modifier.weight(1f)) { share() }
            }
        }
    }
}
