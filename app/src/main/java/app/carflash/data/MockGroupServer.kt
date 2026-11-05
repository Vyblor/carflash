package app.carflash.data

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.carflash.vehicle.VehicleGateway.Companion.log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/** 자리: 0 왼쪽, 1 가운데, 2 오른쪽. 차 이름은 별명만(실명·번호판 없음). */
data class Seat(val pos: Int, val nick: String? = null, val ready: Boolean = false, val me: Boolean = false) {
    val empty get() = nick == null
    companion object { val NAMES = listOf("왼쪽", "가운데", "오른쪽") }
}

data class Room(
    val code: String,
    val showId: String,
    val hostIsMe: Boolean,
    val seats: List<Seat>,
    /** 서버 시각(ms) 기준 시작 시각. null이면 아직 대기. */
    val startAtServer: Long? = null,
) {
    val readyCount get() = seats.count { it.ready }
    val carCount get() = seats.count { !it.empty }
    val mySeat get() = seats.firstOrNull { it.me }
}

/** 함께 시작에 넘기는 정보(카운트다운·재생 화면, 물결 지연). */
data class GroupInfo(val cars: Int, val mySlot: Int, val seats: List<Seat>, val serverStartLabel: String, val latencyMs: Int)

/**
 * 플래시몹 초대 서버 목업(창업자 결정 2026-11-05: 서버 없이 가짜 참가 차로 흐름 완성).
 * 실제 서비스에서는 서버가 방·자리·시작 시각을 관리하고, 각 차는 서버 시계와의 차이와 지연을 재서 보정한다.
 * 여기서는 같은 기기 안에서 가짜 차가 시간차를 두고 들어오고 준비한다.
 */
class MockGroupServer(private val shows: ShowRepository) {
    private val h = Handler(Looper.getMainLooper())
    private val _room = MutableStateFlow<Room?>(null)
    val room: StateFlow<Room?> = _room

    /** 서버 시계: 기기 시계와 조금 다르다고 둔다(보정 시연용). */
    private val skewMs = 1234L
    /** 이 차의 왕복 지연 절반(측정했다고 둔다). */
    val latencyMs = Random.nextInt(25, 60)
    fun serverNow(): Long = System.currentTimeMillis() + skewMs

    /** 서버 시각 → 이 기기의 elapsedRealtime. 지연만큼 일찍 준비해 같은 순간에 첫 박을 친다. */
    fun localAt(serverMs: Long): Long = SystemClock.elapsedRealtime() + (serverMs - serverNow()) - latencyMs

    /** 시험·시연용 이미 열린 방: 방장 "친구 차"(왼쪽), "이웃 차"(가운데). */
    private val demoCode = "246810"
    private fun demoRoom() = Room(demoCode, "p_camp", false, listOf(
        Seat(0, "친구 차", ready = true), Seat(1, "이웃 차", ready = true), Seat(2)))

    private var onStart: ((Room) -> Unit)? = null
    fun setOnStart(f: (Room) -> Unit) { onStart = f }

    // ---------- 방장 ----------
    fun create(showId: String): Room {
        cancelTimers()
        val code = "%06d".format(Random.nextInt(100000, 1000000))
        val r = Room(code, showId, true, listOf(Seat(0), Seat(1, "내 차", ready = true, me = true), Seat(2)))
        _room.value = r
        log("GROUP create code=$code show=$showId")
        // 가짜 친구 차: 4초 뒤 왼쪽에 들어오고 2초 뒤 준비
        later(4000) { update { it.copy(seats = it.seats.map { s -> if (s.pos == 0) Seat(0, "친구 차") else s }) }; log("GROUP fake join nick=친구 차 pos=0") }
        later(6000) { update { it.copy(seats = it.seats.map { s -> if (s.pos == 0) s.copy(ready = true) else s }) }; log("GROUP ready nick=친구 차") }
        return r
    }

    fun changeShow(showId: String) { update { it.copy(showId = showId) }; log("GROUP show=$showId") }

    /** 모두 함께 시작: 서버가 5초 뒤 시각을 정해 모든 차에 보낸다. */
    fun startAll(): Boolean {
        val r = _room.value ?: return false
        if (!r.hostIsMe || r.readyCount < 2) return false
        broadcastStart(r)
        return true
    }

    // ---------- 참가자 ----------
    fun lookup(code: String): Room? {
        val r = if (code == demoCode) demoRoom() else null
        log("GROUP lookup code=$code → ${r?.code}")
        return r
    }

    fun join(code: String): Room? {
        cancelTimers()
        val r = lookup(code) ?: return null
        _room.value = r
        log("GROUP join code=$code")
        return r
    }

    fun pickSeat(pos: Int, nick: String) {
        update { r -> r.copy(seats = r.seats.map { s -> if (s.pos == pos && s.empty) Seat(pos, nick, me = true) else if (s.me) Seat(s.pos) else s }) }
        log("GROUP seat pos=$pos nick=$nick")
    }

    fun ready() {
        update { r -> r.copy(seats = r.seats.map { s -> if (s.me) s.copy(ready = true) else s }) }
        log("GROUP me ready")
        // 가짜 방장이 6초 뒤 시작을 누른다
        later(6000) { _room.value?.let { log("GROUP host start"); broadcastStart(it) } }
    }

    fun leave() {
        cancelTimers()
        if (_room.value != null) log("GROUP leave")
        _room.value = null
    }

    private fun broadcastStart(r: Room) {
        val at = serverNow() + 5000
        val nr = r.copy(startAtServer = at)
        _room.value = nr
        log("GROUP start server=$at latency=${latencyMs}ms cars=${nr.carCount}")
        onStart?.invoke(nr)
    }

    fun info(r: Room): GroupInfo {
        val at = r.startAtServer ?: serverNow()
        val label = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.KOREA).format(java.util.Date(at))
        return GroupInfo(r.carCount, r.mySeat?.pos ?: 1, r.seats, label, latencyMs)
    }

    // ---------- 도우미 ----------
    private val timers = mutableListOf<Runnable>()
    private fun later(ms: Long, f: () -> Unit) {
        val t = object : Runnable { override fun run() { timers.remove(this); f() } }
        timers += t; h.postDelayed(t, ms)
    }
    private fun cancelTimers() { timers.forEach { h.removeCallbacks(it) }; timers.clear() }
    private fun update(f: (Room) -> Room) { _room.value = _room.value?.let(f) }
}
