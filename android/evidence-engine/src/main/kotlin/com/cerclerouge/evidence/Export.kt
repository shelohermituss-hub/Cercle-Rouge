package com.cerclerouge.evidence

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Contenu d'un export : la chaîne et les segments en clair (seulement dans l'archive chiffrée, jamais sur disque). */
class ExportBundle(val alertId: String, val entries: List<SegmentEntry>, val segments: List<ByteArray>)

/**
 * Export chiffré (S6.09) pour un avocat ou la police : archive AES-256-GCM, clé dérivée (PBKDF2-HMAC-SHA256, 210 000 tours)
 * d'un **code à usage unique** de 12 caractères, communiqué séparément (oralement ou par SMS). L'archive contient la chaîne
 * d'intégrité : [verify] la recontrôle (R-EE-03). Format : « CRX1 » ‖ sel (16) ‖ tours (4) ‖ nonce (12) ‖ chiffré.
 */
object EvidenceExport {
    private val MAGIC = "CRX1".toByteArray()
    private const val ITERATIONS = 210_000

    /** Sans 0/O, 1/I/L : lisible au téléphone. 12 caractères parmi 31 ≈ 59 bits. */
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private val random = SecureRandom()

    fun newCode(): String = (1..12).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** Présentation par groupes de 4 : « ABCD-EFGH-JKMN ». */
    fun formatCode(code: String): String = code.chunked(4).joinToString("-")

    fun seal(bundle: ExportBundle, code: String): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(normalize(code), salt, ITERATIONS), GCMParameterSpec(128, nonce))
        cipher.updateAAD(MAGIC)
        val sealed = cipher.doFinal(serialize(bundle))
        return ByteArrayOutputStream().apply {
            write(MAGIC)
            write(salt)
            DataOutputStream(this).writeInt(ITERATIONS)
            write(nonce)
            write(sealed)
        }.toByteArray()
    }

    /** Ouvre une archive ; null si le code est faux ou l'archive altérée (GCM authentifie tout). */
    fun open(archive: ByteArray, code: String): ExportBundle? = runCatching {
        val input = DataInputStream(ByteArrayInputStream(archive))
        val magic = ByteArray(4).also(input::readFully)
        require(magic.contentEquals(MAGIC))
        val salt = ByteArray(16).also(input::readFully)
        val iterations = input.readInt()
        require(iterations in 100_000..10_000_000)
        val nonce = ByteArray(12).also(input::readFully)
        val sealed = input.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(normalize(code), salt, iterations), GCMParameterSpec(128, nonce))
        cipher.updateAAD(MAGIC)
        deserialize(cipher.doFinal(sealed))
    }.getOrNull()

    fun verify(bundle: ExportBundle): ChainCheck = Chain.verify(bundle.entries, bundle.segments)

    private fun normalize(code: String) = code.uppercase().filter { it in ALPHABET }

    private fun key(code: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(code.toCharArray(), salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun serialize(b: ExportBundle): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).apply {
            writeUTF(b.alertId)
            writeInt(b.entries.size)
            b.entries.zip(b.segments).forEach { (e, data) ->
                writeInt(e.seq)
                writeLong(e.startedAt.toEpochMilli())
                writeLong(e.durationMs)
                writeUTF(e.sha256)
                writeUTF(e.prevHash)
                writeUTF(e.entryHash)
                writeInt(data.size)
                write(data)
            }
        }
    }.toByteArray()

    private fun deserialize(bytes: ByteArray): ExportBundle {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val alertId = input.readUTF()
        val n = input.readInt()
        require(n in 0..RecordingPolicy.maxSegments() + 1)
        val entries = ArrayList<SegmentEntry>(n)
        val segments = ArrayList<ByteArray>(n)
        repeat(n) {
            val seq = input.readInt()
            val started = Instant.ofEpochMilli(input.readLong())
            val duration = input.readLong()
            entries += SegmentEntry(alertId, seq, started, duration, input.readUTF(), input.readUTF(), input.readUTF())
            val size = input.readInt()
            require(size in 0..MAX_SEGMENT_BYTES)
            segments += ByteArray(size).also(input::readFully)
        }
        return ExportBundle(alertId, entries, segments)
    }

    private const val MAX_SEGMENT_BYTES = 8 * 1024 * 1024
}

/**
 * Outil de vérification hors ligne (R-EE-03), pour un avocat ou la police :
 * `java -cp evidence-engine.jar com.cerclerouge.evidence.VerifyTool archive.crx CODE [dossier]`.
 * Affiche la chaîne et le verdict ; avec un dossier, y écrit les segments audio (AAC/ADTS) **sur l'ordinateur du vérificateur**.
 */
object VerifyTool {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 2) {
            println("Usage : VerifyTool <archive.crx> <code> [dossier de sortie]")
            return
        }
        val bundle = EvidenceExport.open(File(args[0]).readBytes(), args[1])
        if (bundle == null) {
            println("Impossible d'ouvrir l'archive : code faux ou archive modifiée.")
            return
        }
        println("Alerte ${bundle.alertId} : ${bundle.entries.size} segment(s)")
        bundle.entries.forEach { println("  #${it.seq} ${it.startedAt} ${it.durationMs / 1000} s sha256=${it.sha256}") }
        println("Racine de la chaîne : ${Chain.root(bundle.entries)}")
        val check = EvidenceExport.verify(bundle)
        println(if (check.ok) "Chaîne intacte." else "ALTÉRATION au segment #${check.firstBad} : ${check.reason}")
        args.getOrNull(2)?.let { dir ->
            File(dir).mkdirs()
            bundle.segments.forEachIndexed { i, data -> File(dir, "segment-%03d.aac".format(i)).writeBytes(data) }
            println("Segments écrits dans $dir")
        }
    }
}
