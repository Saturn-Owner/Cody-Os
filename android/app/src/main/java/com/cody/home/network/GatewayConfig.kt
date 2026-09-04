package com.cody.home.network

import com.cody.home.BuildConfig

/**
 * Endpoint constants for the Cody Home Gateway.
 *
 * HTTPS_BASE/WSS_URL come from BuildConfig, not a literal here — set your
 * own Gateway's URLs in gradle.properties (copy gradle.properties.example
 * and fill in CODY_HOME_HTTPS_URL / CODY_HOME_WSS_URL). Without that file
 * the build still succeeds, just pointed at an obviously-fake placeholder
 * domain — see app/build.gradle.kts for the defaults.
 *
 * Two path notions matter and must never be confused:
 *   - the *public* path, reached through Caddy, prefixed with /cody-home
 *   - the *signed* path, what the Gateway itself sees internally (no prefix) —
 *     this is what goes into the HMAC canonical string, per the 2026-09-03
 *     auth handoff. Signing the public path would produce a signature the
 *     server rejects.
 */
object GatewayConfig {
    val HTTPS_BASE = BuildConfig.GATEWAY_HTTPS_URL
    val WSS_URL = BuildConfig.GATEWAY_WSS_URL

    // Signed (internal) paths — used only inside the HMAC canonical string.
    const val SIGNED_PATH_MESSAGE = "/message"
    const val SIGNED_PATH_WS = "/ws"
    const val SIGNED_PATH_VOICE = "/voice"

    const val CLOCK_SKEW_TOLERANCE_SECONDS = 30
    const val VOICE_MAX_UPLOAD_BYTES = 15 * 1024 * 1024 // 15 MiB, per the /voice contract
}
