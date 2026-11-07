package app.carflash.data

import android.content.Context
import app.carflash.show.Designer
import app.carflash.show.SectionPlan
import org.json.JSONArray
import org.json.JSONObject

/** 효과 종류. 아이콘·토글·차종 차이 표시에 같이 쓴다. */
enum class Effect(val label: String) { LIGHT("조명"), WINDOW("창문"), WIPER("와이퍼"), FAN("송풍"), HEADLAMP("전조등") }

/** 이 차가 이 효과를 쓸 수 있나. */
fun app.carflash.vehicle.VehicleCaps.has(e: Effect): Boolean = when (e) {
    Effect.LIGHT -> light; Effect.WINDOW -> window; Effect.WIPER -> wiper; Effect.FAN -> fan; Effect.HEADLAMP -> headlamp
}

data class SongSection(val name: String, val bar: Int, val bars: Int)

/** 앱에 든 곡(tools/make_songs.py가 만든 assets/songs/songs.json). */
data class Song(
    val id: String,
    val title: String,
    val bpm: Int,
    val bars: Int,
    val durationSec: Double,
    val musicSec: Double,
    val asset: String,
    val sections: List<SongSection>,
) {
    val beats get() = bars * 4
    fun sectionAtBar(bar: Int): SongSection = sections.lastOrNull { bar >= it.bar } ?: sections.first()
}

/** 플래시몹 한 편. plans가 비어 있으면 곡 구간으로 자동 설계한다(Designer.auto). */
data class Show(
    val id: String,
    val title: String,
    val songId: String,
    val effects: Set<Effect>,
    val cover: String?,          // assets 경로(covers/<id>.png). 없으면 그라데이션
    val gradient: Pair<Long, Long>,
    val palette: List<Int>,
    val origin: Origin = Origin.PRESET,
    val code: String? = null,
    val plans: List<SectionPlan> = emptyList(),
    val createdAt: Long = 0,
) {
    enum class Origin { PRESET, MINE, FRIEND }
}

/**
 * 플래시몹 저장소. 지금은 서버 없이 앱 안 목업이다(창업자 결정 2026-11-05: "로컬 목업 먼저").
 * 내 플래시몹과 최근 기록은 SharedPreferences(JSON)에 둔다. 코드 → 플래시몹은 MockShowServer가 맡는다.
 */
class ShowRepository(private val context: Context) {
    val songs: List<Song> by lazy { loadSongs() }
    private val prefs get() = context.getSharedPreferences("carflash_shows", Context.MODE_PRIVATE)

