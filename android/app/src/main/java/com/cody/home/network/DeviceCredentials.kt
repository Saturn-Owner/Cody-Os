package com.cody.home.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

data class DeviceCredentials(
    val deviceId: String,
    val deviceSecret: String,
    val protocolVersion: Int,
)

/**
 * Speichert die Zugangsdaten des gepairten Geräts in EncryptedSharedPreferences
 * (AES256-GCM, Schlüsselmaterial im Android Keystore) — niemals in normalen
 * SharedPreferences und niemals in Logs.
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "cody_home_device_credentials",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): DeviceCredentials? {
        val id = prefs.getString(KEY_DEVICE_ID, null) ?: return null
        val secret = prefs.getString(KEY_DEVICE_SECRET, null) ?: return null
        val version = prefs.getInt(KEY_PROTOCOL_VERSION, 1)
        return DeviceCredentials(id, secret, version)
    }

    fun save(credentials: DeviceCredentials) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, credentials.deviceId)
            .putString(KEY_DEVICE_SECRET, credentials.deviceSecret)
            .putInt(KEY_PROTOCOL_VERSION, credentials.protocolVersion)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_SECRET = "device_secret"
        const val KEY_PROTOCOL_VERSION = "protocol_version"
    }
}
