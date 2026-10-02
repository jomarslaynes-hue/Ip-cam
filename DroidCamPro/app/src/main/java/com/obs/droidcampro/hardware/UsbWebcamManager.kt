package com.obs.droidcampro.hardware

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class UsbWebcamManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    val isAndroid14UvcSupported = MutableStateFlow(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    val hasKernelUvcGadget = MutableStateFlow(checkKernelUvcGadget())
    val recommendedWorkflow = MutableStateFlow(getWorkflowRecommendation())

    /**
     * Checks for Linux kernel configfs UVC gadget support at /config/usb_gadget or /sys/class/android_usb
     */
    private fun checkKernelUvcGadget(): Boolean {
        return try {
            val gadgetDir = File("/config/usb_gadget")
            val legacyDir = File("/sys/class/android_usb/android0")
            gadgetDir.exists() || legacyDir.exists()
        } catch (e: Exception) {
            false
        }
    }

    private fun getWorkflowRecommendation(): String {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                "Android 14+ detected! Your device natively supports the USB Webcam standard. In Android Settings -> Connected Devices -> USB, select 'Webcam'."
            }
            checkKernelUvcGadget() -> {
                "Kernel UVC gadget detected. Direct USB UVC is possible with Root or vendor firmware."
            }
            else -> {
                "Native UVC Webcam is unavailable on this device version. Recommended solution: Use the high-performance local IP Camera stream over USB Tethering or Wi-Fi (http://PHONE-IP:8080/stream) with zero extra drivers needed!"
            }
        }
    }
}
