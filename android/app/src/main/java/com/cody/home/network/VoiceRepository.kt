package com.cody.home.network

import java.io.File

/** Schlanke Orchestrierung über [VoiceClient] — die einzige Voice-Schnittstelle für UI/Audio. */
class VoiceRepository(private val credentialStore: CredentialStore) {

    private val client = VoiceClient()

    suspend fun sendVoice(audioFile: File): VoiceResult {
        val creds = credentialStore.load() ?: return VoiceResult.Failure("not_paired", 0)
        return client.sendVoice(creds, audioFile)
    }
}
