package com.cerclerouge.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S5.11, règle absolue n° 2 : **aucun audio brut ne quitte le téléphone**. Vérifié sur le code à chaque build :
 * 1. le code qui touche au micro (capture, PCM, encodeur) n'a aucun accès au réseau ;
 * 2. le module `audio-engine` n'a aucune dépendance réseau ;
 * 3. les seuls octets envoyés au serveur par les preuves sont les fichiers **déjà chiffrés** (segments, métadonnées).
 * Complément sur téléphone (📱) : capture réseau et fichiers pendant 10 min d'écoute → 0 octet audio.
 */
class NoAudioLeavesTest {
    private val app = File("src/main/java/com/cerclerouge/app")
    private val audioEngine = File("../audio-engine/src/main/kotlin")
    private val network = Regex("""java\.net\.|HttpURLConnection|SupabaseApi|ServerSync|okhttp|\bURL\(""")
    private val microphone = Regex("""\bAudioRecord\b|startRecording\s*\(|MediaCodec\b""")

    private fun kotlinFiles(dir: File) = dir.walkTopDown().filter { it.extension == "kt" }.toList()

    @Test fun codeThatTouchesTheMicrophoneHasNoNetworkAccess() {
        val offenders = kotlinFiles(app).filter { microphone.containsMatchIn(it.readText()) && network.containsMatchIn(it.readText()) }
        assertTrue(kotlinFiles(app).any { microphone.containsMatchIn(it.readText()) }, "le test ne trouve plus le code du micro")
        assertEquals(emptyList(), offenders.map { it.name })
    }

    @Test fun audioEngineHasNoNetworkAtAll() {
        val offenders = kotlinFiles(audioEngine).filter { network.containsMatchIn(it.readText()) }
        assertEquals(emptyList(), offenders.map { it.name })
        val deps = File("../audio-engine/build.gradle.kts").readText()
        assertTrue("okhttp" !in deps && "ktor" !in deps && "retrofit" !in deps, deps)
    }

    @Test fun onlyEncryptedEvidenceFilesAreUploaded() {
        val uploader = File(app, "evidence/EvidenceUploader.kt").readText()
        val uploads = Regex("""api\.upload\([^)]*\)""").findAll(uploader).map { it.value }.toList()
        assertTrue(uploads.isNotEmpty())
        // Chaque envoi passe des octets lus par encryptedSegment / encryptedNotes (fichiers chiffrés tels quels sur le disque).
        assertTrue(
            Regex("""store\.encryptedSegment\(""").containsMatchIn(uploader) &&
                Regex("""store\.encryptedNotes\(""").containsMatchIn(uploader)
        )
        assertTrue(
            "store.read(" !in uploader && "decryptAll" !in uploader && "notes(" !in uploader.replace("encryptedNotes(", ""),
            "l'envoi ne doit jamais lire de contenu déchiffré"
        )
        // Aucun autre fichier n'envoie d'objets au stockage.
        val others = kotlinFiles(app).filter {
            it.name != "EvidenceUploader.kt" &&
                it.name != "SupabaseApi.kt" &&
                "api.upload(" in it.readText()
        }
        assertEquals(emptyList(), others.map { it.name })
    }
}
