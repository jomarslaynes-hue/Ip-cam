package com.obs.droidcampro.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.obs.droidcampro.camera.CameraController
import com.obs.droidcampro.security.SecurityManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class HttpStreamServer(
    private val context: Context,
    private val cameraController: CameraController,
    private val securityManager: SecurityManager,
    private val port: Int = 8080
) {
    private val TAG = "HttpStreamServer"
    private var serverEngine: ApplicationEngine? = null
    private val activeClients = AtomicInteger(0)
    private val gson = Gson()

    // Multi-cast channel for MJPEG listeners
    private val streamSubscribers = ConcurrentHashMap<String, OutputStream>()

    fun start() {
        if (serverEngine != null) return

        serverEngine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
            }
            install(WebSockets)

            routing {
                // Security check filter
                intercept(ApplicationCallPipeline.Plugins) {
                    if (!securityManager.isAuthorized(call)) {
                        call.respond(HttpStatusCode.Unauthorized, "Access Denied: Authentication Required")
                        finish()
                    }
                }

                // Root: Clean OBS Browser Source View
                get("/") {
                    val obsHtml = loadAssetString("web/obs.html")
                    call.respondText(obsHtml, ContentType.Text.Html)
                }

                // Dedicated OBS Route
                get("/obs") {
                    val obsHtml = loadAssetString("web/obs.html")
                    call.respondText(obsHtml, ContentType.Text.Html)
                }

                // Responsive Remote Browser Control Interface
                get("/control") {
                    val controlHtml = loadAssetString("web/control.html")
                    call.respondText(controlHtml, ContentType.Text.Html)
                }

                // Serve Web Assets (CSS / JS)
                get("/web/{asset}") {
                    val asset = call.parameters["asset"] ?: ""
                    val content = loadAssetString("web/$asset")
                    val contentType = when {
                        asset.endsWith(".css") -> ContentType.Text.CSS
                        asset.endsWith(".js") -> ContentType.Application.JavaScript
                        else -> ContentType.Text.Plain
                    }
                    call.respondText(content, contentType)
                }

                // MJPEG Live Stream Endpoint for OBS Studio Media Source or Browser Source
                get("/stream") {
                    activeClients.incrementAndGet()
                    val boundary = "boundary_droidcam_frame"

                    call.response.headers.append(
                        HttpHeaders.ContentType,
                        "multipart/x-mixed-replace; boundary=$boundary"
                    )
                    call.response.headers.append(HttpHeaders.CacheControl, "no-cache, no-store, must-revalidate")
                    call.response.headers.append("Connection", "close")

                    call.respondOutputStream(ContentType.parse("multipart/x-mixed-replace; boundary=$boundary")) {
                        val clientId = java.util.UUID.randomUUID().toString()
                        streamSubscribers[clientId] = this

                        try {
                            // Hold connection open while pushing frames
                            while (true) {
                                delay(1000)
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Stream client disconnected: $clientId")
                        } finally {
                            streamSubscribers.remove(clientId)
                            activeClients.decrementAndGet()
                        }
                    }
                }

                // Single Snapshot API
                get("/snapshot") {
                    latestJpegFrame?.let {
                        call.respondBytes(it, ContentType.Image.JPEG)
                    } ?: call.respond(HttpStatusCode.ServiceUnavailable, "No frame available")
                }

                // REST API - Get Camera Telemetry & Status
                get("/api/status") {
                    val status = mapOf(
                        "status" to "streaming",
                        "resolution" to "${cameraController.currentResolution.value.width}x${cameraController.currentResolution.value.height}",
                        "fps" to cameraController.targetFps.value,
                        "zoom" to cameraController.zoomRatio.value,
                        "torch" to cameraController.isTorchOn.value,
                        "facing" to if (cameraController.isFrontCamera.value) "front" else "back",
                        "quality" to cameraController.compressionQuality.value,
                        "connectedClients" to activeClients.get()
                    )
                    call.respondText(gson.toJson(status), ContentType.Application.Json)
                }

                // REST API - Camera Remote Control Command
                post("/api/control") {
                    val params = call.request.queryParameters
                    params["zoom"]?.toFloatOrNull()?.let { cameraController.setZoom(it) }
                    params["torch"]?.toBooleanStrictOrNull()?.let {
                        if (it != cameraController.isTorchOn.value) cameraController.toggleTorch()
                    }
                    params["exposure"]?.toIntOrNull()?.let { cameraController.setExposure(it) }
                    params["quality"]?.toIntOrNull()?.let { cameraController.compressionQuality.value = it }
                    params["fps"]?.toIntOrNull()?.let { cameraController.setFps(it) }

                    call.respondText("{\"status\":\"ok\"}", ContentType.Application.Json)
                }

                // WebSocket endpoint for low-latency bidirectional telemetry and WebRTC signaling
                webSocket("/ws") {
                    activeClients.incrementAndGet()
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                handleWebSocketMessage(text)
                            }
                        }
                    } finally {
                        activeClients.decrementAndGet()
                    }
                }
            }
        }.start(wait = false)

        // Hook up camera frames to MJPEG subscribers
        cameraController.onFrameAvailable = { jpeg ->
            latestJpegFrame = jpeg
            broadcastFrame(jpeg)
        }
    }

    @Volatile
    private var latestJpegFrame: ByteArray? = null

    private fun broadcastFrame(jpeg: ByteArray) {
        val boundary = "boundary_droidcam_frame"
        val header = "\r\n--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n".toByteArray()

        streamSubscribers.forEach { (_, outputStream) ->
            try {
                outputStream.write(header)
                outputStream.write(jpeg)
                outputStream.write("\r\n".toByteArray())
                outputStream.flush()
            } catch (e: Exception) {
                // Client dropped
            }
        }
    }

    private fun handleWebSocketMessage(message: String) {
        try {
            val cmd = gson.fromJson(message, Map::class.java)
            val action = cmd["action"] as? String ?: return
            when (action) {
                "setZoom" -> (cmd["value"] as? Number)?.toFloat()?.let { cameraController.setZoom(it) }
                "toggleTorch" -> cameraController.toggleTorch()
                "setQuality" -> (cmd["value"] as? Number)?.toInt()?.let { cameraController.compressionQuality.value = it }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WS parse error", e)
        }
    }

    private fun loadAssetString(path: String): String {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<!-- Asset not found: $path -->"
        }
    }

    fun getConnectedClients(): Int = activeClients.get()

    fun stop() {
        serverEngine?.stop(1000, 2000)
        serverEngine = null
    }
}
