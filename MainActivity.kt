package nl.example.laadwatt

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var toggle: Button
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            text = "Toont het laadvermogen als getal in de statusbalk."
        }
        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        val battery = Button(this).apply {
            text = "Batterij-optimalisatie uitzetten"
            setOnClickListener { requestIgnoreBatteryOptimizations() }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(status)
            addView(toggle)
            addView(battery)
        })
    }

    override fun onResume() {
        super.onResume()
        refreshButton()
    }

    private fun onToggle() {
        if (WattService.running) {
            stopService(Intent(this, WattService::class.java))
            toggle.postDelayed({ refreshButton() }, 200)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            return
        }
        startWatt()
    }

    private fun startWatt() {
        ContextCompat.startForegroundService(this, Intent(this, WattService::class.java))
        toggle.postDelayed({ refreshButton() }, 200)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startWatt()
        else status.text = "Zonder meldingsrechten kan het getal niet in de statusbalk getoond worden."
    }

    private fun refreshButton() {
        toggle.text = if (WattService.running) "Stoppen" else "Starten"
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
    }
}
