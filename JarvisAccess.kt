package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class JarvisAccess : AccessibilityService() {
    companion object { var i: JarvisAccess? = null }
    override fun onServiceConnected() { i = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(x: Intent?): Boolean { i = null; return super.onUnbind(x) }
}
