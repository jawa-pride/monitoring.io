package com.familycare.app.telegram

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.familycare.app.ActivityLog
import com.familycare.app.AppPreferences
import com.familycare.app.FamilyCareApp
import com.familycare.app.camera.CameraHelper
import com.familycare.app.ui.MainActivity
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class TelegramPollingService : LifecycleService() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastId = 0L
    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    override fun onCreate() {
        super.onCreate()
        startForeground(FamilyCareApp.NOTIFICATION_ID, buildNotif())
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                try {
                    val updates = TelegramBot.getUpdates(lastId + 1)
                    for (u in updates) {
                        if (u.chatId == AppPreferences.chatId) handleCmd(u)
                        if (u.updateId > lastId) lastId = u.updateId
                    }
                } catch (e: Exception) {}
                delay(3000)
            }
        }
    }

    private suspend fun handleCmd(u: TelegramUpdate) {
        u.callbackId?.let { TelegramBot.answerCallback(it) }
        val cmd = u.text.trim()
        val cmdLower = cmd.lowercase()

        when {
            cmdLower == "/start" || cmdLower == "/menu" -> TelegramBot.sendMenu()
            cmdLower == "/snapshot" -> {
                TelegramBot.sendMessage("📸 Mengambil snapshot...")
                captureAndSend("📸 *Snapshot Manual*\n🕐 ${fmt.format(Date())}")
            }
            cmdLower == "/location" -> sendLocation()
            cmdLower == "/lockall" -> {
                AppPreferences.guardianEnabled = true
                AppPreferences.tempDisableUntil = 0
                TelegramBot.sendMessage("🔒 Guardian diaktifkan & semua app terlindungi.")
            }
            cmdLower == "/unlockall" -> {
                AppPreferences.clearLockedApps()
                TelegramBot.sendMessage("🔓 Semua app terkunci sudah dibuka.")
            }
            cmdLower == "/unlock30" -> {
                AppPreferences.tempDisableUntil = System.currentTimeMillis() + 30 * 60 * 1000
                TelegramBot.sendMessage("⏸ Guardian dinonaktifkan *30 menit*.")
            }
            cmdLower == "/log" -> TelegramBot.sendMessage(ActivityLog.formatForTelegram())
            cmdLower == "/lockedapps" -> {
                val locked = AppPreferences.getLockedApps()
                if (locked.isEmpty()) TelegramBot.sendMessage("✅ Tidak ada app yang terkunci.")
                else TelegramBot.sendMessage("🔒 *App Terkunci:*\n${locked.joinToString("\n") { "• `$it`" }}\n\nGunakan `/unlockapp <package>`")
            }
            cmdLower.startsWith("/unlockapp ") -> {
                val pkg = cmd.removePrefix("/unlockapp ").trim()
                AppPreferences.removeLockedApp(pkg)
                TelegramBot.sendMessage("🔓 App `$pkg` sudah dibuka.")
            }
            cmdLower.startsWith("/setkode ") -> {
                val code = cmd.removePrefix("/setkode ").trim()
                if (code.length >= 4) {
                    AppPreferences.secretCode = code
                    TelegramBot.sendMessage("✅ Kode berhasil diubah.")
                } else TelegramBot.sendMessage("❌ Kode minimal 4 karakter.")
            }
            cmdLower == "/enable" -> {
                AppPreferences.guardianEnabled = true
                AppPreferences.tempDisableUntil = 0
                TelegramBot.sendMessage("✅ Guardian *diaktifkan*.")
            }
            cmdLower == "/disable" -> {
                AppPreferences.guardianEnabled = false
                TelegramBot.sendMessage("⛔ Guardian *dinonaktifkan*.")
            }
            cmdLower == "/status" -> TelegramBot.sendMessage(buildStatus())
            else -> TelegramBot.sendMenu()
        }
    }

    private fun captureAndSend(caption: String) {
        CameraHelper.captureSnapshot(this) { bytes ->
            scope.launch {
                if (bytes != null) TelegramBot.sendPhoto(bytes, caption)
                else TelegramBot.sendMessage("⚠️ Kamera tidak tersedia.")
            }
        }
    }

    suspend fun sendLocation() {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var loc: android.location.Location? = null
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    if (lm.isProviderEnabled(p)) { loc = lm.getLastKnownLocation(p); if (loc != null) break }
                } catch (e: SecurityException) {}
            }
            if (loc != null) {
                val url = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
                TelegramBot.sendMessage("📍 *Lokasi Terakhir*\n`${loc.latitude}, ${loc.longitude}`\n[Buka di Maps]($url)")
            } else TelegramBot.sendMessage("📍 Lokasi tidak tersedia.")
        } catch (e: Exception) { TelegramBot.sendMessage("📍 Gagal ambil lokasi.") }
    }

    private fun buildStatus(): String {
        val locked = AppPreferences.getLockedApps().size
        val pause = if (AppPreferences.temporarilyDisabled) {
            val rem = (AppPreferences.tempDisableUntil - System.currentTimeMillis()) / 60000
            "⏸ ~${rem} menit"
        } else "—"
        return """
🛡️ *Status Asisten Keluarga*

• Guardian: ${if (AppPreferences.guardianEnabled) "✅ Aktif" else "⛔ Nonaktif"}
• Pause: $pause
• App terkunci: $locked
• Kode: ${"*".repeat(AppPreferences.secretCode.length)}
• Log: ${ActivityLog.getAll().size} entri
• Waktu: ${fmt.format(Date())}
        """.trimIndent()
    }

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, FamilyCareApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Asisten Keluarga")
            .setContentText("Perlindungan aktif")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
    }
}
