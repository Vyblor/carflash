package app.carflash.data

import app.carflash.show.Designer
import app.carflash.vehicle.VehicleGateway.Companion.log
import kotlin.random.Random

/**
 * 코드 서버 목업(창업자 결정 2026-11-05: 서버는 이번에 만들지 않는다).
 * 실제 서비스에서는 저장 → 서버가 6자리 코드를 주고, 다른 차가 코드로 받아 간다.
 * 여기서는 추천 플래시몹 코드, 이 기기에서 만든 내 플래시몹 코드, 그리고 "다른 차에서 만든" 예시 2개를 안다.
 */
class MockShowServer(private val repo: ShowRepository) {
    /** 다른 차(조명 5구역)에서 만든 친구 플래시몹 예시. */
    val friends: List<Show> by lazy {
        listOf(
            friend("f_serenade", "불꽃 세레나데", "romantic_drive", setOf(Effect.LIGHT, Effect.WIPER, Effect.FAN),
                listOf(0xFF2D55, 0xFF8A00, 0xFFD60A, 0xFF4FD8), 0xFFFF2D55 to 0xFFFF8A00, "415926"),
            friend("f_starcamp", "별 헤는 캠프", "sweet_night", setOf(Effect.LIGHT, Effect.WINDOW),
                listOf(0x00E5FF, 0x2F6BFF, 0xA05CFF, 0xFFFFFF), 0xFF00E5FF to 0xFF2F6BFF, "314159"),
        )
    }

    private fun friend(id: String, title: String, songId: String, fx: Set<Effect>, palette: List<Int>, g: Pair<Long, Long>, code: String): Show {
        val song = repo.song(songId)
        val plans = song?.let { Designer.auto(it, palette, fx) } ?: emptyList()
        return Show(id, title, songId, fx, null, g, palette, Show.Origin.FRIEND, code, plans, 0)
    }

    /** 코드 → 플래시몹. 없으면 null. 실제라면 네트워크 왕복이 있다. */
    fun lookup(code: String): Show? {
        val all = repo.presets + repo.mine() + friends
        val hit = all.firstOrNull { it.code == code }
        log("SERVER lookup code=$code → ${hit?.id}")
        return hit
    }

    /** 저장: 새 6자리 코드를 준다(이미 쓰인 코드는 피한다). */
    fun publish(show: Show): String {
        val used = (repo.presets + repo.mine() + friends).mapNotNull { it.code }.toSet()
        var code: String
        do { code = "%06d".format(Random.nextInt(100000, 1000000)) } while (code in used)
        log("SERVER publish id=${show.id} code=$code")
        return code
    }
}
