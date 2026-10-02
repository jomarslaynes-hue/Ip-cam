package com.obs.droidcampro.security

import android.content.Context
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.coroutines.flow.MutableStateFlow

class SecurityManager(private val context: Context) {

    val isAuthEnabled = MutableStateFlow(false)
    val authUsername = MutableStateFlow("admin")
    val authPassword = MutableStateFlow("obsstream")
    val accessToken = MutableStateFlow("droidcam_key_2026")
    val localSubnetOnly = MutableStateFlow(true)

    fun isAuthorized(call: ApplicationCall): Boolean {
        // If security is disabled, permit all requests on LAN
        if (!isAuthEnabled.value) return true

        // Check query parameter access token
        val tokenParam = call.request.queryParameters["token"]
        if (tokenParam != null && tokenParam == accessToken.value) {
            return true
        }

        // Check HTTP Basic Auth Header
        val authHeader = call.request.header("Authorization")
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            val base64 = authHeader.removePrefix("Basic ").trim()
            val decoded = String(android.util.Base64.decode(base64, android.util.Base64.NO_WRAP))
            val parts = decoded.split(":")
            if (parts.size == 2 && parts[0] == authUsername.value && parts[1] == authPassword.value) {
                return true
            }
        }

        return false
    }
}
