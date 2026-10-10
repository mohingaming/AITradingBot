package com.example.aibot

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppDetectorService : AccessibilityService() {

    companion object {
        @Volatile var tradingBoxVisible = false
        @Volatile var currentApp = ""
        private val MARKERS = listOf("Payout", "End of trade", "Investment", "Up", "Down")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        currentApp = event.packageName?.toString() ?: currentApp
        tradingBoxVisible = scan(rootInActiveWindow)
    }

    private fun scan(root: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (root == null || depth > 30) return false
        val text = root.text?.toString() ?: ""
        if (text.isNotEmpty() && MARKERS.any { text.contains(it, ignoreCase = true) }) return true
        for (i in 0 until root.childCount) {
            if (scan(root.getChild(i), depth + 1)) return true
        }
        return false
    }

    override fun onInterrupt() {}
}
