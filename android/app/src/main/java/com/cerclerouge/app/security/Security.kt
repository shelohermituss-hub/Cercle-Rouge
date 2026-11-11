package com.cerclerouge.app.security

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Réglages de sécurité (S7.02), dans la base chiffrée.
 * - `biometric` : empreinte ou visage (classe Strong) à la place du PIN — désactivé par défaut.
 * - `biometricForAlerts` : aussi pour annuler une alerte. Désactivé par défaut : un agresseur peut forcer un doigt
 *   ou un visage plus facilement qu'un code (ADR-0023).
 */
@Singleton
class SecuritySettings @Inject constructor(db: PrivacyDatabase) {
    private val settings = db.settings()

    suspend fun biometric(): Boolean = settings.get(BIO) == "on"

    suspend fun biometricForAlerts(): Boolean = biometric() && settings.get(BIO_ALERTS) == "on"

    suspend fun setBiometric(on: Boolean) = settings.put(SettingEntity(BIO, if (on) "on" else "off"))

    suspend fun setBiometricForAlerts(on: Boolean) = settings.put(SettingEntity(BIO_ALERTS, if (on) "on" else "off"))

    private companion object {
        const val BIO = "security.biometric"
        const val BIO_ALERTS = "security.biometric.alerts"
    }
}

object Biometric {
    /** Vrai si le téléphone a une biométrie forte enregistrée. */
    fun available(activity: Activity?): Boolean = activity != null &&
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Demande l'empreinte ou le visage. Vrai en cas de succès ; faux si refusé, annulé ou indisponible. */
    suspend fun prompt(activity: Activity?, title: String, cancel: String): Boolean {
        val host = activity as? FragmentActivity ?: return false
        if (!available(host)) return false
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                host,
                ContextCompat.getMainExecutor(host),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(false)
                    }
                }
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setNegativeButtonText(cancel)
                    .setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .build()
            )
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }
}

/** Écran sensible (alerte, coffre, réglages) : invisible aux captures d'écran et dans les apps récentes (S7.04). */
@Composable
fun SecureScreen() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
