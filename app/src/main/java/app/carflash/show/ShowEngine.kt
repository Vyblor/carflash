package app.carflash.show

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.SystemClock
import app.carflash.data.Effect
import app.carflash.data.GroupInfo
import app.carflash.data.Show
import app.carflash.data.ShowRepository
import app.carflash.data.Song
import app.carflash.haechi.HaechiHost
import app.carflash.haechi.HaechiState
import app.carflash.vehicle.Gear
import app.carflash.vehicle.VehicleGateway
import app.carflash.vehicle.VehicleGateway.Companion.log
import app.carflash.vehicle.VehicleSnapshot
import app.carflash.vehicle.WiperMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class StopReason(val title: String, val detail: String) {
    USER("플래시몹을 멈췄어요", "언제든 처음부터 다시 틀 수 있어요"),
    GEAR("주행이 감지돼 플래시몹을 멈췄어요", "정차(P) 중에만 플래시몹을 틀어요. P로 바꾸면 처음부터 다시 틀 수 있어요"),
    BATTERY("배터리를 아끼려고 멈췄어요", "배터리가 부족해요. 충전한 뒤에 다시 틀어 주세요"),
    AUDIO("다른 소리가 시작돼서 멈췄어요", "전화나 안내 음성이 끝나면 다시 틀 수 있어요"),
    ERROR("문제가 생겨서 멈췄어요", "잠시 뒤에 다시 틀어 주세요"),
}

/**
 * 배터리 보호(E3). 기준값은 실차 정보가 없어 임시로 정했다(INFERRED, docs/carflash-sprints.md).
 * LOW 미만: 창문·송풍을 끄고 조명 밝기를 70%로 제한해 시작. CRITICAL 미만: 시작하지 않는다.
 */
object BatteryGuard {
    const val LOW = 20
    const val CRITICAL = 10
    const val DIM_MAX = 70
    fun low(soc: Int?) = soc != null && soc < LOW
    fun critical(soc: Int?) = soc != null && soc < CRITICAL
}

sealed interface EngineState {
    data object Idle : EngineState
    data class Countdown(val show: Show, val song: Song, val secondsLeft: Int, val startAtMs: Long, val enabled: Set<Effect>, val group: GroupInfo? = null) : EngineState
    data class Playing(val show: Show, val song: Song, val posMs: Long, val frame: Frame?, val enabled: Set<Effect>, val group: GroupInfo? = null) : EngineState
    /** restored = 되돌린 결과 한 줄(예: "조명 원래 색 · 창문 닫힘 · 송풍 원래 단") */
    data class Ended(val show: Show, val song: Song, val enabled: Set<Effect>, val restored: String) : EngineState
    data class Stopped(val show: Show, val song: Song, val reason: StopReason, val posMs: Long, val enabled: Set<Effect>, val restored: String) : EngineState
}

/**
 * 플래시몹 엔진. 곡 위치(ms) → 박자 → Frame → 바뀐 값만 차에 보낸다.
 * 시작 전 차 상태를 찍어 두고(snapshot) 끝·정지·기어 변경 때 그대로 되돌린다(restore).
 * 시계는 곡 재생 위치 하나다. 해치 춤도 같은 위치를 받는다.
 */
