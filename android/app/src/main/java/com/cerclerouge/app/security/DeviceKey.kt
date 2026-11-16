package com.cerclerouge.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clé d'appareil `K_device` (ADR-0016, 1.15) : paire EC P-256 créée dans le Keystore Android, **non exportable**. Elle signe la
 * racine de la chaîne d'intégrité des preuves avant son dépôt au serveur : la racine horodatée par le serveur et signée par le
 * téléphone montre que les preuves n'ont pas été changées après coup. Servira aussi à signer la clé publique de partage (1.16).
 */
@Singleton
class DeviceKey @Inject constructor() {
    private fun keyStore() = KeyStore.getInstance(STORE).apply { load(null) }

    private fun ensure() {
        if (keyStore().containsAlias(ALIAS)) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, STORE).apply {
            initialize(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
        }.generateKeyPair()
    }

    /** Signature ECDSA-SHA256 (DER, Base64). */
    fun sign(data: ByteArray): String {
        ensure()
        val key = keyStore().getKey(ALIAS, null) as PrivateKey
        val sig = Signature.getInstance("SHA256withECDSA").apply {
            initSign(key)
            update(data)
        }.sign()
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    /** Clé publique (X.509 SubjectPublicKeyInfo, Base64), pour vérifier les signatures hors du téléphone. */
    fun publicKey(): String {
        ensure()
        return Base64.encodeToString(keyStore().getCertificate(ALIAS).publicKey.encoded, Base64.NO_WRAP)
    }

    private companion object {
        const val STORE = "AndroidKeyStore"
        const val ALIAS = "cr_device_sign"
    }
}
