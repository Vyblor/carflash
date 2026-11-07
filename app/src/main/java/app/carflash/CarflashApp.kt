package app.carflash

import android.app.Application
import android.content.Context
import app.carflash.data.Effect
import app.carflash.data.has
import app.carflash.show.BatteryGuard
import app.carflash.data.MockGroupServer
import app.carflash.data.ShowRepository
import app.carflash.haechi.HaechiHost
import app.carflash.show.ShowEngine
import app.carflash.vehicle.FakeVehicleGateway
import app.carflash.vehicle.PleosVehicleGateway
import app.carflash.vehicle.VehicleGateway
import app.carflash.vehicle.VehicleGateway.Companion.log
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow

class CarflashApp : Application() {
    lateinit var vehicle: VehicleGateway
        private set
    lateinit var haechi: HaechiHost
        private set
    lateinit var shows: ShowRepository
        private set
    lateinit var engine: ShowEngine
        private set
    lateinit var groups: MockGroupServer
        private set

    override fun onCreate() {
        super.onCreate()
        vehicle = try {
            PleosVehicleGateway(this).also { it.start() }
        } catch (t: Throwable) {
            log("VEH pleos unavailable ($t), using fake")
            FakeVehicleGateway().also { it.start() }
        }
        haechi = HaechiHost(this)
        shows = ShowRepository(this)
        engine = ShowEngine(this, vehicle, shows, haechi)
        groups = MockGroupServer(shows)
        // 서버가 시작 시각을 보내면 이 차도 같은 순간에 시작한다(D4)
        groups.setOnStart { r ->
            val show = shows.byId(r.showId) ?: return@setOnStart
            val c = vehicle.caps.value
            val low = BatteryGuard.low(vehicle.battery.value)
            val fx = show.effects.filter { e ->
                (c == null || c.has(e)) &&
                    !(low && (e == Effect.WINDOW || e == Effect.FAN))
            }.toSet()
            engine.start(show, fx, true, startAtMs = groups.localAt(r.startAtServer ?: groups.serverNow()), group = groups.info(r))
        }
        watchNetwork()
        log("APP start vehicle=${vehicle.name}")
    }

    /** 인터넷 연결(E4). 코드·모임은 서버가 필요하므로 끊기면 막는다. 추천 플래시몹은 앱 안에 있어 그대로. */
    val online = MutableStateFlow(true)
    private var netOnline = true
    /** 디버그 빌드 시험 전용: 오프라인 흉내. */
    var debugOffline = false
        set(v) { field = v; publishOnline() }

    private fun publishOnline() {
        val v = netOnline && !debugOffline
        if (online.value != v) log("NET online=$v")
        online.value = v
    }

    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val now = cm.getNetworkCapabilities(cm.activeNetwork)
        netOnline = now?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        publishOnline()
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
                netOnline = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET); publishOnline()
            }
            override fun onLost(n: Network) { netOnline = false; publishOnline() }
        })
    }

    /** 디버그 빌드 시험 전용: 카운트다운 길이(ms). 0이면 기본 3초. */
    var debugCountdownMs: Long = 0
    /** 디버그 빌드 시험 전용: 앱 영역 너비(dp)를 좁혀 분할 화면(840)을 흉내 낸다. 0이면 그대로. */
    val debugWidthDp = androidx.compose.runtime.mutableIntStateOf(0)
    /** 디버그 빌드 시험 전용: 곡 중간(ms)부터 시작. */
    var debugOffsetMs: Long = 0

    val prefs get() = getSharedPreferences("carflash", Context.MODE_PRIVATE)
}

val Context.app get() = applicationContext as CarflashApp
