package app.carflash.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import app.carflash.vehicle.VehicleGateway.Companion.log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Pleos Vehicle SDK 2.3.2 구현. API 모양은 공식 문서(Get·Set·Callback·Capability)와
 * contest/probe-app 시험 앱에서 에뮬레이터로 확인한 호출만 쓴다.
 */
class PleosVehicleGateway(private val context: Context) : VehicleGateway {
    override val name = "pleos"
    private val vehicle = Vehicle(context)
    private val main = Handler(Looper.getMainLooper())
    private var car: Car? = null

    override val gear = MutableStateFlow(Gear.UNKNOWN)
    override val restricted = MutableStateFlow(false)
    override val caps = MutableStateFlow<VehicleCaps?>(null)
    override val battery = MutableStateFlow<Int?>(null)

    private var seats: List<SeatArea> = emptyList()
    private var windowAreas: List<WindowArea> = emptyList()
    private var wiperArea: WindowArea? = null
    private var fanArea: SeatArea? = null
    private var hasLight = false
    private var hasWindow = false
    private var hasWiper = false
    private var hasFan = false
    private var fanMax = 8
    private var windowMax = 10

    override fun start() {
        vehicle.initialize()
        log("VEH pleos initialize")
        setupUx()
        // 초기화 직후보다 약 2.5초 뒤 등록이 안정적이었다(시험 앱)
        main.postDelayed({ setupVehicle() }, 2500)
    }

    private fun setupUx() {
        try {
            val c = Car.createCar(context)
            car = c
            val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            restricted.value = ux.currentCarUxRestrictions.isRequiresDistractionOptimization
            ux.registerListener { r ->
                restricted.value = r.isRequiresDistractionOptimization
                log("VEH ux requiresDO=${r.isRequiresDistractionOptimization}")
            }
        } catch (e: Exception) {
            log("VEH ux setup failed $e")
        }
    }

    private fun mapGear(g: VehicleGear?): Gear {
        val n = g?.name?.uppercase() ?: return Gear.UNKNOWN
        return when {
            n.contains("PARK") || n == "P" -> Gear.P
            n.contains("REVERSE") || n == "R" -> Gear.R
            n.contains("NEUTRAL") || n == "N" -> Gear.N
            n.contains("DRIVE") || n == "D" || n.startsWith("GEAR_") -> Gear.D
            else -> Gear.UNKNOWN
        }
    }

    private fun setupVehicle() {
        try {
            vehicle.getGear().registerGearSelection(object : GearSelectionListener {
                override fun onSelected(g: VehicleGear?) {
                    val m = mapGear(g)
                    log("VEH gear raw=$g mapped=$m t=${System.currentTimeMillis()}")
                    gear.value = m
                }
                override fun onFailed(e: Exception) { log("VEH gear listener failed $e") }
            })
        } catch (e: Exception) { log("VEH gear register failed $e") }

        val light = vehicle.getLight()
        light.checkAmbientLightBrightnessColorCapabilities({ c, areas ->
            seats = areas.toList(); hasLight = seats.isNotEmpty(); publishCaps()
            log("VEH cap ambient ${c.accessType} zones=${seats.size}")
        }, { e -> log("VEH cap ambient failed $e"); publishCaps() })
        vehicle.getWindow().checkWindowPositionCapabilities({ c, m ->
            windowAreas = m.keys.toList(); hasWindow = windowAreas.isNotEmpty()
            m.values.firstOrNull()?.let { b -> windowMax = (b.maxValue as? Number)?.toInt() ?: 10 }
            publishCaps(); log("VEH cap window ${c.accessType} n=${windowAreas.size} max=$windowMax")
        }, { e -> log("VEH cap window failed $e"); publishCaps() })
        vehicle.getWiper().checkWiperSwitchCapabilities({ c, m ->
            wiperArea = m.keys.firstOrNull(); hasWiper = wiperArea != null
            publishCaps(); log("VEH cap wiper ${c.accessType}")
        }, { e -> log("VEH cap wiper failed $e"); publishCaps() })
        vehicle.getHVAC().checkHvacFanSpeedCapabilities({ c, m ->
            fanArea = m.keys.firstOrNull(); hasFan = fanArea != null
            m.values.firstOrNull()?.let { b -> fanMax = (b.maxValue as? Number)?.toInt() ?: 8 }
            publishCaps(); log("VEH cap fan ${c.accessType} max=$fanMax")
        }, { e -> log("VEH cap fan failed $e"); publishCaps() })
    }

