package app.carflash.vehicle

import app.carflash.vehicle.VehicleGateway.Companion.log
import kotlinx.coroutines.flow.MutableStateFlow

/** Pleos가 아닌 기기에서 쓰는 가짜 차. 값은 메모리에만 있다. */
class FakeVehicleGateway : VehicleGateway {
    override val name = "fake"
    override val gear = MutableStateFlow(Gear.P)
    override val restricted = MutableStateFlow(false)
    override val caps = MutableStateFlow<VehicleCaps?>(VehicleCaps(ambientZones = 5, windows = 4, wiper = true, fan = true, headlamp = true))
    override val battery = MutableStateFlow<Int?>(null)

    private val ambient = MutableList(5) { 0xFFFFFF }
    private val bright = MutableList(5) { 50 }
    private val windows = MutableList(4) { 0 }
    private var wiper = WiperMode.OFF
    private var fan = 2
    private var lamp = LampMode.OFF

    override fun start() { log("VEH fake start") }
    override fun release() {}
    override fun setAmbient(zone: Int, rgb: Int) { if (zone in ambient.indices) ambient[zone] = rgb }
    override fun setBrightness(zone: Int, percent: Int) { if (zone in bright.indices) bright[zone] = percent }
    override fun setWindow(index: Int, position: Int) { if (index in windows.indices) windows[index] = position }
    override fun setWiper(mode: WiperMode) { wiper = mode }
    override fun setFan(level: Int) { fan = level }
    override fun setLamp(mode: LampMode) { lamp = mode }
    override suspend fun snapshot() = VehicleSnapshot(ambient.toList(), bright.toList(), windows.toList(), wiper, fan,
        lowBeam = if (lamp == LampMode.LOW) "ON" else "OFF", tailLamp = if (lamp == LampMode.TAIL) "ON" else "OFF")
    override fun restore(s: VehicleSnapshot) {
        log("VEH restore $s t=${System.currentTimeMillis()}")
        s.ambient.forEachIndexed { i, c -> setAmbient(i, c ?: 0xFFFFFF) }
        s.windows.forEachIndexed { i, w -> setWindow(i, w ?: 0) }
        setWiper(s.wiper ?: WiperMode.OFF)
        s.fan?.let { setFan(it) }
        setLamp(if (s.lowBeam == "ON") LampMode.LOW else if (s.tailLamp == "ON") LampMode.TAIL else LampMode.OFF)
    }
}
