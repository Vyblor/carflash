package app.carflash.vehicle

import android.util.Log
import kotlinx.coroutines.flow.StateFlow

enum class Gear { P, R, N, D, UNKNOWN }

enum class WiperMode { OFF, LOW, HIGH }

/**
 * 외부 조명 스위치(Pleos Light: 전조등 LowBeam + 미등 TailLamp). 차는 셋 중 하나만 고른다:
 * 미등을 켜면 전조등이 꺼지고, 전조등을 끄면 미등도 꺼진다(에뮬레이터 시험 2026-11-07). P단에서만 쓸 수 있다(D단 50001).
 */
enum class LampMode { OFF, TAIL, LOW }

/** 이 차에서 쓸 수 있는 효과. check() 결과로 채운다. */
data class VehicleCaps(
    val ambientZones: Int,
    val windows: Int,
    val wiper: Boolean,
    val fan: Boolean,
    val fanMax: Int = 8,
    val windowMax: Int = 10,
    /** 전조등·미등 스위치 쓰기 가능 */
    val headlamp: Boolean = false,
) {
    val light get() = ambientZones > 0
    val window get() = windows > 0
}

/** 플래시몹 시작 직전 차 상태. 정지·종료·기어 변경 때 이 값으로 되돌린다. */
data class VehicleSnapshot(
    val ambient: List<Int?>,
    val brightness: List<Int?>,
    val windows: List<Int?>,
    val wiper: WiperMode?,
    val fan: Int?,
    /** 전조등·미등 스위치 원래 값(LightSwitch 이름: OFF/ON/AUTOMATIC/DAYTIME_RUNNING). null = 모름 또는 손대지 않음 */
    val lowBeam: String? = null,
    val tailLamp: String? = null,
)

/**
 * 차와 이야기하는 유일한 통로. 실제 차(Pleos Vehicle SDK)와 가짜 차(다른 기기·미리보기) 두 구현이 있다.
 * 영역(area)은 반드시 check 결과에서 받은 것을 쓴다(직접 만든 SeatArea는 0x1 not supported).
 */
interface VehicleGateway {
    val name: String
    val gear: StateFlow<Gear>
    /** 주행 제한 중(DO 필요). D단에서 true. */
    val restricted: StateFlow<Boolean>
    val caps: StateFlow<VehicleCaps?>
    /** 배터리 잔량 %(모르면 null). E3 배터리 보호에 쓴다. */
    val battery: StateFlow<Int?>

    fun start()
    fun release()

    fun setAmbient(zone: Int, rgb: Int)
    fun setBrightness(zone: Int, percent: Int)
    fun setWindow(index: Int, position: Int)
    fun setWiper(mode: WiperMode)
    fun setFan(level: Int)
    fun setLamp(mode: LampMode)

    suspend fun snapshot(): VehicleSnapshot
    fun restore(s: VehicleSnapshot)

    companion object {
        const val TAG = "CARFLASH"
        fun log(msg: String) = Log.i(TAG, msg)
    }
}
