package app.carflash.data

import app.carflash.show.Pattern
import app.carflash.show.SectionPlan
import app.carflash.vehicle.WiperMode
import org.json.JSONArray
import org.json.JSONObject

/** Show ⇄ JSON. 내 플래시몹 저장과 목업 서버(코드 공유) 둘 다 이 형식을 쓴다. */
object Codec {
    fun encode(s: Show): JSONObject = JSONObject().apply {
        put("v", 1)
        put("id", s.id); put("title", s.title); put("songId", s.songId)
        put("effects", JSONArray(s.effects.map { it.name }))
        put("cover", s.cover ?: JSONObject.NULL)
        put("g1", s.gradient.first); put("g2", s.gradient.second)
        put("palette", JSONArray(s.palette))
        put("origin", s.origin.name)
        put("code", s.code ?: JSONObject.NULL)
        put("createdAt", s.createdAt)
        put("plans", JSONArray(s.plans.map { p ->
            JSONObject().apply {
                put("name", p.name); put("bar", p.bar); put("bars", p.bars)
                put("colors", JSONArray(p.colors)); put("pattern", p.pattern.name)
                put("window", p.window); put("wiper", p.wiper.name); put("fan", p.fan)
            }
        }))
    }

    fun decode(o: JSONObject): Show? = try {
        fun ints(a: JSONArray) = (0 until a.length()).map { a.getInt(it) }
        val plans = o.optJSONArray("plans")?.let { a ->
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                SectionPlan(p.getString("name"), p.getInt("bar"), p.getInt("bars"), ints(p.getJSONArray("colors")),
                    Pattern.valueOf(p.getString("pattern")), p.getInt("window"), WiperMode.valueOf(p.getString("wiper")), p.getInt("fan"))
            }
        } ?: emptyList()
        val eff = o.getJSONArray("effects").let { a -> (0 until a.length()).map { Effect.valueOf(a.getString(it)) }.toSet() }
        Show(
            id = o.getString("id"), title = o.getString("title"), songId = o.getString("songId"), effects = eff,
            cover = if (o.isNull("cover")) null else o.getString("cover"),
            gradient = o.getLong("g1") to o.getLong("g2"),
            palette = ints(o.getJSONArray("palette")),
            origin = Show.Origin.valueOf(o.getString("origin")),
            code = if (o.isNull("code")) null else o.getString("code"),
            plans = plans, createdAt = o.optLong("createdAt"),
        )
    } catch (e: Exception) {
        null
    }
}
