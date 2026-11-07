package app.carflash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WifiOff
import app.carflash.ui.components.AppHead
import app.carflash.ui.screens.HomeActions
import app.carflash.ui.screens.CodeScreen
import app.carflash.ui.screens.Draft
import app.carflash.ui.screens.EditorScreen
import app.carflash.ui.screens.SavedScreen
import app.carflash.ui.screens.SongScreen
import app.carflash.ui.screens.GroupLobbyScreen
import app.carflash.ui.screens.GroupSeatScreen
import app.carflash.ui.screens.GroupStartScreen
import app.carflash.ui.screens.HomeScreen
import app.carflash.ui.screens.OnboardingScreen
import app.carflash.ui.screens.CountdownScreen
import app.carflash.ui.screens.PlayingScreen
import app.carflash.ui.screens.PrepScreen
import app.carflash.ui.screens.ResultScreen
import app.carflash.show.EngineState
import app.carflash.show.Designer
import app.carflash.show.blankPlans
import app.carflash.show.expandPerBar
import app.carflash.data.Effect
import app.carflash.data.Show
import app.carflash.show.StopReason
import app.carflash.ui.theme.Cf
import app.carflash.ui.theme.CarflashTheme
import app.carflash.ui.theme.CfType
import app.carflash.ui.theme.Layout
import app.carflash.vehicle.VehicleGateway.Companion.log
import java.util.Calendar

