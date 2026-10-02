package com.obs.droidcampro.hardware

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow

class HdmiOutputManager(private val context: Context) {

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    val isExternalDisplayConnected = MutableStateFlow(false)
    val externalDisplayName = MutableStateFlow<String?>(null)
    val hasHardwareDpAltMode = MutableStateFlow(checkDeviceSupport())

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            checkConnectedDisplays()
        }

        override fun onDisplayRemoved(displayId: Int) {
            checkConnectedDisplays()
        }

        override fun onDisplayChanged(displayId: Int) {
            checkConnectedDisplays()
        }
    }

    init {
        displayManager.registerDisplayListener(displayListener, null)
        checkConnectedDisplays()
    }

    private fun checkConnectedDisplays() {
        val displays = displayManager.displays
        val external = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

        if (external != null) {
            isExternalDisplayConnected.value = true
            externalDisplayName.value = external.name
        } else {
            isExternalDisplayConnected.value = false
            externalDisplayName.value = null
        }
    }

    /**
     * Checks if this Android device hardware supports USB-C DisplayPort Alt Mode.
     * Devices like Samsung Galaxy S8+, Note 8+, S20-S24, Pixel 8/8 Pro/9 support DP Alt mode.
     * Most budget phones (USB 2.0 type C) do not possess the required hardware muxer pins.
     */
    private fun checkDeviceSupport(): Boolean {
        val model = Build.MODEL.uppercase()
        val manufacturer = Build.MANUFACTURER.uppercase()

        // Samsung flagship line with DeX / DP Alt Mode
        if (manufacturer.contains("SAMSUNG")) {
            if (model.contains("SM-S") || model.contains("SM-G9") || model.contains("SM-N9")) {
                return true
            }
        }

        // Google Pixel 8, 8 Pro, 8a, 9 series (enabled via kernel/firmware)
        if (manufacturer.contains("GOOGLE")) {
            if (model.contains("PIXEL 8") || model.contains("PIXEL 9")) {
                return true
            }
        }

        return false
    }
}
