package com.cody.home.network

import com.cody.home.BuildConfig

/**
 * Endpunkt-Konstanten für das Cody Home Gateway.
 *
 * HTTPS_BASE/WSS_URL kommen aus BuildConfig, nicht aus festem Code. Trage deine
 * Gateway-URLs in gradle.properties ein. Ohne lokale gradle.properties baut die
 * App weiterhin mit offensichtlichen Platzhalter-Domains.
 *
 * Zwei Pfadbegriffe sind wichtig:
 *   - öffentlicher Pfad über Reverse Proxy, mit /cody-home-Präfix
 *   - signierter interner Pfad, den das Gateway selbst sieht, ohne Präfix.
 *     Dieser Pfad geht in den HMAC-Canonical-String.
 */
object GatewayConfig {
    val HTTPS_BASE = BuildConfig.GATEWAY_HTTPS_URL
    val WSS_URL = BuildConfig.GATEWAY_WSS_URL

    // Signierte interne Pfade — nur für den HMAC-Canonical-String.
    const val SIGNED_PATH_MESSAGE = "/message"
    const val SIGNED_PATH_WS = "/ws"
    const val SIGNED_PATH_VOICE = "/voice"

    const val CLOCK_SKEW_TOLERANCE_SECONDS = 30
    const val VOICE_MAX_UPLOAD_BYTES = 15 * 1024 * 1024 // 15 MiB gemäß /voice-Vertrag
}
