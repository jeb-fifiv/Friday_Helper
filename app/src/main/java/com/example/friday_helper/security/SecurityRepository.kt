package com.example.friday_helper.security

import android.content.Context
import androidx.core.content.edit
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SecurityRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPinEnabled(): Boolean = prefs.getBoolean(KEY_PIN_ENABLED, false) && hasPin()
    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    fun isGuestModeEnabled(): Boolean = prefs.getBoolean(KEY_GUEST_ENABLED, false)
    fun isNotesEncryptionEnabled(): Boolean = prefs.getBoolean(KEY_NOTES_ENCRYPTION_ENABLED, false)
    fun isNotesProtectionEnabled(): Boolean = prefs.getBoolean(KEY_NOTES_PROTECTION_ENABLED, true)

    fun setGuestModeEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_GUEST_ENABLED, enabled) }
    fun setNotesEncryptionEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_NOTES_ENCRYPTION_ENABLED, enabled) }
    fun setNotesProtectionEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_NOTES_PROTECTION_ENABLED, enabled) }

    fun setBiometricEnabled(enabled: Boolean) = prefs.edit {
        putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
    }

    fun setPinEnabled(enabled: Boolean) = prefs.edit {
        putBoolean(KEY_PIN_ENABLED, enabled)
        if (!enabled) {
            // if pin is disabled, biometric shouldn't be used
            putBoolean(KEY_BIOMETRIC_ENABLED, false)
        }
    }

    fun hasPin(): Boolean = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank() && !prefs.getString(KEY_PIN_SALT, null).isNullOrBlank()

    fun clearPin() {
        prefs.edit {
            remove(KEY_PIN_HASH)
            remove(KEY_PIN_SALT)
            putBoolean(KEY_PIN_ENABLED, false)
            putBoolean(KEY_BIOMETRIC_ENABLED, false)
        }
    }

    fun setPin(pin: String) {
        val p = pin.trim()
        require(p.length in 4..12 && p.all { it.isDigit() }) { "PIN format" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(p, salt)
        prefs.edit {
            putString(KEY_PIN_SALT, salt.toHex())
            putString(KEY_PIN_HASH, hash.toHex())
            putBoolean(KEY_PIN_ENABLED, true)
        }
    }

    fun verifyPin(pin: String): Boolean {
        val saltHex = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashHex = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = saltHex.fromHex() ?: return false
        val expected = hashHex.fromHex() ?: return false
        val actual = hashPin(pin.trim(), salt)
        return constantTimeEquals(expected, actual)
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray? {
        val s = trim()
        if (s.length % 2 != 0) return null
        return try {
            ByteArray(s.length / 2) { i ->
                s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS = "security_prefs"
        private const val KEY_PIN_ENABLED = "pin_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_GUEST_ENABLED = "guest_enabled"
        private const val KEY_NOTES_ENCRYPTION_ENABLED = "notes_encryption_enabled"
        private const val KEY_NOTES_PROTECTION_ENABLED = "notes_protection_enabled"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
    }
}