    private fun publishCaps() {
        caps.value = VehicleCaps(
            ambientZones = if (hasLight) seats.size else 0,
            windows = if (hasWindow) windowAreas.size else 0,
            wiper = hasWiper, fan = hasFan, fanMax = fanMax, windowMax = windowMax,
        )
    }

    override fun release() {
        try { vehicle.release() } catch (_: Exception) {}
        try { car?.disconnect() } catch (_: Exception) {}
    }

    override fun setAmbient(zone: Int, rgb: Int) {
        val a = seats.getOrNull(zone) ?: return
        vehicle.getLight().setAmbientLightBrightnessColor(a, rgb and 0xFFFFFF, { _, _ -> }, { e -> log("VEH set ambient failed $e") })
    }

    override fun setBrightness(zone: Int, percent: Int) {
        val a = seats.getOrNull(zone) ?: return
        vehicle.getLight().setAmbientLightBrightness(a, percent.coerceIn(0, 100), { _, _ -> }, { e -> log("VEH set brightness failed $e") })
    }

    override fun setWindow(index: Int, position: Int) {
        val a = windowAreas.getOrNull(index) ?: return
        vehicle.getWindow().setWindowPosition(a, position.coerceIn(0, windowMax), { _, _ -> }, { e -> log("VEH set window failed $e") })
    }

    override fun setWiper(mode: WiperMode) {
        val a = wiperArea ?: return
        val v = when (mode) {
            WiperMode.OFF -> WiperSwitch.OFF
            WiperMode.LOW -> WiperSwitch.CONTINUOUS_LEVEL_1
            WiperMode.HIGH -> WiperSwitch.CONTINUOUS_LEVEL_3
        }
        vehicle.getWiper().setWiperSwitch(a, v, { _, _ -> }, { e -> log("VEH set wiper failed $e") })
    }

    override fun setFan(level: Int) {
        val a = fanArea ?: return
        vehicle.getHVAC().setHvacFanSpeed(a, level.coerceIn(1, fanMax), { _, _ -> }, { e -> log("VEH set fan failed $e") })
    }

    private suspend fun <T> ask(block: (ok: (T?) -> Unit, fail: (Exception) -> Unit) -> Unit): T? =
        withTimeoutOrNull(1500) {
            suspendCancellableCoroutine { cont ->
                try {
                    block({ v -> if (cont.isActive) cont.resume(v) }, { e -> log("VEH get failed $e"); if (cont.isActive) cont.resume(null) })
                } catch (e: Exception) {
                    log("VEH get threw $e"); if (cont.isActive) cont.resume(null)
                }
            }
        }

    override suspend fun snapshot(): VehicleSnapshot {
        val l = vehicle.getLight()
        val amb = seats.map { a -> ask<Int> { ok, f -> l.getAmbientLightBrightnessColor(a, { v -> ok(v) }, f) } }
        val br = seats.map { a -> ask<Int> { ok, f -> l.getAmbientLightBrightness(a, { v -> ok(v) }, f) } }
        val win = windowAreas.map { a -> ask<Int> { ok, f -> vehicle.getWindow().getCurrentWindowPosition(a, { v: Int? -> ok(v) }, f) } }
        val wip = wiperArea?.let { a -> ask<WiperSwitch> { ok, f -> vehicle.getWiper().getCurrentWiperSwitch(a, { v: WiperSwitch? -> ok(v) }, f) } }
        val fan = fanArea?.let { a -> ask<Int> { ok, f -> vehicle.getHVAC().getHvacFanSpeed(a, { v -> ok(v) }, f) } }
        val wm = when (wip) {
            null -> null
            WiperSwitch.OFF -> WiperMode.OFF
            WiperSwitch.CONTINUOUS_LEVEL_3 -> WiperMode.HIGH
            else -> WiperMode.LOW
        }
        val s = VehicleSnapshot(amb, br, win, wm, fan)
        log("VEH snapshot $s")
        return s
    }

    override fun restore(s: VehicleSnapshot) {
        log("VEH restore $s t=${System.currentTimeMillis()}")
        s.ambient.forEachIndexed { i, c -> setAmbient(i, c ?: 0xFFFFFF) }
        s.brightness.forEachIndexed { i, b -> setBrightness(i, b ?: 50) }
        s.windows.forEachIndexed { i, w -> setWindow(i, w ?: 0) }
        setWiper(s.wiper ?: WiperMode.OFF)
        s.fan?.let { setFan(it) }
    }
}
