package com.obs.droidcampro

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.obs.droidcampro.hardware.HdmiOutputManager
import com.obs.droidcampro.hardware.UsbWebcamManager
import com.obs.droidcampro.service.CameraStreamService
import com.obs.droidcampro.ui.CameraScreen
import com.obs.droidcampro.ui.HdmiCleanScreen
import com.obs.droidcampro.ui.theme.DroidCamProTheme

class MainActivity : ComponentActivity() {

    private var streamService: CameraStreamService? = null
    private var isBound by mutableStateOf(false)
    private var isHdmiMode by mutableStateOf(false)

    private val hdmiManager by lazy { HdmiOutputManager(this) }
    private val usbManager by lazy { UsbWebcamManager(this) }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as CameraStreamService.LocalBinder
            streamService = localBinder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            streamService = null
            isBound = false
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        if (cameraGranted) {
            bindStreamService()
        } else {
            Toast.makeText(this, "Camera permission is required for streaming", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen awake while app is running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Check and request permissions
        checkAndRequestPermissions()

        setContent {
            DroidCamProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isHdmiMode) {
                        HdmiCleanScreen(
                            service = streamService,
                            onExitHdmiMode = { isHdmiMode = false }
                        )
                    } else {
                        CameraScreen(
                            service = streamService,
                            isBound = isBound,
                            hdmiManager = hdmiManager,
                            usbManager = usbManager,
                            onToggleHdmiMode = { isHdmiMode = !isHdmiMode }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            bindStreamService()
        } else {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun bindStreamService() {
        val intent = Intent(this, CameraStreamService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
