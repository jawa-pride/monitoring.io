package com.familycare.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import com.familycare.app.AppPreferences
import com.familycare.app.ui.GuardianOverlayActivity

class GuardianAccessibilityService : AccessibilityService() {
    companion object {
        var instance: GuardianAccessibilityService? = null
        private val IGNORED = setOf(
            "com.familycare.app", "com.android.systemui", "android",
            "com.android.launcher", "com.android.launcher2", "com.android.launcher3",
            "com.google.android.apps.nexuslauncher", "com.sec.android.app.launcher",
            "com.miui.home", "com.huawei.android.launcher", "com.oppo.launcher",
            "com.vivo.launcher", "com.android.settings"
        )
    }

    private var lastPkg = ""; private var lastTime = 0L

    override fun onServiceConnected() { instance = this }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: ""
        if (cls.isNotEmpty() && !cls.contains("Activity")) return
        if (IGNORED.any { pkg.startsWith(it) }) return
        if (!AppPreferences.guardianEnabled || AppPreferences.temporarilyDisabled) return
        val now = System.currentTimeMillis()
        if (pkg == lastPkg && (now - lastTime) < 3000L) return
        lastPkg = pkg; lastTime = now
        val appName = try {
            val info = applicationContext.packageManager.getApplicationInfo(pkg, 0)
            applicationContext.packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) { pkg }
        startActivity(Intent(this, GuardianOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("package_name", pkg); putExtra("app_name", appName)
        })
    }

    override fun onInterrupt() { instance = null }
    override fun onDestroy() { super.onDestroy(); instance = null }
}
