package com.cody.home.network

import java.io.File

/** Thin orchestration over [VoiceClient] — the only thing UI/audio code talks to for voice. */
class VoiceRepository(private val credentialStore: CredentialStore) {

    private val client = VoiceClient()

    suspend fun sendVoice(audioFile: File): VoiceResult {
        val creds = credentialStore.load() ?: return VoiceResult.Failure("not_paired", 0)
        return client.sendVoice(creds, audioFile)
    }
}
