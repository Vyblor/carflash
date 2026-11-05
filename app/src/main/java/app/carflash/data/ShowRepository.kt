package app.carflash.data

import android.content.Context
import org.json.JSONObject

/** 효과 종류. 아이콘·토글·차종 차이 표시에 같이 쓴다. */
enum class Effect(val label: String) { LIGHT("조명"), WINDOW("창문"), WIPER("와이퍼"), FAN("송풍") }

/** 앱에 든 곡(tools/make_songs.py가 만든 assets/songs/songs.json). */
data class Song(
    val id: String,
    val title: String,
    val bpm: Int,
    val bars: Int,
    val durationSec: Double,
    val musicSec: Double,
    val asset: String,
)

/** 플래시몹 한 편. 큐(cue)는 S2에서 엔진이 곡 박자로 만든다. */
data class Show(
    val id: String,
    val title: String,
    val songId: String,
    val effects: Set<Effect>,
    val cover: String?,          // assets 경로(covers/<id>.png). 없으면 그라데이션
    val gradient: Pair<Long, Long>,
    val origin: Origin = Origin.PRESET,
    val code: String? = null,
) {
    enum class Origin { PRESET, MINE, FRIEND }
}

/**
 * 플래시몹 저장소. 지금은 서버 없이 앱 안 목업이다(창업자 결정 2026-11-05: "로컬 목업 먼저").
 */
class ShowRepository(private val context: Context) {
    val songs: List<Song> by lazy { loadSongs() }

    private fun loadSongs(): List<Song> = try {
        val txt = context.assets.open("songs/songs.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(txt).getJSONArray("songs")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Song(o.getString("id"), o.getString("title"), o.getInt("bpm"), o.getInt("bars"),
                o.getDouble("durationSec"), o.getDouble("musicSec"), o.getString("asset"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun song(id: String) = songs.firstOrNull { it.id == id }

    val presets: List<Show> = listOf(
        Show("p_camp", "캠핑장의 밤", "camp_party", setOf(Effect.LIGHT, Effect.WINDOW, Effect.FAN), "covers/p_camp.png", 0xFFFF8A00 to 0xFFA05CFF),
        Show("p_romance", "로맨틱 드라이브", "romantic_drive", setOf(Effect.LIGHT, Effect.WINDOW), "covers/p_romance.png", 0xFFFF4FD8 to 0xFF2F6BFF),
        Show("p_night", "감미로운 밤", "sweet_night", setOf(Effect.LIGHT, Effect.FAN), "covers/p_night.png", 0xFF2F6BFF to 0xFFA05CFF),
        Show("p_birthday", "생일 축하 플래시몹", "birthday_party", setOf(Effect.LIGHT, Effect.WIPER, Effect.FAN), "covers/p_birthday.png", 0xFFFF2D55 to 0xFFFFD60A),
        Show("p_rain", "비 오는 주차장", "rainy_parking", setOf(Effect.LIGHT, Effect.WIPER), "covers/p_rain.png", 0xFF00E5FF to 0xFF2F6BFF),
    )

    /** 오늘의 플래시몹: 날짜로 돌린다(자동 넘김 없음, 하루 고정). */
    fun today(dayOfYear: Int): Show = presets[dayOfYear % presets.size]

    fun durationLabel(show: Show): String {
        val d = song(show.songId)?.durationSec ?: 0.0
        val s = d.toInt()
        return "${s / 60}분 ${"%02d".format(s % 60)}초"
    }

    fun byId(id: String): Show? = presets.firstOrNull { it.id == id }
}