    private fun loadSongs(): List<Song> = try {
        val txt = context.assets.open("songs/songs.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(txt).getJSONArray("songs")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val secs = o.getJSONArray("sections")
            Song(
                o.getString("id"), o.getString("title"), o.getInt("bpm"), o.getInt("bars"),
                o.getDouble("durationSec"), o.getDouble("musicSec"), o.getString("asset"),
                (0 until secs.length()).map { j -> secs.getJSONObject(j).let { s -> SongSection(s.getString("name"), s.getInt("bar"), s.getInt("bars")) } },
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun song(id: String) = songs.firstOrNull { it.id == id }

    val presets: List<Show> = listOf(
        Show("p_camp", "캠핑장의 밤", "camp_party", setOf(Effect.LIGHT, Effect.WINDOW, Effect.FAN, Effect.HEADLAMP), "covers/p_camp.jpg",
            0xFFFF8A00 to 0xFFA05CFF, listOf(0xFF8A00, 0xFFD60A, 0xFF2D55, 0xA05CFF), code = "206518"),
        Show("p_romance", "로맨틱 드라이브", "romantic_drive", setOf(Effect.LIGHT, Effect.WINDOW), "covers/p_romance.jpg",
            0xFFFF4FD8 to 0xFF2F6BFF, listOf(0xFF4FD8, 0xFF2D55, 0xA05CFF, 0x2F6BFF), code = "731204"),
        Show("p_night", "감미로운 밤", "sweet_night", setOf(Effect.LIGHT, Effect.FAN), "covers/p_night.jpg",
            0xFF2F6BFF to 0xFFA05CFF, listOf(0x2F6BFF, 0xA05CFF, 0x00E5FF, 0xFFFFFF), code = "482915"),
        Show("p_birthday", "생일 축하 플래시몹", "birthday_party", setOf(Effect.LIGHT, Effect.WIPER, Effect.FAN, Effect.HEADLAMP), "covers/p_birthday.jpg",
            0xFFFF2D55 to 0xFFFFD60A, listOf(0xFF2D55, 0xFFD60A, 0x30D158, 0x00E5FF, 0xFF4FD8), code = "120120"),
        Show("p_rain", "비 오는 주차장", "rainy_parking", setOf(Effect.LIGHT, Effect.WIPER), "covers/p_rain.jpg",
            0xFF00E5FF to 0xFF2F6BFF, listOf(0x00E5FF, 0x2F6BFF, 0x30D158, 0xFFFFFF), code = "880088"),
    )

    /** 오늘의 플래시몹: 날짜로 돌린다(자동 넘김 없음, 하루 고정). */
    fun today(dayOfYear: Int): Show = presets[dayOfYear % presets.size]

    fun durationSec(show: Show): Double = song(show.songId)?.durationSec ?: 0.0

    fun durationLabel(show: Show): String = mmss(durationSec(show)).let { (m, s) -> "${m}분 ${"%02d".format(s)}초" }

    private fun mmss(d: Double): Pair<Int, Int> = d.toInt().let { it / 60 to it % 60 }

    fun plansFor(show: Show): List<SectionPlan> {
        if (show.plans.isNotEmpty()) return show.plans
        val song = song(show.songId) ?: return emptyList()
        return Designer.auto(song, show.palette, show.effects)
    }

    // ---------- 내 플래시몹·최근 ----------
    fun mine(): List<Show> = readList("mine")
    fun recent(): List<Show> = readList("recent")

    /** 코드 서버 목업. */
    val server by lazy { MockShowServer(this) }

    fun byId(id: String): Show? =
        presets.firstOrNull { it.id == id } ?: mine().firstOrNull { it.id == id } ?: recent().firstOrNull { it.id == id }
            ?: server.friends.firstOrNull { it.id == id }

    fun saveMine(show: Show) {
        val list = mine().filter { it.id != show.id } + show
        writeList("mine", list)
    }

    fun addRecent(show: Show) {
        if (show.origin == Show.Origin.PRESET) return
        val list = (listOf(show) + recent().filter { it.id != show.id }).take(6)
        writeList("recent", list)
    }

    /** B1 최근 코드 칩: 코드로 찾은 순서(최근 것 먼저) 3개. */
    fun recentCodes(): List<String> = (prefs.getString("codes", "") ?: "").split(",").filter { it.length == 6 }.take(3)

    fun pushCode(code: String) {
        val list = (listOf(code) + recentCodes().filter { it != code }).take(3)
        prefs.edit().putString("codes", list.joinToString(",")).apply()
    }

    /** 새 플래시몹의 기본 색: 같은 곡을 쓰는 추천 플래시몹 색. */
    fun paletteFor(songId: String): Pair<List<Int>, Pair<Long, Long>> =
        presets.firstOrNull { it.songId == songId }?.let { it.palette to it.gradient }
            ?: (listOf(0xFF4D8D, 0x3FD5FF, 0xA05CFF, 0xFFD60A) to (0xFFFF4D8D to 0xFF3FD5FF))

    private fun readList(key: String): List<Show> = try {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).mapNotNull { Codec.decode(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeList(key: String, list: List<Show>) {
        val arr = JSONArray()
        list.forEach { arr.put(Codec.encode(it)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
