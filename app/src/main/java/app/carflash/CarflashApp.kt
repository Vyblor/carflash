package app.carflash

import android.app.Application
import android.content.Context
import app.carflash.data.ShowRepository
import app.carflash.haechi.HaechiHost
import app.carflash.vehicle.FakeVehicleGateway
import app.carflash.vehicle.PleosVehicleGateway
import app.carflash.vehicle.VehicleGateway
import app.carflash.vehicle.VehicleGateway.Companion.log

class CarflashApp : Application() {
    lateinit var vehicle: VehicleGateway
        private set
    lateinit var haechi: HaechiHost
        private set
    lateinit var shows: ShowRepository
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
        log("APP start vehicle=${vehicle.name}")
    }

    val prefs get() = getSharedPreferences("carflash", Context.MODE_PRIVATE)
}

val Context.app get() = applicationContext as CarflashApp
