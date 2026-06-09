package com.example.security

import android.app.Activity
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.widget.Toast
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

object SecurityHelper {

    private const val DB_KEYSTORE_ALIAS = "dompetku_db_key"
    private const val DB_SALT_PREF = "dompetku_db_salt_v1"

    /**
     * Hashes standard user PIN strings using securely cryptographic SHA-256 digests.
     */
    fun hashPin(pin: String): String {
        val bytes = pin.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns a random 32-byte SQLCipher master passphrase.
     *
     * The passphrase itself is never persisted. What is persisted is a random
     * 32-byte salt stored in app-private SharedPreferences, and the passphrase is
     * re-derived on every launch as HMAC-SHA256(salt, hardware-backed 256-bit key)
     * where the key lives in the Android Keystore and is non-exportable.
     *
     * The salt is available at Application.onCreate time (before any UI), so there
     * is no bootstrapping dependency on the user's PIN.
     */
    fun deriveDatabasePassphrase(context: android.content.Context): ByteArray {
        val prefs = context.getSharedPreferences("dompetku_secure_prefs", android.content.Context.MODE_PRIVATE)
        val salt = prefs.getString(DB_SALT_PREF, null)?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }
            ?: ByteArray(32).also { fresh ->
                SecureRandom().nextBytes(fresh)
                prefs.edit()
                    .putString(DB_SALT_PREF, android.util.Base64.encodeToString(fresh, android.util.Base64.NO_WRAP))
                    .apply()
            }

        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secretKey: SecretKey = (keystore.getEntry(DB_KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(
                        DB_KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    ).setKeySize(256).build()
                )
            }.generateKey()

        return Mac.getInstance("HmacSHA256").apply { init(secretKey) }.doFinal(salt)
    }

    /**
     * Standard native Biometric Prompt implementation supporting API 28+ directly on Activity
     * context without forcing FragmentActivity upgrades.
     */
    fun authenticateBiometrics(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val executor = activity.mainExecutor
            try {
                val biometricPrompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                    .setTitle(activity.getString(com.example.R.string.security_biometric_prompt_title))
                    .setSubtitle(activity.getString(com.example.R.string.security_biometric_prompt_subtitle))
                    .setDescription(activity.getString(com.example.R.string.security_biometric_prompt_description))
                    .setNegativeButton(
                        activity.getString(android.R.string.cancel),
                        executor
                    ) { _, _ ->
                        onFailure("Cancelled")
                    }
                    .build()

                val cancellationSignal = CancellationSignal()
                biometricPrompt.authenticate(
                    cancellationSignal,
                    executor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            activity.runOnUiThread { onSuccess() }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            activity.runOnUiThread { onFailure("Failed") }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            activity.runOnUiThread { onFailure(errString?.toString() ?: "Error") }
                        }
                    }
                )
            } catch (e: Exception) {
                onFailure(e.localizedMessage ?: "Biometric error")
            }
        } else {
            val msg = if (com.example.domain.utils.LocalizationUtils.activeLanguage == "en") {
                "Biometrics not supported on this Android version"
            } else {
                "Biometrik tidak didukung pada versi Android ini"
            }
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            onFailure("Not supported")
        }
    }
}
