package com.example.host.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class InjectorAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        controller = Controller(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (controller?.service == this) controller = null
    }

    class Controller(val service: AccessibilityService) {
        fun injectTap(x: Float, y: Float) {
            val p = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(p, 0, 1)
            val gd = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gd, null, null)
        }
    }

    companion object {
        @Volatile var controller: Controller? = null
    }
}
