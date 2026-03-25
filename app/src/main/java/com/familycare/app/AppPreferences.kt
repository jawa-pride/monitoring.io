package com.familycare.app

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREF_NAME = "fc_prefs"
    private var _prefs: SharedPreferences? = null

    fun init(context: Context) {
        _prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var botToken: String
        get() = _prefs!!.getString("bot_token", "") ?: ""
        set(v) { _prefs!!.edit().putString("bot_token", v).apply() }

    var chatId: String
        get() = _prefs!!.getString("chat_id", "") ?: ""
        set(v) { _prefs!!.edit().putString("chat_id", v).apply() }

    var guardianEnabled: Boolean
        get() = _prefs!!.getBoolean("guardian_enabled", true)
        set(v) { _prefs!!.edit().putBoolean("guardian_enabled", v).apply() }

    var tempDisableUntil: Long
        get() = _prefs!!.getLong("temp_disable_until", 0)
        set(v) { _prefs!!.edit().putLong("temp_disable_until", v).apply() }

    val temporarilyDisabled: Boolean
        get() = tempDisableUntil > System.currentTimeMillis()

    var setupDone: Boolean
        get() = _prefs!!.getBoolean("setup_done", false)
        set(v) { _prefs!!.edit().putBoolean("setup_done", v).apply() }

    var secretCode: String
        get() = _prefs!!.getString("secret_code", "1234") ?: "1234"
        set(v) { _prefs!!.edit().putString("secret_code", v).apply() }

    fun addLockedApp(pkg: String) {
        val set = getLockedApps().toMutableSet()
        set.add(pkg)
        _prefs!!.edit().putStringSet("locked_apps", set).apply()
    }

    fun removeLockedApp(pkg: String) {
        val set = getLockedApps().toMutableSet()
        set.remove(pkg)
        _prefs!!.edit().putStringSet("locked_apps", set).apply()
    }

    fun clearLockedApps() {
        _prefs!!.edit().putStringSet("locked_apps", emptySet()).apply()
    }

    fun getLockedApps(): Set<String> =
        _prefs!!.getStringSet("locked_apps", emptySet()) ?: emptySet()

    fun isAppLocked(pkg: String): Boolean = getLockedApps().contains(pkg)
}
