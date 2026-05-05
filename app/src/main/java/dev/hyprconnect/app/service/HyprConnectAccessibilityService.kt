package dev.hyprconnect.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Minimal accessibility service whose sole purpose is to grant the app process
 * elevated clipboard access on Android 10+.
 *
 * When an AccessibilityService is enabled, Android's ClipboardService grants the
 * app's UID permission to read clipboard in the background — the same mechanism
 * used by password managers and Samsung Keyboard. No screen content is read here.
 *
 * User setup: Settings → Accessibility → Installed services → HyprConnect → Enable
 */
class HyprConnectAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
