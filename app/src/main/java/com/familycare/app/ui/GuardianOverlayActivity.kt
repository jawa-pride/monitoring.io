package com.familycare.app.ui

import android.content.Context
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.familycare.app.ActivityLog
import com.familycare.app.AppPreferences
import com.familycare.app.camera.CameraHelper
import com.familycare.app.telegram.TelegramBot
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class GuardianOverlayActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    private var pkgName = ""; private var appName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        pkgName = intent.getStringExtra("package_name") ?: ""
        appName = intent.getStringExtra("app_name") ?: pkgName
        setContentView(if (AppPreferences.isAppLocked(pkgName)) buildLockedView() else buildVerifyView())
    }

    private fun buildVerifyView(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#CC000000")) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            setBackgroundColor(Color.WHITE)
            elevation = 16f
        }
        card.addView(TextView(this).apply {
            text = "🛡️ Verifikasi Pengguna"; textSize = 20f; setPadding(0, 0, 0, 8)
        })
        card.addView(TextView(this).apply {
            text = "Membuka: $appName"; textSize = 14f
            setTextColor(Color.parseColor("#757575")); setPadding(0, 0, 0, 32)
        })
        card.addView(Button(this).apply {
            text = "✅  Saya Pemilik"
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE); textSize = 16f; setPadding(0, 24, 0, 24)
            setOnClickListener { onOwner() }
        })
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24)
        })
        card.addView(Button(this).apply {
            text = "🚫  Bukan Saya"
            setBackgroundColor(Color.parseColor("#F44336"))
            setTextColor(Color.WHITE); textSize = 16f; setPadding(0, 24, 0, 24)
            setOnClickListener { onStranger() }
        })
        card.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER; setMargins(48, 0, 48, 0) }
        root.addView(card); return root
    }

    private fun buildLockedView(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#CC000000")) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            setBackgroundColor(Color.WHITE)
            elevation = 16f
        }
        val statusTv = TextView(this).apply { textSize = 13f; setPadding(0, 8, 0, 0) }
        card.addView(TextView(this).apply {
            text = "🔒 App Terkunci"; textSize = 22f; setPadding(0, 0, 0, 8)
        })
        card.addView(TextView(this).apply {
            text = "$appName terkunci.\nMasukkan kode untuk membuka."
            textSize = 14f; setTextColor(Color.parseColor("#757575")); setPadding(0, 0, 0, 24)
        })
        val etCode = EditText(this).apply {
            hint = "Masukkan kode"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            textSize = 18f; gravity = Gravity.CENTER
        }
        card.addView(etCode)
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 16)
        })
        card.addView(Button(this).apply {
            text = "🔓 Buka"
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE); textSize = 16f; setPadding(0, 24, 0, 24)
            setOnClickListener {
                if (etCode.text.toString().trim() == AppPreferences.secretCode) {
                    AppPreferences.removeLockedApp(pkgName); finish()
                } else {
                    statusTv.text = "❌ Kode salah"; statusTv.setTextColor(Color.RED); etCode.text.clear()
                }
            }
        })
        card.addView(statusTv)
        card.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER; setMargins(48, 0, 48, 0) }
        root.addView(card); return root
    }

    private fun onOwner() {
        ActivityLog.add(pkgName, appName, "owner")
        CameraHelper.captureSnapshot(this) { bytes ->
            scope.launch {
                if (bytes != null) TelegramBot.sendPhoto(bytes, "✅ *Pemilik membuka app*\n📱 $appName\n🕐 ${fmt.format(Date())}")
            }
        }
        finish()
    }

    private fun onStranger() {
        ActivityLog.add(pkgName, appName, "stranger")
        AppPreferences.addLockedApp(pkgName)
        CameraHelper.captureSnapshot(this) { bytes ->
            scope.launch {
                val time = fmt.format(Date())
                if (bytes != null) TelegramBot.sendPhoto(bytes, "🚨 *ORANG ASING!*\n📱 $appName\n🕐 $time")
                else TelegramBot.sendMessage("🚨 *ORANG ASING!*\n📱 $appName\n🕐 $time\n⚠️ Snapshot tidak tersedia.")
                sendLoc()
            }
        }
        ActivityLog.add(pkgName, appName, "locked")
        finish()
    }

    private suspend fun sendLoc() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var loc: android.location.Location? = null
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try { if (lm.isProviderEnabled(p)) { loc = lm.getLastKnownLocation(p); if (loc != null) break } }
                catch (e: SecurityException) {}
            }
            if (loc != null) {
                val url = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                TelegramBot.sendMessage("📍 *Lokasi*\n`${loc.latitude}, ${loc.longitude}`\n[Buka di Maps]($url)")
            } else TelegramBot.sendMessage("📍 Lokasi tidak tersedia.")
        } catch (e: Exception) {}
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    @Deprecated("Deprecated")
    override fun onBackPressed() { /* Disable */ }
}
