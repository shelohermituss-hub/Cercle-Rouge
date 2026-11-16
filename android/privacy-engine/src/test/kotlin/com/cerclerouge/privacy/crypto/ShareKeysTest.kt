package com.cerclerouge.privacy.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 1.16 (ADR-0016) : seule l'app du proche destinataire peut ouvrir la clé d'une alerte et lire ses segments. */
class ShareKeysTest {
    private val owner = EvidenceKeys.inMemory()
    private val contact = ShareKeys.inMemory()
    private val stranger = ShareKeys.inMemory()

    private fun encryptSegment(alertId: String, seq: Int, content: ByteArray, wrappedKey: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        owner.open(alertId, wrappedKey).newEncryptingStream(out, "cercle-rouge/evidence/v1/$alertId/$seq".toByteArray()).use {
            it.write(content)
        }
        return out.toByteArray()
    }

    @Test fun contactReadsTheSegmentStrangerCannot() {
        val (_, wrappedKey) = owner.create("alert-1")
        val segment = encryptSegment("alert-1", 0, "VOIX".toByteArray(), wrappedKey)
        val envelope = contact.publicKey().let { pk ->
            val keyset = owner.exportForSharing("alert-1", wrappedKey)
            ShareKeys.inMemory().wrap(pk, "alert-1", keyset).also { keyset.fill(0) }
        }
        val opened = assertNotNull(contact.unwrap(envelope))
        assertEquals("alert-1", opened.localAlertId)
        val plain = opened.streaming.newDecryptingStream(ByteArrayInputStream(segment), "cercle-rouge/evidence/v1/alert-1/0".toByteArray())
            .use { it.readBytes() }
        assertContentEquals("VOIX".toByteArray(), plain)
        assertNull(stranger.unwrap(envelope)) // une autre app ne l'ouvre pas
    }

    @Test fun tamperedEnvelopeIsRejected() {
        val (_, wrappedKey) = owner.create("alert-2")
        val envelope = contact.wrap(contact.publicKey(), "alert-2", owner.exportForSharing("alert-2", wrappedKey))
        val bytes = java.util.Base64.getDecoder().decode(envelope).also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() }
        assertNull(contact.unwrap(java.util.Base64.getEncoder().encodeToString(bytes)))
    }

    @Test fun publishedKeyHoldsNoSecret() {
        val pk = java.util.Base64.getDecoder().decode(contact.publicKey())
        val other = ShareKeys.inMemory()
        // Avec la seule clé publique, on peut chiffrer pour le contact mais pas ouvrir : seule sa clé privée ouvre.
        val envelope = other.wrap(java.util.Base64.getEncoder().encodeToString(pk), "a", byteArrayOf(1, 2, 3))
        assertNull(other.unwrap(envelope))
    }
}
