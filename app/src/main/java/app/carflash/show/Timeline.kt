package app.carflash.show

import app.carflash.data.Effect
import app.carflash.data.Song
import app.carflash.vehicle.LampMode
import app.carflash.vehicle.WiperMode

/** 조명 움직임 모양. 편집기(C2)에서 고른다. */
enum class Pattern(val label: String) {
    SOLID("한 색 유지"),
    PULSE("박자에 맞춰 숨쉬기"),
    ALTERNATE("두 색 번갈아"),
    RAINBOW("색 돌리기"),
    /** 반 박자가 아니라 한 박자마다 켜고 끈다. 140 BPM이어도 초당 약 1.2회라 "초당 3회 제한" 안이다. */
    STROBE("반짝반짝"),
    /** 조명을 끈다(밝기 0). */
    OFF("조명 끔"),
    /** 비어 있음: 조명에 손대지 않는다(빈 플래시몹에서 시작한 칸). */
    NONE("비어 있음"),
}

/**
 * 전조등·미등 움직임. 정차(P) 중에만 차가 받아 준다. 깜빡임은 한 박자마다 바뀌어 140 BPM에서도 초당 약 1.2회다.
 * BLINK는 전조등↔미등이라 완전히 꺼지는 순간이 없다.
 */
enum class LampPattern(val label: String) {
    NONE("그대로"),
    TAIL("미등 켜기"),
    LOW("전조등 켜기"),
    BLINK("전조등 깜빡"),
    TAIL_BLINK("미등 깜빡"),
}

/**
 * 곡 구간 하나의 연출. 창문은 0~60(%), 송풍은 0(그대로)~8단.
 * 창문 상한 60%는 안전 약속 3("창문이 움직일 수 있어요")에 맞춘 카플래시 자체 한도다.
 */
data class SectionPlan(
    val name: String,
    val bar: Int,
    val bars: Int,
    val colors: List<Int>,
    val pattern: Pattern,
    val window: Int,
    val wiper: WiperMode,
    val fan: Int,
    val lamp: LampPattern = LampPattern.NONE,
) {
    val endBar get() = bar + bars
}

/** "지금 움직이는 것" 한 줄. 효과마다 하나. 문구는 구간 안에서 바뀌지 않는다(박자마다 바뀌면 접근성 이벤트가 쏟아진다). */
data class Cue(val effect: Effect, val text: String)

/** 한 박자에 차가 가져야 할 모습. 차량 엔진이 이전 박자와 달라진 값만 보낸다. */
data class Frame(
    val beat: Int,
    val section: String,
    val zones: List<Int>,          // 논리 5구역(대시보드, 1열 좌·우, 2열 좌·우) RGB
    val brightness: Int,           // 0~100
    val windows: List<Int>,        // 4개, 0~60 %
    val wiper: WiperMode,
    val fan: Int,                  // 0 = 손대지 않음
    val lamp: LampMode?,           // null = 손대지 않음
    val cues: List<Cue>,           // A5 "지금 움직이는 것"
)

const val WINDOW_LIMIT = 60

object Designer {
    /** 프리셋·새 플래시몹의 기본 설계: 곡 구간 이름으로 정한다. */
    fun auto(song: Song, palette: List<Int>, effects: Set<Effect>): List<SectionPlan> {
        val p = palette.ifEmpty { listOf(0xFF4D8D, 0x3FD5FF) }
        fun c(i: Int) = p[i % p.size]
        return song.sections.mapIndexed { i, s ->
            val n = s.name.lowercase()
            val high = n.startsWith("drop") || n.startsWith("chorus")
            val (pattern, colors) = when {
                n.startsWith("intro") -> Pattern.PULSE to listOf(c(0))
                n.startsWith("verse") -> Pattern.ALTERNATE to listOf(c(0), c(1))
                n.startsWith("build") -> Pattern.STROBE to listOf(c(2), c(0))
                high -> Pattern.RAINBOW to p
                n.startsWith("break") || n.startsWith("bridge") -> Pattern.SOLID to listOf(c(3 + i))
                else -> Pattern.PULSE to listOf(c(i))
            }
            SectionPlan(
                name = s.name, bar = s.bar, bars = s.bars, colors = colors, pattern = pattern,
                window = if (Effect.WINDOW in effects && high) WINDOW_LIMIT else if (Effect.WINDOW in effects && n.startsWith("bridge")) 30 else 0,
                wiper = if (Effect.WIPER in effects && high) WiperMode.LOW else WiperMode.OFF,
                fan = if (Effect.FAN !in effects) 0 else when {
                    high -> 5; n.startsWith("build") -> 4; n.startsWith("verse") -> 2; else -> 1
                },
                lamp = if (Effect.HEADLAMP !in effects) LampPattern.NONE else when {
                    high -> LampPattern.BLINK
                    n.startsWith("build") -> LampPattern.TAIL_BLINK
                    else -> LampPattern.TAIL
                },
            )
        }
    }
}

