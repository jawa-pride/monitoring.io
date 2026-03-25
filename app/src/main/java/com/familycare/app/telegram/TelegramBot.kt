package com.familycare.app.telegram

import com.familycare.app.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TelegramBot {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun base() = "https://api.telegram.org/bot${AppPreferences.botToken}"

    suspend fun sendMessage(text: String, parseMode: String = "Markdown"): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("chat_id", AppPreferences.chatId)
                put("text", text)
                put("parse_mode", parseMode)
            }
            val req = Request.Builder().url("${base()}/sendMessage")
                .post(json.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun sendPhoto(bytes: ByteArray, caption: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", AppPreferences.chatId)
                .addFormDataPart("caption", caption, caption.toRequestBody("text/plain".toMediaType()))
                .addFormDataPart("photo", "snap.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
                .build()
            val req = Request.Builder().url("${base()}/sendPhoto").post(body).build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun answerCallback(id: String, text: String = ""): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply { put("callback_query_id", id); put("text", text) }
            val req = Request.Builder().url("${base()}/answerCallbackQuery")
                .post(json.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    suspend fun sendMenu(): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = org.json.JSONArray().apply {
                put(org.json.JSONArray().apply {
                    put(btn("📸 Snapshot", "/snapshot"))
                    put(btn("📍 Lokasi", "/location"))
                })
                put(org.json.JSONArray().apply {
                    put(btn("🔒 Lock Semua App", "/lockall"))
                    put(btn("🔓 Unlock Semua", "/unlockall"))
                })
                put(org.json.JSONArray().apply {
                    put(btn("⏸ Pause 30 Menit", "/unlock30"))
                    put(btn("📋 Log Aktivitas", "/log"))
                })
                put(org.json.JSONArray().apply {
                    put(btn("🔐 App Terkunci", "/lockedapps"))
                    put(btn("📊 Status", "/status"))
                })
                put(org.json.JSONArray().apply {
                    put(btn("✅ Aktifkan", "/enable"))
                    put(btn("⛔ Nonaktifkan", "/disable"))
                })
            }
            val keyboard = JSONObject().apply { put("inline_keyboard", rows) }
            val json = JSONObject().apply {
                put("chat_id", AppPreferences.chatId)
                put("text", "🛡️ *Asisten Keluarga - Panel Kontrol*\n\n💡 Ganti kode: `/setkode 1234`")
                put("parse_mode", "Markdown")
                put("reply_markup", keyboard)
            }
            val req = Request.Builder().url("${base()}/sendMessage")
                .post(json.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    private fun btn(text: String, data: String) = JSONObject().apply {
        put("text", text); put("callback_data", data)
    }

    suspend fun getUpdates(offset: Long = 0): List<TelegramUpdate> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("${base()}/getUpdates?offset=$offset&timeout=10").get().build()
            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return@withContext emptyList()
            val json = JSONObject(res.body?.string() ?: return@withContext emptyList())
            if (!json.getBoolean("ok")) return@withContext emptyList()
            val results = json.getJSONArray("result")
            val updates = mutableListOf<TelegramUpdate>()
            for (i in 0 until results.length()) {
                val u = results.getJSONObject(i)
                val uid = u.getLong("update_id")
                u.optJSONObject("message")?.let { msg ->
                    updates.add(TelegramUpdate(uid, msg.getJSONObject("chat").getString("id"), msg.optString("text", "")))
                }
                u.optJSONObject("callback_query")?.let { cb ->
                    updates.add(TelegramUpdate(uid, cb.getJSONObject("message").getJSONObject("chat").getString("id"), cb.optString("data", ""), cb.getString("id")))
                }
            }
            updates
        } catch (e: Exception) { emptyList() }
    }
}

data class TelegramUpdate(val updateId: Long, val chatId: String, val text: String, val callbackId: String? = null)
