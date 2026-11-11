package com.cerclerouge.contacts

import com.cerclerouge.domain.Reason
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessagesTest {
    private val at = Instant.parse("2026-11-11T03:14:00Z") // 22:14 à Port-au-Prince (UTC−5)
    private val zone = ZoneId.of("America/Port-au-Prince")
    private val fix = Fix(18.5392, -72.3364, 20f, at)

    private fun input(
        lang: Language,
        reason: Reason = Reason.SOS,
        kind: MessageKind = MessageKind.ALERT,
        position: LocationShare = LocationShare.Fresh(fix),
        test: Boolean = false,
        unconsented: Boolean = false,
        name: String = "Marie",
        link: String? = "https://cr.example/s/AbC123"
    ) = MessageInput(name, reason, kind, at, zone, position, link, lang, "a1b2c3d4-0000", test, unconsented, null)

    @Test fun gsm7TableMatchesTheSpecMeasurements() { // docs/produit/05 : é è à ù ì ò Ç passent, ê â î ô û « » ’ — non
        assertTrue(Gsm7.isGsm7("é è à ù ì ò Ç"))
        listOf("ê", "â", "î", "ô", "û", "«", "»", "’", "—").forEach { assertFalse(Gsm7.isGsm7(it), it) }
    }

    @Test fun segmentCounting() {
        assertEquals(1, Gsm7.segments("a".repeat(160)))
        assertEquals(2, Gsm7.segments("a".repeat(161)))
        assertEquals(2, Gsm7.segments("a".repeat(306)))
        assertEquals(3, Gsm7.segments("a".repeat(307)))
        assertEquals(1, Gsm7.segments("ê".repeat(70))) // UCS-2
        assertEquals(2, Gsm7.segments("ê".repeat(71)))
        assertEquals(161, Gsm7.septets("a".repeat(159) + "€")) // « € » compte pour 2
    }

    @Test fun everyTemplateIsGsm7InEveryLanguage() { // R-ALR-08 : un test échoue si un gabarit sort du GSM-7
        for (lang in Language.entries) {
            for (kind in MessageKind.entries) {
                for (reason in Reason.entries) {
                    for (pos in listOf(LocationShare.Fresh(fix), LocationShare.LastKnown(fix, 300), LocationShare.NotShared)) {
                        val m = Messages.sms(input(lang, reason, kind, pos, test = kind == MessageKind.TEST, unconsented = true))
                        assertTrue(Gsm7.isGsm7(m), "$lang/$kind/$reason hors GSM-7 : $m")
                    }
                }
            }
        }
    }

    @Test fun anAlertFitsInTwoSegmentsAtMost() {
        for (lang in Language.entries) {
            for (reason in Reason.entries) {
                val m = Messages.sms(input(lang, reason))
                assertTrue(Gsm7.segments(m) <= 2, "$lang/$reason = ${Gsm7.segments(m)} SMS : $m")
            }
        }
    }

    @Test fun alertCarriesNameTimeRawCoordinatesLinkAndShortId() {
        val m = Messages.sms(input(Language.FR))
        assertTrue("Marie" in m)
        assertTrue("22h14" in m, m)
        assertTrue("18.5392,-72.3364" in m, m)
        assertTrue("https://cr.example/s/AbC123" in m)
        assertTrue("a1b2c3" in m && "a1b2c3d4" !in m, "identifiant court pour la déduplication (R-ALR-02) : $m")
    }

    @Test fun lastKnownPositionShowsItsTime() {
        val m = Messages.sms(
            input(Language.FR, position = LocationShare.LastKnown(fix.copy(at = Instant.parse("2026-11-11T03:09:00Z")), 300))
        )
        assertTrue("22h09" in m, m)
        assertTrue("18.5392,-72.3364" in m, m)
    }

    @Test fun refusedLocationSaysNotShared() {
        assertTrue("non partag" in Messages.sms(input(Language.FR, position = LocationShare.NotShared)))
        assertTrue("not shared" in Messages.sms(input(Language.EN, position = LocationShare.NotShared)))
        assertFalse("18.5392" in Messages.sms(input(Language.FR, position = LocationShare.NotShared)))
    }

    @Test fun testMessagesStartWithTheTestMarker() { // R-ALR-05
        for (lang in Language.entries) {
            val m = Messages.sms(input(lang, kind = MessageKind.TEST, test = true))
            assertTrue(m.startsWith("[TEST]"), m)
        }
        assertFalse(Messages.sms(input(Language.FR)).contains("[TEST]"))
    }

    @Test fun unconsentedContactsGetTheMentionAndStop() { // ADR-0015
        val fr = Messages.sms(input(Language.FR, unconsented = true))
        assertTrue("pas inscrit" in fr && "STOP" in fr && "Marie" in fr, fr)
        assertFalse("pas inscrit" in Messages.sms(input(Language.FR)))
    }

    @Test fun closureMessagesExist() { // R-ALR-03
        assertTrue("va bien" in Messages.sms(input(Language.FR, kind = MessageKind.CLOSURE_SAFE)))
        assertTrue("Fausse alerte" in Messages.sms(input(Language.FR, kind = MessageKind.CLOSURE_FALSE)))
        assertTrue("terminée" in Messages.sms(input(Language.FR, kind = MessageKind.CLOSURE_ENDED)))
    }

    @Test fun pinFailuresWarnContactsInEveryLanguage() { // S7.01, R-SEC-02
        Language.entries.forEach { l ->
            val m = Messages.sms(input(l, kind = MessageKind.PIN_FAILURES))
            assertTrue("Marie" in m, m)
            assertTrue(Gsm7.isGsm7(m), "GSM-7 : $m")
            assertTrue(Gsm7.segments(m) <= 1, "un seul SMS : $m")
        }
    }

    @Test fun silentSecretPhraseNeverRevealsItself() { // le proche voit « SOS » seulement
        val m = Messages.sms(input(Language.FR, reason = Reason.SOS))
        assertFalse("phrase" in m.lowercase())
        assertFalse("secret" in m.lowercase())
    }

    @Test fun customMessageIsAppendedAndMayForceUcs2() {
        val m = Messages.sms(input(Language.FR).copy(custom = "Code du portail : 1234"))
        assertTrue(m.endsWith("Code du portail : 1234"))
    }

    @Test fun creoleDoesNotFallBackToUcs2WithItsOwnAccents() {
        assertTrue(Gsm7.isGsm7("Alèt ak yon rèl detrès. Li poko di li rive. Pozisyon pa pataje. Dènye pozisyon òdinè."))
    }
}