class ShowEngine(
    private val context: Context,
    private val vehicle: VehicleGateway,
    private val repo: ShowRepository,
    private val haechi: HaechiHost,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state

    private var player: MediaPlayer? = null
    private var job: Job? = null
    private var snapshot: VehicleSnapshot? = null
    private var last: Frame? = null
    private var plans: List<SectionPlan> = emptyList()
    private var focus: AudioFocusRequest? = null
    private var dim = false
    private var lastRestored = ""
    private val audio get() = context.getSystemService(AudioManager::class.java)

    /** 지금 차가 플래시몹을 하고 있나(카운트다운 포함). */
    val active get() = _state.value is EngineState.Countdown || _state.value is EngineState.Playing

    init {
        // 안전 약속 2: 기어를 바꾸면 1초 안에 멈춘다(E1). 카운트다운 중에도 같다.
        scope.launch {
            vehicle.gear.collect { g ->
                if (active && g != Gear.P && g != Gear.UNKNOWN) {
                    log("SHOW gear=$g while active → stop t=${SystemClock.elapsedRealtime()}")
                    stop(StopReason.GEAR)
                }
            }
        }
        // 앱이 플래시몹 도중 죽었으면(강제 종료 등) 다음 실행 때 저장해 둔 시작 전 상태로 되돌린다
        scope.launch {
            vehicle.caps.collect { c ->
                if (c != null && !active) recoverPending()
            }
        }
        // 배터리 보호(E3): 아주 부족하면 무조건, 부족하면 창문·송풍을 쓰는 중일 때 멈춘다.
        scope.launch {
            vehicle.battery.collect { soc ->
                val en = when (val s = _state.value) { is EngineState.Countdown -> s.enabled; is EngineState.Playing -> s.enabled; else -> null }
                    ?: return@collect
                if (BatteryGuard.critical(soc) || (BatteryGuard.low(soc) && (Effect.WINDOW in en || Effect.FAN in en))) {
                    log("SHOW battery=$soc while active → stop")
                    stop(StopReason.BATTERY)
                }
            }
        }
    }

    /** 되돌릴 내용을 사람이 읽는 말로. */
    private fun restoreSummary(enabled: Set<Effect>, s: VehicleSnapshot?): String {
        val parts = mutableListOf<String>()
        if (Effect.LIGHT in enabled) parts += "조명 원래 색"
        if (Effect.WINDOW in enabled) parts += if (s == null || s.windows.all { (it ?: 0) == 0 }) "창문 닫힘" else "창문 원래 높이"
        if (Effect.WIPER in enabled) parts += if (s?.wiper == null || s.wiper == WiperMode.OFF) "와이퍼 끔" else "와이퍼 원래대로"
        if (Effect.FAN in enabled) parts += "송풍 원래 단"
        if (Effect.HEADLAMP in enabled) parts += if (s == null || (s.lowBeam ?: "OFF") == "OFF" && (s.tailLamp ?: "OFF") == "OFF") "전조등 끔" else "전조등 원래대로"
        return parts.joinToString(" · ").ifEmpty { "바뀐 것 없음" }
    }

    /**
     * 시작. startAtMs(SystemClock.elapsedRealtime 기준)를 주면 그 시각에 맞춰 첫 박자를 친다(D4 함께 시작).
     */
    /** 편집기 미리보기(C2): 이 위치(ms)에 닿으면 조용히 끝내고 Idle로 돌아간다. */
    private var previewEndMs: Long? = null
    /** 함께 시작(D4). 자리 번호만큼 조명을 박자 늦게 쳐서 왼쪽 → 가운데 → 오른쪽 물결을 만든다. */
    private var group: GroupInfo? = null

    fun start(show: Show, enabled: Set<Effect>, sound: Boolean, startAtMs: Long? = null, offsetMs: Long = 0, previewEndMs: Long? = null,
              group: GroupInfo? = null) {
        if (active) return
        val song = repo.song(show.songId) ?: run { log("SHOW no song ${show.songId}"); return }
        if (vehicle.gear.value != Gear.P && vehicle.gear.value != Gear.UNKNOWN) {
            log("SHOW refuse start gear=${vehicle.gear.value}")
            _state.value = EngineState.Stopped(show, song, StopReason.GEAR, 0, enabled, "바뀐 것 없음")
            return
        }
        if (BatteryGuard.critical(vehicle.battery.value)) {
            log("SHOW refuse start battery=${vehicle.battery.value}")
            _state.value = EngineState.Stopped(show, song, StopReason.BATTERY, 0, enabled, "바뀐 것 없음")
            return
        }
        plans = repo.plansFor(show)
        last = null
        this.previewEndMs = previewEndMs
        this.group = group
        dim = BatteryGuard.low(vehicle.battery.value)
        lastRestored = restoreSummary(enabled, null)
        val startAt = startAtMs ?: (SystemClock.elapsedRealtime() + 3000)
        log("SHOW start id=${show.id} song=${song.id} bpm=${song.bpm} fx=$enabled sound=$sound startAt=$startAt offset=$offsetMs dim=$dim battery=${vehicle.battery.value} group=${group?.cars} slot=${group?.mySlot} wave=${group?.mySlot ?: 0}")
        ShowService.start(context, show.title)
        if (previewEndMs == null) repo.addRecent(show)
        _state.value = EngineState.Countdown(show, song, ((startAt - SystemClock.elapsedRealtime() + 999) / 1000).toInt().coerceAtLeast(1), startAt, enabled, group)
        job?.cancel()
        job = scope.launch {
            // 전조등을 쓰지 않는 플래시몹은 전조등 스위치를 되돌리지도 않는다(운전자가 바꾼 값을 덮지 않게)
            snapshot = vehicle.snapshot().let { if (Effect.HEADLAMP in enabled) it else it.copy(lowBeam = null, tailLamp = null) }
            snapshot?.let { savePending(it) }
            lastRestored = restoreSummary(enabled, snapshot)
            prepare(song, sound)
            haechi.setState(HaechiState.WAIT)
            // 카운트다운
            while (isActive) {
                val left = startAt - SystemClock.elapsedRealtime()
                if (left <= 0) break
                val n = ((left + 999) / 1000).toInt()
                val cur = _state.value
                if (cur !is EngineState.Countdown || cur.secondsLeft != n) {
                    _state.value = EngineState.Countdown(show, song, n, startAt, enabled, group)
                    log("SHOW countdown $n")
                }
                delay(minOf(left, 50))
            }
            if (!isActive) return@launch
            val p = player ?: return@launch
            if (offsetMs > 0) p.seekTo(offsetMs.toInt())
            p.start()
            log("SHOW playing t=${SystemClock.elapsedRealtime()} wall=${System.currentTimeMillis()}")
            haechi.setState(HaechiState.DANCE, song.bpm)
            var uiAt = 0L
            var hcAt = 0L
            while (isActive && player != null) {
                val pos = runCatching { p.currentPosition.toLong() }.getOrDefault(0L)
                val beat = (pos / 1000.0 * song.bpm / 60.0).toInt()
                val shift = group?.mySlot ?: 0
                val f = if (pos / 1000.0 < song.musicSec) Timeline.frame(song, plans, beat, enabled).let { fr ->
                    if (shift > 0) Timeline.frame(song, plans, (beat - shift).coerceAtLeast(0), enabled).let { d -> fr.copy(zones = d.zones, lamp = d.lamp) } else fr
                } else null
                if (f != null && f.beat != last?.beat) {
                    apply(f, last)
                    last = f
                    if (beat % 4 == 0) log("SHOW bar=${beat / 4} sec=${f.section} zone0=#%06X bright=${f.brightness} win=${f.windows} wiper=${f.wiper} fan=${f.fan} lamp=${f.lamp}".format(f.zones[0] and 0xFFFFFF))
                }
                val now = SystemClock.elapsedRealtime()
                if (now - uiAt >= 100) {
                    uiAt = now
                    _state.value = EngineState.Playing(show, song, pos, last, enabled, group)
                }
                if (now - hcAt >= 1000) { hcAt = now; haechi.setPosition(pos / 1000.0, true) }
                val pe = previewEndMs
                if (pe != null && pos >= pe) { endPreview(); break }
                delay(20)
            }
        }
    }

    private fun prepare(song: Song, sound: Boolean) {
        release()
        val mp = MediaPlayer()
        context.assets.openFd(song.asset).use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        mp.prepare()
        if (!sound) mp.setVolume(0f, 0f)
        mp.setOnCompletionListener { finish() }
        mp.setOnErrorListener { _, what, extra -> log("SHOW player error $what/$extra"); stop(StopReason.ERROR); true }
        player = mp
        if (sound) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setOnAudioFocusChangeListener { ch ->
                    log("SHOW focus $ch")
                    if (ch == AudioManager.AUDIOFOCUS_LOSS && active) stop(StopReason.AUDIO)
                }.build()
            focus = req
            audio.requestAudioFocus(req)
        }
    }

    private fun apply(f: Frame, prev: Frame?) {
        val caps = vehicle.caps.value ?: return
        if (f.brightness >= 0) {
            val n = caps.ambientZones.coerceAtMost(5)
            for (i in 0 until n) {
                // 영역이 5개 미만이면 앞 구역 색을 쓴다(에뮬레이터는 1개)
                val c = f.zones[i]
                if (prev == null || prev.zones.getOrNull(i) != c) vehicle.setAmbient(i, c)
                val b = if (dim) f.brightness.coerceAtMost(BatteryGuard.DIM_MAX) else f.brightness
                if (prev == null || prev.brightness != f.brightness) vehicle.setBrightness(i, b)
            }
        }
        if (caps.window && f.windows.any { it >= 0 }) {
            for (i in 0 until caps.windows.coerceAtMost(4)) {
                val pct = f.windows[i]
                if (pct >= 0 && (prev == null || prev.windows.getOrNull(i) != pct)) {
                    vehicle.setWindow(i, (pct / 100.0 * caps.windowMax).roundToInt())
                }
            }
        }
        if (caps.wiper && (prev == null || prev.wiper != f.wiper)) vehicle.setWiper(f.wiper)
        if (caps.fan && f.fan > 0 && (prev == null || prev.fan != f.fan)) vehicle.setFan(f.fan.coerceAtMost(caps.fanMax))
        if (caps.headlamp && f.lamp != null && (prev == null || prev.lamp != f.lamp)) vehicle.setLamp(f.lamp)
    }

    private fun finish() {
        if (previewEndMs != null) { endPreview(); return }
        val cur = _state.value as? EngineState.Playing
        log("SHOW end id=${cur?.show?.id}")
        job?.cancel()
        restore()
        release()
        haechi.setState(HaechiState.CHEER)
        ShowService.stop(context)
        if (cur != null) _state.value = EngineState.Ended(cur.show, cur.song, cur.enabled, lastRestored)
        else _state.value = EngineState.Idle
    }

    private fun endPreview() {
        log("SHOW preview end")
        job?.cancel()
        restore()
        release()
        ShowService.stop(context)
        previewEndMs = null
        haechi.setState(HaechiState.IDLE)
        _state.value = EngineState.Idle
    }

    val isPreview get() = previewEndMs != null

    fun stop(reason: StopReason) {
        val cur = _state.value
        val t0 = SystemClock.elapsedRealtime()
        job?.cancel()
        val pos = runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0L
        release()
        restore()
        haechi.setState(if (reason == StopReason.USER) HaechiState.STOP else HaechiState.REST)
        log("SHOW stop reason=$reason restoredIn=${SystemClock.elapsedRealtime() - t0}ms t=${SystemClock.elapsedRealtime()}")
        ShowService.stop(context)
        val wasPreview = previewEndMs != null
        previewEndMs = null
        if (wasPreview && reason == StopReason.USER) { _state.value = EngineState.Idle; return }
        _state.value = when (cur) {
            is EngineState.Countdown -> EngineState.Stopped(cur.show, cur.song, reason, 0, cur.enabled, lastRestored)
            is EngineState.Playing -> EngineState.Stopped(cur.show, cur.song, reason, pos, cur.enabled, lastRestored)
            else -> cur
        }
    }

    private fun restore() {
        val s = snapshot ?: return
        snapshot = null
        last = null
        vehicle.restore(s)
        clearPending()
    }

    // ---------- 죽어도 되돌리기 ----------
    private val prefs get() = context.getSharedPreferences("carflash_engine", Context.MODE_PRIVATE)

    private fun savePending(s: VehicleSnapshot) {
        fun arr(l: List<Int?>) = org.json.JSONArray(l.map { it ?: org.json.JSONObject.NULL })
        val o = org.json.JSONObject().put("ambient", arr(s.ambient)).put("brightness", arr(s.brightness)).put("windows", arr(s.windows))
            .put("wiper", s.wiper?.name ?: org.json.JSONObject.NULL).put("fan", s.fan ?: org.json.JSONObject.NULL)
            .put("lowBeam", s.lowBeam ?: org.json.JSONObject.NULL).put("tailLamp", s.tailLamp ?: org.json.JSONObject.NULL)
        prefs.edit().putString("pending", o.toString()).commit()
    }

    private fun clearPending() { prefs.edit().remove("pending").commit() }

    private fun recoverPending() {
        val txt = prefs.getString("pending", null) ?: return
        try {
            val o = org.json.JSONObject(txt)
            fun ints(k: String) = o.getJSONArray(k).let { a -> (0 until a.length()).map { if (a.isNull(it)) null else a.getInt(it) } }
            val s = VehicleSnapshot(ints("ambient"), ints("brightness"), ints("windows"),
                if (o.isNull("wiper")) null else WiperMode.valueOf(o.getString("wiper")), if (o.isNull("fan")) null else o.getInt("fan"),
                lowBeam = if (o.isNull("lowBeam")) null else o.optString("lowBeam"), tailLamp = if (o.isNull("tailLamp")) null else o.optString("tailLamp"))
            log("SHOW recover pending restore $s")
            vehicle.restore(s)
        } catch (e: Exception) {
            log("SHOW recover failed $e")
        }
        clearPending()
    }

    private fun release() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    /** 끝·멈춤 화면을 닫을 때. */
    fun reset() {
        if (!active) _state.value = EngineState.Idle
    }
}
