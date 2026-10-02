package com.obs.droidcampro.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.obs.droidcampro.hardware.HdmiOutputManager
import com.obs.droidcampro.hardware.UsbWebcamManager
import com.obs.droidcampro.network.NetworkUtils
import com.obs.droidcampro.service.CameraStreamService

@Composable
fun CameraScreen(
    service: CameraStreamService?,
    isBound: Boolean,
    hdmiManager: HdmiOutputManager,
    usbManager: UsbWebcamManager,
    onToggleHdmiMode: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val ipAddress = remember { NetworkUtils.getLocalIpAddress(context) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }

    val isFrontCamera = service?.cameraController?.isFrontCamera?.collectAsState()?.value ?: false
    val isTorchOn = service?.cameraController?.isTorchOn?.collectAsState()?.value ?: false
    val zoomRatio = service?.cameraController?.zoomRatio?.collectAsState()?.value ?: 1.0f
    val currentResolution = service?.cameraController?.currentResolution?.collectAsState()?.value
    val targetFps = service?.cameraController?.targetFps?.collectAsState()?.value ?: 30
    val audioLevel = service?.audioStreamer?.audioLevel?.collectAsState()?.value ?: 0
    val connectedClients = service?.server?.getConnectedClients() ?: 0

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Native CameraX Viewfinder
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    previewView = this
                    service?.cameraController?.bindCamera(lifecycleOwner, surfaceProvider)
                }
            }
        )

        // Top Telemetry Bar (Professional Broadcast HUD)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color(0xCC111827), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LIVE IP: http://$ipAddress:8080/stream",
                    color = Color(0xFF10B981),
                    fontSize = 13.sp,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "OBS Browser Source: http://$ipAddress:8080/obs",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${currentResolution?.width ?: 1920}x${currentResolution?.height ?: 1080} @ ${targetFps}fps",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Text(
                    text = "Clients: $connectedClients",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp
                )
                Text(
                    text = "VU: $audioLevel%",
                    color = if (audioLevel > 80) Color.Red else Color.Green,
                    fontSize = 12.sp
                )
            }
        }

        // Right-Side Controls Palette
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp)
                .background(Color(0xBB1F2937), RoundedCornerShape(12.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = { service?.cameraController?.switchCamera(lifecycleOwner, previewView?.surfaceProvider) }) {
                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = Color.White)
            }

            IconButton(onClick = { service?.cameraController?.toggleTorch() }) {
                Icon(
                    if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Torch",
                    tint = if (isTorchOn) Color.Yellow else Color.White
                )
            }

            IconButton(onClick = { service?.audioStreamer?.toggleMute() }) {
                Icon(Icons.Default.Mic, contentDescription = "Mute Mic", tint = Color.White)
            }

            IconButton(onClick = onToggleHdmiMode) {
                Icon(Icons.Default.Tv, contentDescription = "HDMI Clean Feed", tint = Color(0xFF38BDF8))
            }
        }

        // Bottom Zoom Slider
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.6f)
                .padding(bottom = 20.dp)
                .background(Color(0xBB1F2937), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("1x", color = Color.White, fontSize = 11.sp)
            Slider(
                value = zoomRatio,
                onValueChange = { service?.cameraController?.setZoom(it) },
                valueRange = 1f..8f,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(String.format("%.1fx", zoomRatio), color = Color.White, fontSize = 11.sp)
        }
    }
}
