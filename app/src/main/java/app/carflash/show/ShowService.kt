package app.carflash.show

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.carflash.MainActivity
import app.carflash.R
import app.carflash.vehicle.VehicleGateway.Companion.log

/**
 * 플래시몹이 도는 동안 앱 프로세스를 붙잡아 두는 포그라운드 서비스.
 * 주행 차단 화면이 앱을 가려도(E1) 엔진이 기어 변경을 받고 복원 명령을 끝까지 보낸다.
 * 엔진 자체는 Application에 있고, 이 서비스는 프로세스 수명만 책임진다.
 */
class ShowService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "플래시몹"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "플래시몹 진행", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("카플래시 · $title")
            .setContentText("플래시몹 중이에요. 기어를 바꾸면 바로 멈추고 차를 원래대로 돌려놔요")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, n)
            log("SVC foreground on")
        } catch (e: Exception) {
            log("SVC foreground failed $e")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        log("SVC foreground off")
        super.onDestroy()
    }

    companion object {
        private const val CH = "show"
        private const val ID = 7
        fun start(c: Context, title: String) {
            try {
                c.startForegroundService(Intent(c, ShowService::class.java).putExtra("title", title))
            } catch (e: Exception) {
                log("SVC start failed $e")
            }
        }
        fun stop(c: Context) {
            c.stopService(Intent(c, ShowService::class.java))
        }
    }
}
