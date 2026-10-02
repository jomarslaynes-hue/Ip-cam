package com.obs.droidcampro.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.obs.droidcampro.service.CameraStreamService
import kotlinx.coroutines.delay

@Composable
fun HdmiCleanScreen(
    service: CameraStreamService?,
    onExitHdmiMode: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var showHint by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(3500)
        showHint = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onExitHdmiMode() }
    ) {
        // Pure unadulterated camera viewfinder - zero overlays for HDMI capture card
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    service?.cameraController?.bindCamera(lifecycleOwner, surfaceProvider)
                }
            }
        )

        // Temporary hint fading after 3.5s
        if (showHint) {
            Text(
                text = "HDMI Clean Feed Active • Tap anywhere to exit",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
}
