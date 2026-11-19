package com.cerclerouge.app.brain

import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Téléchargement du modèle (IA.10, T17) : source absente sautée, reprise par `Range`, empreinte refusée. */
@RunWith(RobolectricTestRunner::class)
class ModelStoreTest {
    private val body = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
    private val ranges = mutableListOf<String?>()
    private lateinit var server: HttpServer
    private lateinit var base: String
    private val store = ModelStore(ApplicationProvider.getApplicationContext())
    private val part get() = File(store.file.parentFile, "${ModelStore.FILE_NAME}.part")

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/missing") { it.sendResponseHeaders(404, -1) }
        server.createContext("/model") { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            ranges += range
            val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            ex.sendResponseHeaders(if (from > 0) 206 else 200, (body.size - from).toLong())
            ex.responseBody.use { it.write(body, from, body.size - from) }
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
        store.file.delete()
        part.delete()
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun `skips a missing source and installs the verified file`() = runBlocking {
        assertTrue(store.runDownload(listOf("$base/missing", "$base/model"), body.size.toLong(), sha))
        assertEquals(ModelState.Ready, store.state.value)
        assertTrue(store.file.readBytes().contentEquals(body))
    }

    @Test
    fun `resumes from the partial file`() = runBlocking {
        part.writeBytes(body.copyOf(120_000))
        assertTrue(store.runDownload(listOf("$base/model"), body.size.toLong(), sha))
        assertEquals("bytes=120000-", ranges.single())
        assertTrue(store.file.readBytes().contentEquals(body))
    }

    @Test
    fun `refuses a file with the wrong fingerprint`() = runBlocking {
        assertFalse(store.runDownload(listOf("$base/model"), body.size.toLong(), "0".repeat(64)))
        assertEquals(ModelState.Failed(ModelStore.REASON_HASH), store.state.value)
        assertFalse(store.file.exists())
        assertFalse(part.exists())
    }

    @Test
    fun `no source answering is a network failure`() = runBlocking {
        assertFalse(store.runDownload(listOf("$base/missing"), body.size.toLong(), sha))
        assertEquals(ModelState.Failed(ModelStore.REASON_NETWORK), store.state.value)
    }
}
