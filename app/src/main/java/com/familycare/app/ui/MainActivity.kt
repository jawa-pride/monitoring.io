package com.familycare.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.familycare.app.AppPreferences
import com.familycare.app.telegram.TelegramBot
import com.familycare.app.telegram.TelegramPollingService
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppPreferences.setupDone) showStatus() else showSetup()
    }

    private fun showSetup() {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(64, 80, 64, 80) }

        layout.addView(TextView(this).apply { text = "⚙️ Setup Asisten Keluarga"; textSize = 22f; setPadding(0,0,0,8) })
        layout.addView(TextView(this).apply { text = "Hubungkan dengan bot Telegram untuk notifikasi."; textSize = 14f; setPadding(0,0,0,32) })

        val etToken = EditText(this).apply { hint = "Bot Token (dari @BotFather)"; setText(AppPreferences.botToken) }
        val etChat = EditText(this).apply { hint = "Chat ID lo (dari @userinfobot)"; setText(AppPreferences.chatId) }
        val etKode = EditText(this).apply { hint = "Kode rahasia (min 4 karakter)"; setText(AppPreferences.secretCode) }

        layout.addView(TextView(this).apply { text = "Bot Token"; textSize = 14f })
        layout.addView(etToken)
        layout.addView(TextView(this).apply { text = "Chat ID"; textSize = 14f; setPadding(0,16,0,0) })
        layout.addView(etChat)
        layout.addView(TextView(this).apply { text = "Kode Rahasia"; textSize = 14f; setPadding(0,16,0,0) })
        layout.addView(etKode)

        layout.addView(Button(this).apply {
            text = "🔍 Test Koneksi"; setPadding(0,16,0,16)
            setOnClickListener {
                AppPreferences.botToken = etToken.text.toString().trim()
                AppPreferences.chatId = etChat.text.toString().trim()
                scope.launch {
                    val ok = TelegramBot.sendMessage("✅ *Asisten Keluarga* terhubung!\nKirim /menu untuk panel kontrol.")
                    Toast.makeText(this@MainActivity, if (ok) "✅ Berhasil! Cek Telegram." else "❌ Gagal. Cek token & chat ID.", Toast.LENGTH_LONG).show()
                }
            }
        })
        layout.addView(Button(this).apply {
            text = "💾 Simpan & Mulai"; setPadding(0,16,0,16)
            setOnClickListener {
                val token = etToken.text.toString().trim()
                val chat = etChat.text.toString().trim()
                val kode = etKode.text.toString().trim()
                if (token.isEmpty() || chat.isEmpty()) { Toast.makeText(this@MainActivity, "Isi token & chat ID dulu", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                if (kode.length < 4) { Toast.makeText(this@MainActivity, "Kode minimal 4 karakter", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                AppPreferences.botToken = token
                AppPreferences.chatId = chat
                AppPreferences.secretCode = kode
                AppPreferences.setupDone = true
                startSvc(); showStatus()
            }
        })

        scroll.addView(layout); setContentView(scroll)
    }

    private fun showStatus() {
        startSvc()
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(64, 80, 64, 80) }

        val accessOk = isAccessibilityEnabled()
        layout.addView(TextView(this).apply { text = "🛡️ Asisten Keluarga"; textSize = 24f; setPadding(0,0,0,16) })
        layout.addView(TextView(this).apply {
            text = if (accessOk) "✅ Accessibility: Aktif" else "⚠️ Accessibility: Belum aktif"
            textSize = 14f; setPadding(0,0,0,4)
        })
        layout.addView(TextView(this).apply {
            text = if (AppPreferences.guardianEnabled) "✅ Guardian: Aktif" else "⛔ Guardian: Nonaktif"
            textSize = 14f; setPadding(0,0,0,24)
        })

        if (!accessOk) {
            layout.addView(Button(this).apply {
                text = "⚙️ Buka Accessibility Settings"
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            })
        }

        layout.addView(Button(this).apply {
            text = "📲 Kirim Menu ke Telegram"
            setOnClickListener { scope.launch { TelegramBot.sendMenu(); Toast.makeText(this@MainActivity, "Menu dikirim!", Toast.LENGTH_SHORT).show() } }
        })
        layout.addView(Button(this).apply {
            text = "🔄 Reset Setup"
            setOnClickListener { AppPreferences.setupDone = false; AppPreferences.botToken = ""; AppPreferences.chatId = ""; showSetup() }
        })

        layout.addView(TextView(this).apply {
            text = "\n📋 Commands Telegram:\n/menu — Panel kontrol\n/setkode 1234 — Ganti kode\n/snapshot — Ambil foto\n/location — Kirim lokasi\n/lockedapps — App terkunci\n/unlockall — Unlock semua\n/unlock30 — Pause 30 menit\n/status — Status lengkap"
            textSize = 13f
        })

        setContentView(layout)
    }

    private fun startSvc() {
        val svc = Intent(this, TelegramPollingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