/** 화면 경로. 와이어프레임 v0.3 번호를 그대로 쓴다. */
sealed interface Route {
    data object Onboard : Route
    data object Home : Route
    data class Prep(val showId: String) : Route
    data object Code : Route
    data object Songs : Route
    data object Editor : Route
    data class Saved(val showId: String) : Route
    data object Group : Route
    data object GroupCode : Route
    data object GroupSeat : Route
    data object Lobby : Route
    data class Todo(val title: String) : Route
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = app
        readDebugExtras(intent)
        val first = !app.prefs.getBoolean("onboarded", false)
        setContent {
            val dw = app.debugWidthDp.intValue
            BoxWithConstraints(Modifier.fillMaxSize().background(Cf.bg)) {
            BoxWithConstraints((if (dw > 0) Modifier.fillMaxHeight().width(dw.dp) else Modifier.fillMaxSize())
                .background(Cf.bg).semantics { testTagsAsResourceId = true }) {
                // 앱 영역 1682 = landscape, 분할 840 = portrait
                val layout = if (maxWidth < 1200.dp) Layout.Portrait else Layout.Landscape
                CarflashTheme(layout) {
                    var route by remember { mutableStateOf<Route>(if (first) Route.Onboard else Route.Home) }
                    val go: (Route) -> Unit = { r -> log("NAV $r layout=$layout"); route = r }
                    val gear by app.vehicle.gear.collectAsState()
                    val caps by app.vehicle.caps.collectAsState()
                    val es by app.engine.state.collectAsState()
                    val battery by app.vehicle.battery.collectAsState()
                    val online by app.online.collectAsState()
                    val room by app.groups.room.collectAsState()
                    // 함께 플래시몹이 끝나거나 멈춘 뒤 결과 화면을 닫으면 방에서 나온다
                    val closeResult = { app.engine.reset(); app.groups.leave(); go(Route.Home) }
                    var sound by remember { mutableStateOf(true) }
                    // 만들기 초안: 미리보기(재생 화면)를 다녀와도 남도록 경로 밖에 둔다
                    var draft by remember { mutableStateOf<Draft?>(null) }
                    val engine = app.engine
                    BackHandler(enabled = es is EngineState.Countdown || es is EngineState.Playing) { engine.stop(StopReason.USER) }
                    BackHandler(enabled = es is EngineState.Ended || es is EngineState.Stopped) { closeResult() }
                    BackHandler(enabled = es is EngineState.Idle && route !is Route.Home && route !is Route.Onboard) {
                        when (route) {
                            Route.Editor -> go(Route.Songs)
                            Route.Lobby, Route.GroupSeat -> { app.groups.leave(); go(Route.Group) }
                            Route.GroupCode -> go(Route.Group)
                            else -> go(Route.Home)
                        }
                    }
                    val durLabel: (Show) -> String = { app.shows.durationLabel(it) }
                    when (val e = es) {
                        is EngineState.Countdown -> { CountdownScreen(e.show, e.secondsLeft, e.enabled, gear, e.group) { engine.stop(StopReason.USER) }; return@CarflashTheme }
                        is EngineState.Playing -> { PlayingScreen(e.show, e.song, e.posMs, e.frame, e.enabled, gear, e.group) { engine.stop(StopReason.USER) }; return@CarflashTheme }
                        is EngineState.Ended -> {
                            ResultScreen(e.show, e.song, e.enabled, gear, durLabel(e.show), null, restored = e.restored,
                                onBack = closeResult,
                                onPrimary = { engine.reset(); app.groups.leave(); engine.start(e.show, e.enabled, sound) },
                                onSecondary = closeResult)
                            return@CarflashTheme
                        }
                        is EngineState.Stopped -> {
                            ResultScreen(e.show, e.song, e.enabled, gear, durLabel(e.show), e.reason, e.posMs, restored = e.restored,
                                onBack = closeResult,
                                onPrimary = { engine.reset(); app.groups.leave(); engine.start(e.show, e.enabled, sound) },
                                onSecondary = closeResult)
                            return@CarflashTheme
                        }
                        EngineState.Idle -> {}
                    }
                    when (val r = route) {
                        Route.Onboard -> OnboardingScreen(gear, caps) {
                            app.prefs.edit().putBoolean("onboarded", true).apply()
                            go(Route.Home)
                        }
                        Route.Home -> {
                            val today = app.shows.today(Calendar.getInstance().get(Calendar.DAY_OF_YEAR))
                            HomeScreen(
                                gear = gear, repo = app.shows, today = today, mine = app.shows.mine(), recent = app.shows.recent().filter { it.origin != Show.Origin.MINE }, online = online,
                                banner = if (online) null else ({ OfflineBanner() }),
                                a = HomeActions(
                                    openShow = { go(Route.Prep(it.id)) },
                                    code = { go(Route.Code) },
                                    group = { go(Route.Group) },
                                    create = { go(Route.Songs) },
                                    settings = { go(Route.Onboard) },
                                ),
                            )
                        }
                        is Route.Prep -> {
                            val show = app.shows.byId(r.showId)
                            val song = show?.let { app.shows.song(it.songId) }
                            if (show == null || song == null) Todo("플래시몹", gear) { go(Route.Home) }
                            else PrepScreen(show, song, app.shows.plansFor(show), caps, gear, durLabel(show), battery = battery,
                                onBack = { go(Route.Home) },
                                onStart = { fx, snd ->
                                    sound = snd
                                    engine.start(show, fx, snd,
                                        startAtMs = app.debugCountdownMs.takeIf { it > 0 }?.let { android.os.SystemClock.elapsedRealtime() + it },
                                        offsetMs = app.debugOffsetMs)
                                })
                        }
                        Route.Code -> CodeScreen(gear, app.shows.recentCodes(),
                            lookup = { c ->
                                app.shows.server.lookup(c)?.let { s ->
                                    app.shows.pushCode(c)
                                    "「${s.title}」를 찾았어요" to "${durLabel(s)} · ${Effect.entries.filter { it in s.effects }.joinToString(" · ") { it.label }}"
                                }
                            },
                            onBack = { go(Route.Home) },
                            onFound = { c ->
                                val s = app.shows.server.lookup(c)
                                if (s != null) { if (s.origin == Show.Origin.FRIEND) app.shows.addRecent(s); go(Route.Prep(s.id)) }
                            })
                        Route.Songs -> SongScreen(gear, app.shows.songs, onBack = { go(Route.Home) }) { song, fromPreset ->
                            val (pal, grad) = app.shows.paletteFor(song.id)
                            val fx = setOfNotNull(
                                Effect.LIGHT.takeIf { caps?.light != false },
                                Effect.WINDOW.takeIf { caps?.window != false },
                                Effect.WIPER.takeIf { caps?.wiper != false },
                                Effect.FAN.takeIf { caps?.fan != false },
                                Effect.HEADLAMP.takeIf { caps?.headlamp != false })
                            val plans = if (fromPreset) expandPerBar(song, Designer.auto(song, pal, fx))
                                else blankPlans(song)
                            val show = Show("m_${System.currentTimeMillis()}", "내 ${song.title}", song.id, fx, null, grad, pal,
                                Show.Origin.MINE, null, plans, System.currentTimeMillis())
                            log("CREATE song=${song.id} fromPreset=$fromPreset id=${show.id}")
                            draft = Draft(show, plans)
                            go(Route.Editor)
                        }
                        Route.Editor -> {
                            val d = draft
                            val song = d?.let { app.shows.song(it.show.songId) }
                            if (d == null || song == null) Todo("편집", gear) { go(Route.Home) }
                            else EditorScreen(gear, song, caps, d,
                                onBack = { go(Route.Songs) },
                                onPreview = { fromBar, bars ->
                                    val ms = { b: Int -> b * 4 * 60000L / song.bpm }
                                    val s = d.show.copy(plans = d.plans, effects = d.usedEffects())
                                    log("EDIT preview bars=$fromBar+$bars")
                                    engine.start(s, d.usedEffects(), true, startAtMs = android.os.SystemClock.elapsedRealtime() + 1000,
                                        offsetMs = ms(fromBar), previewEndMs = ms(fromBar + bars))
                                },
                                onSave = {
                                    val code = d.show.code ?: app.shows.server.publish(d.show)
                                    val saved = d.show.copy(plans = d.plans, effects = d.usedEffects(), code = code)
                                    app.shows.saveMine(saved)
                                    d.show = saved
                                    log("SAVE id=${saved.id} code=$code fx=${saved.effects}")
                                    go(Route.Saved(saved.id))
                                })
                        }
                        is Route.Saved -> {
                            val s = app.shows.byId(r.showId)
                            if (s == null) Todo("저장", gear) { go(Route.Home) }
                            else SavedScreen(gear, s, durLabel(s), onBack = { go(Route.Home) }, onPlay = { go(Route.Prep(s.id)) }, onEdit = { go(Route.Editor) })
                        }
                        Route.Group -> GroupStartScreen(gear, onBack = { go(Route.Home) },
                            onHost = {
                                val today = app.shows.today(Calendar.getInstance().get(Calendar.DAY_OF_YEAR))
                                app.groups.create(today.id); go(Route.Lobby)
                            },
                            onJoin = { go(Route.GroupCode) })
                        Route.GroupCode -> CodeScreen(gear, emptyList(),
                            lookup = { c ->
                                app.groups.lookup(c)?.let { rm ->
                                    "방 ${c.take(3)} ${c.drop(3)}를 찾았어요" to "플래시몹: ${app.shows.byId(rm.showId)?.title ?: "?"} · ${rm.carCount}대 · 빈 자리 ${rm.seats.count { it.empty }}"
                                }
                            },
                            onBack = { go(Route.Group) },
                            onFound = { c -> if (app.groups.join(c) != null) go(Route.GroupSeat) },
                            title = "플래시몹 참가", prompt = "방장이 알려 준 6자리 숫자", screenTag = "screen_group_code",
                            missingText = "그 코드의 방이 없어요")
                        Route.GroupSeat -> {
                            val rm = room
                            if (rm == null) Todo("플래시몹 참가", gear) { go(Route.Group) }
                            else GroupSeatScreen(gear, rm, app.shows.byId(rm.showId), onBack = { app.groups.leave(); go(Route.Group) }) { pos, nick ->
                                app.groups.pickSeat(pos, nick); app.groups.ready(); go(Route.Lobby)
                            }
                        }
                        Route.Lobby -> {
                            val rm = room
                            if (rm == null) Todo("플래시몹 초대", gear) { go(Route.Group) }
                            else {
                                val show = app.shows.byId(rm.showId)
                                GroupLobbyScreen(gear, rm, show, show?.let { durLabel(it) } ?: "",
                                    onBack = { app.groups.leave(); go(Route.Group) },
                                    onChangeShow = {
                                        // 바꾸기: 추천 → 내 플래시몹 순서로 돌린다
                                        val all = app.shows.presets + app.shows.mine()
                                        val i = all.indexOfFirst { it.id == rm.showId }
                                        app.groups.changeShow(all[(i + 1) % all.size].id)
                                    },
                                    onStart = { app.groups.startAll() })
                            }
                        }
                        is Route.Todo -> Todo(r.title, gear) { go(Route.Home) }
                    }
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readDebugExtras(intent)
    }

    /** 시험용(디버그 빌드만): am start --el cf_countdown_ms 8000 --el cf_offset_ms 31000 --ez cf_offline true */
    private fun readDebugExtras(i: android.content.Intent?) {
        if (!BuildConfig.DEBUG || i == null) return
        val ms = i.getLongExtra("cf_countdown_ms", -1)
        if (ms >= 0) { app.debugCountdownMs = ms; log("DEBUG countdownMs=$ms") }
        val off = i.getLongExtra("cf_offset_ms", -1)
        if (off >= 0) { app.debugOffsetMs = off; log("DEBUG offsetMs=$off") }
        val w = i.getIntExtra("cf_width_dp", -1)
        if (w >= 0) { app.debugWidthDp.intValue = w; log("DEBUG widthDp=$w") }
        if (i.hasExtra("cf_offline")) { app.debugOffline = i.getBooleanExtra("cf_offline", false); log("DEBUG offline=${app.debugOffline}") }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

/** E4 오프라인 배너. */
@Composable
private fun OfflineBanner() {
    app.carflash.ui.components.Banner("인터넷 연결이 없어요", "앱에 든 플래시몹 5개와 내 플래시몹만 틀 수 있어요",
        app.carflash.ui.components.BannerKind.Stop, Modifier.testTag("banner_offline"),
        icon = Icons.Rounded.WifiOff)
}

/** 다음 스프린트에서 채울 화면 자리. */
@Composable
private fun Todo(title: String, gear: app.carflash.vehicle.Gear, back: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Cf.bg).testTag("screen_todo")) {
        AppHead(title, gear, onBack = back)
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("준비 중인 화면이에요", style = CfType.body, color = Cf.text2)
        }
    }
}