/** 편집기용: 곡 전체를 마디 하나씩의 계획으로 편다(C2는 마디 단위로 고친다). */
fun expandPerBar(song: Song, plans: List<SectionPlan>): List<SectionPlan> = (0 until song.bars).map { b ->
    val p = Timeline.planAt(plans, b)
    val name = song.sectionAtBar(b).name
    p?.copy(name = name, bar = b, bars = 1) ?: SectionPlan(name, b, 1, emptyList(), Pattern.NONE, 0, WiperMode.OFF, 0)
}

/** 빈 플래시몹: 모든 칸 비어 있음. */
fun blankPlans(song: Song): List<SectionPlan> = (0 until song.bars).map { b ->
    SectionPlan(song.sectionAtBar(b).name, b, 1, emptyList(), Pattern.NONE, 0, WiperMode.OFF, 0)
}

object Timeline {
    fun planAt(plans: List<SectionPlan>, bar: Int): SectionPlan? =
        plans.lastOrNull { bar >= it.bar } ?: plans.firstOrNull()

    fun frame(song: Song, plans: List<SectionPlan>, beat: Int, enabled: Set<Effect>): Frame {
        val bar = beat / 4
        val plan = planAt(plans, bar) ?: return Frame(beat, "", List(5) { 0xFFFFFF }, 50, List(4) { 0 }, WiperMode.OFF, 0, null, emptyList())
        val cols = plan.colors.ifEmpty { listOf(0xFFFFFF) }
        val zones: List<Int>
        var bright: Int
        when (plan.pattern) {
            Pattern.SOLID -> { zones = List(5) { cols[0] }; bright = 70 }
            Pattern.PULSE -> { zones = List(5) { cols[0] }; bright = if (beat % 2 == 0) 90 else 40 }
            Pattern.ALTERNATE -> {
                val a = cols[beat % cols.size]; val b = cols[(beat + 1) % cols.size]
                zones = listOf(a, a, b, b, a); bright = 80
            }
            Pattern.RAINBOW -> { zones = List(5) { z -> cols[(beat + z) % cols.size] }; bright = 100 }
            Pattern.STROBE -> { zones = List(5) { cols[(beat / 2) % cols.size] }; bright = if (beat % 2 == 0) 100 else 15 }
            Pattern.OFF -> { zones = List(5) { cols[0] }; bright = 0 }
            Pattern.NONE -> { zones = List(5) { -1 }; bright = -1 }
        }
        // 곡 구간 마지막 마디의 마지막 박은 다음 구간으로 넘어가며 살짝 어두워진다
        val sec = song.sectionAtBar(bar)
        if (bright > 0 && bar == sec.bar + sec.bars - 1 && beat % 4 == 3) bright = (bright * 0.6).toInt()

        // 창문: 높은 구간은 2마디마다 앞줄·뒷줄 번갈아(모터가 느려서 마디 단위)
        val w = plan.window.coerceIn(0, WINDOW_LIMIT)
        val windows = when {
            w == 0 -> List(4) { 0 }
            plan.window >= WINDOW_LIMIT -> if ((bar / 2) % 2 == 0) listOf(w, w, w / 3, w / 3) else listOf(w / 3, w / 3, w, w)
            else -> List(4) { w }
        }
        val fx = Frame(
            beat = beat,
            section = plan.name,
            zones = if (Effect.LIGHT in enabled) zones else List(5) { -1 },
            brightness = if (Effect.LIGHT in enabled) bright else -1,
            // 창문 칸이 비면(0) 닫힌 상태를 뜻한다
            windows = if (Effect.WINDOW in enabled) windows else List(4) { -1 },
            wiper = if (Effect.WIPER in enabled) plan.wiper else WiperMode.OFF,
            fan = if (Effect.FAN in enabled) plan.fan else 0,
            lamp = if (Effect.HEADLAMP in enabled) lampAt(plan.lamp, beat) else null,
            cues = emptyList(),
        )
        return fx.copy(cues = describe(plan, fx))
    }

    fun lampAt(p: LampPattern, beat: Int): LampMode? = when (p) {
        LampPattern.NONE -> null
        LampPattern.TAIL -> LampMode.TAIL
        LampPattern.LOW -> LampMode.LOW
        LampPattern.BLINK -> if (beat % 2 == 0) LampMode.LOW else LampMode.TAIL
        LampPattern.TAIL_BLINK -> if (beat % 2 == 0) LampMode.TAIL else LampMode.OFF
    }

    private fun describe(plan: SectionPlan, f: Frame): List<Cue> = buildList {
        if (f.brightness >= 0) add(Cue(Effect.LIGHT, plan.pattern.label))
        if (f.windows.any { it > 0 }) add(Cue(Effect.WINDOW, "최대 ${f.windows.max()}% 열림"))
        if (f.wiper != WiperMode.OFF) add(Cue(Effect.WIPER, "천천히 움직임"))
        if (f.fan > 0) add(Cue(Effect.FAN, "${f.fan}단"))
        if (f.lamp != null) add(Cue(Effect.HEADLAMP, plan.lamp.label))
    }
}
