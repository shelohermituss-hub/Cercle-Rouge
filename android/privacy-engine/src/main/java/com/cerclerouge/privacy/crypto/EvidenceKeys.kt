package com.cerclerouge.privacy.crypto

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig

/**
 * Clés des preuves (S6.02, ADR-0007) : une clé de données `StreamingAead` (AES-256-GCM-HKDF, segments de 1 Mo) **par alerte**,
 * dont le keyset est chiffré par une clé maîtresse non exportable du Keystore. Détruire le keyset chiffré suffit à rendre
 * les fichiers illisibles (crypto-shredding). `master` est injectable pour les tests JVM.
 */
class EvidenceKeys(private val master: Aead) {
    init {
        StreamingAeadConfig.register()
    }

    /** Nouvelle clé d'alerte : la primitive à utiliser et son keyset chiffré à ranger en base. */
    fun create(alertId: String): Pair<StreamingAead, ByteArray> {
        val handle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
        val wrapped = TinkProtoKeysetFormat.serializeEncryptedKeyset(handle, master, aad(alertId))
        return handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java) to wrapped
    }

    fun open(alertId: String, wrapped: ByteArray): StreamingAead = TinkProtoKeysetFormat.parseEncryptedKeyset(wrapped, master, aad(alertId))
        .getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)

    private fun aad(alertId: String) = "cercle-rouge/evidence-key/v1/$alertId".toByteArray()

    companion object {
        /** Clé maîtresse dans l'Android Keystore (jamais exportable). */
        fun android(context: Context): EvidenceKeys {
            AeadConfig.register()
            val master = AndroidKeysetManager.Builder()
                .withSharedPref(context, "cr_evidence_keyset", "cr_keysets")
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri("android-keystore://cr_evidence_master")
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            return EvidenceKeys(master)
        }

        /** Pour les tests seulement : clé maîtresse en mémoire. */
        fun inMemory(): EvidenceKeys {
            AeadConfig.register()
            val master = KeysetHandle.generateNew(
                KeyTemplates.get("AES256_GCM")
            ).getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            return EvidenceKeys(master)
        }
    }
}
