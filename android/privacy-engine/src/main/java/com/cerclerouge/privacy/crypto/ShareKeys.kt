package com.cerclerouge.privacy.crypto

import android.content.Context
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** Clé d'une alerte reçue d'une autre personne : son identifiant local (données associées des segments) et sa primitive. */
class SharedAlertKey(val localAlertId: String, val streaming: StreamingAead)

/**
 * Partage des preuves de bout en bout (ADR-0016, 1.16). Chaque app a une paire de clés **HPKE** (DHKEM P-256, HKDF-SHA256,
 * AES-256-GCM) ; la clé privée est dans un keyset chiffré par une clé maîtresse du Keystore, la clé publique est publiée.
 * La clé d'une alerte (keyset `StreamingAead`) est « enveloppée » pour chaque proche avec sa clé publique : seul son téléphone
 * peut l'ouvrir. Le contenu de l'enveloppe ne vit qu'en mémoire.
 */
class ShareKeys(private val handle: KeysetHandle) {
    init {
        HybridConfig.register()
        StreamingAeadConfig.register()
    }

    /** Clé publique à publier (keyset sans secret, Base64). */
    fun publicKey(): String =
        Base64.getEncoder().encodeToString(TinkProtoKeysetFormat.serializeKeysetWithoutSecret(handle.publicKeysetHandle))

    /** Enveloppe la clé d'une alerte (keyset en clair, en mémoire) pour le proche dont c'est la clé publique. */
    fun wrap(recipientPublicKey: String, localAlertId: String, alertKeyset: ByteArray): String {
        val recipient = TinkProtoKeysetFormat.parseKeysetWithoutSecret(Base64.getDecoder().decode(recipientPublicKey))
            .getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val payload = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).apply {
                writeUTF(localAlertId)
                writeInt(alertKeyset.size)
                write(alertKeyset)
            }
        }.toByteArray()
        val sealed = recipient.encrypt(payload, CONTEXT)
        payload.fill(0)
        return Base64.getEncoder().encodeToString(sealed)
    }

    /** Ouvre une enveloppe reçue. Null si elle n'était pas pour cette app (autre clé) ou si elle a été modifiée. */
    fun unwrap(wrapped: String): SharedAlertKey? = runCatching {
        val payload = handle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)
            .decrypt(Base64.getDecoder().decode(wrapped), CONTEXT)
        val input = DataInputStream(ByteArrayInputStream(payload))
        val id = input.readUTF()
        val size = input.readInt()
        require(size in 1..MAX_KEYSET)
        val keyset = ByteArray(size).also(input::readFully)
        payload.fill(0)
        val streaming = TinkProtoKeysetFormat.parseKeyset(keyset, InsecureSecretKeyAccess.get())
            .getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
        keyset.fill(0)
        SharedAlertKey(id, streaming)
    }.getOrNull()

    companion object {
        private val CONTEXT = "cercle-rouge/share/v1".toByteArray()
        private const val MAX_KEYSET = 4096
        private const val TEMPLATE = "DHKEM_P256_HKDF_SHA256_HKDF_SHA256_AES_256_GCM"

        /** Paire de l'app, créée au premier usage, clé privée chiffrée par le Keystore (jamais exportable). */
        fun android(context: Context): ShareKeys {
            HybridConfig.register()
            val handle = AndroidKeysetManager.Builder()
                .withSharedPref(context, "cr_share_keyset", "cr_keysets")
                .withKeyTemplate(KeyTemplates.get(TEMPLATE))
                .withMasterKeyUri("android-keystore://cr_share_master")
                .build()
                .keysetHandle
            return ShareKeys(handle)
        }

        /** Pour les tests seulement. */
        fun inMemory(): ShareKeys {
            HybridConfig.register()
            return ShareKeys(KeysetHandle.generateNew(KeyTemplates.get(TEMPLATE)))
        }
    }
}
