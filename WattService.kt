package nl.example.laadwatt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import kotlin.math.abs
import kotlin.math.roundToInt

class WattService : Service() {

    companion object {
        @Volatile var running = false
        private const val CHANNEL_ID = "watt"
        private const val NOTIF_ID = 1
        private const val INTERVAL_MS = 2000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var bm: BatteryManager
    private lateinit var nm: NotificationManager

    private val tick = object : Runnable {
        override fun run() {
            nm.notify(NOTIF_ID, buildNotification(readWatts()))
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    // Stopt de service zodra de lader eruit gaat
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_DISCONNECTED) stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Laadwattage", NotificationManager.IMPORTANCE_LOW)
        )
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(0f),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        ContextCompat.registerReceiver(
            this, powerReceiver,
            IntentFilter(Intent.ACTION_POWER_DISCONNECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        handler.post(tick)
    }

    private fun readWatts(): Float {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val mV = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val raw = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // Sommige toestellen geven µA, andere mA terug
        val mA = if (abs(raw) > 20_000) raw / 1000f else raw.toFloat()
        return abs(mA) * mV / 1_000_000f
    }

    private fun buildNotification(watts: Float): Notification {
        val label = if (watts < 10f) "%.1f".format(watts) else watts.roundToInt().toString()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(textIcon(label))
            .setContentTitle("Laadvermogen")
            .setContentText("%.1f W".format(watts))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun textIcon(text: String): IconCompat {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = size * 0.7f
        }
        // Maak de tekst smaller als hij niet past (bijv. "12.5")
        val maxWidth = size * 0.95f
        val w = paint.measureText(text)
        if (w > maxWidth) paint.textSize *= maxWidth / w
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2
        Canvas(bmp).drawText(text, size / 2f, y, paint)
        return IconCompat.createWithBitmap(bmp)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(powerReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
