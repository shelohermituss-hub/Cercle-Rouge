package com.cerclerouge.privacy

import androidx.test.platform.app.InstrumentationRegistry
import com.cerclerouge.privacy.db.PrivacyDatabase
import com.cerclerouge.privacy.db.SettingEntity
import com.cerclerouge.privacy.log.EventLog
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest

/** S1.10–S1.12 sur un vrai Android : Keystore, SQLCipher, journal chaîné. */
class EncryptedStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun base_chiffree_relue_apres_reouverture() = runTest(timeout = 10.minutes) {
        context.deleteDatabase(PrivacyDatabase.NAME)
        val db = PrivacyDatabase.open(context)
        db.settings().put(SettingEntity("canari", "valeur-secrete-canari"))
        val log = EventLog(db.eventLog(), Clock.systemUTC())
        repeat(3) { log.append("TEST", "entrée $it") }
        db.close()

        val file = context.getDatabasePath(PrivacyDatabase.NAME)
        val bytes = file.readBytes()
        // Une base SQLite en clair commence par « SQLite format 3 » et contiendrait le canari.
        assertFalse(String(bytes.copyOf(16), Charsets.ISO_8859_1).startsWith("SQLite format 3"))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("valeur-secrete-canari"))

        val reopened = PrivacyDatabase.open(context)
        assertEquals("valeur-secrete-canari", reopened.settings().get("canari"))
        assertNull(EventLog(reopened.eventLog(), Clock.systemUTC()).firstBreak())
        reopened.close()
    }
}
