package com.cerclerouge.app.ui.verre

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.cerclerouge.app.ui.onboarding.LoginUi
import com.cerclerouge.app.ui.onboarding.OnbActions
import com.cerclerouge.app.ui.onboarding.OnboardingUi
import com.cerclerouge.app.ui.onboarding.Step
import com.cerclerouge.app.ui.theme.CercleRougeTheme
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.contacts.ContactBook
import com.cerclerouge.domain.ConsentScope
import com.cerclerouge.domain.Retention
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Écrans « Verre » rendus à la taille des maquettes Claude Design (390 × 844, 1 px = 1 dp), en français, dans
 * build/verre : ils sont comparés côte à côte aux maquettes (planches envoyées au porteur).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "fr-w390dp-h844dp-mdpi")
class VerreScreensTest {
    @get:Rule val rule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { CercleRougeTheme { Box(Modifier.fillMaxSize().background(Cr.colors.surface)) { content() } } }
        rule.mainClock.advanceTimeBy(1700) // après l'apparition du logo (1,5 s)
        val dir = File("build/verre").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private class FakeOnb(val initial: OnboardingUi, contacts: ContactBook = ContactBook()) : OnbActions {
        override val ui = MutableStateFlow(initial)
        override val login = MutableStateFlow(LoginUi())
        override val book = MutableStateFlow(contacts)
        override fun edit(change: OnboardingUi.() -> OnboardingUi) {
            ui.value = ui.value.change()
        }
        override fun goTo(step: Step) = Unit
        override fun saveProfile() = Unit
        override fun requestGuardian() = Unit
        override fun checkGuardian() = Unit
        override fun simulateGuardian() = Unit
        override fun chooseRetention(r: Retention) = Unit
        override fun pinDigit(value: String) = Unit
        override fun recordConsent(scope: ConsentScope, granted: Boolean) = Unit
        override fun finish(onDone: () -> Unit) = Unit
        override fun addFirstContact() = Unit
        override fun refreshContacts() = Unit
        override fun requestLogin(raw: String) = Unit
        override fun verifyLogin(code: String, onRestored: () -> Unit) = Unit
        override fun cancelLogin() = Unit
    }

    private fun onb(content: @Composable () -> Unit) = @Composable {
        Box(Modifier.fillMaxSize().background(com.cerclerouge.app.ui.components.screenBg(true))) { content() }
    }

    @Test fun onb1() = capture("VerreOnb1", onb { com.cerclerouge.app.ui.onboarding.Posters(0, {}, {}, {}) })

    @Test fun onb2() = capture("VerreOnb2", onb { com.cerclerouge.app.ui.onboarding.Posters(1, {}, {}, {}) })

    @Test fun onb3() = capture("VerreOnb3", onb { com.cerclerouge.app.ui.onboarding.Posters(2, {}, {}, {}) })

    @Test fun onb4() = capture("VerreOnb4", onb { com.cerclerouge.app.ui.onboarding.Posters(3, {}, {}, {}) })

    @Test fun permissions() = capture(
        "VerrePermissions",
        onb {
            com.cerclerouge.app.ui.onboarding.PermissionsScreen(FakeOnb(OnboardingUi())) {}
        }
    )

    @Test fun setup1() = capture(
        "VerreSetup1",
        onb {
            val f = FakeOnb(OnboardingUi(step = Step.PROFILE, name = "Mia", month = "3", year = "2011", holderAttested = true))
            com.cerclerouge.app.ui.onboarding.ProfileStep(f.initial, f)
        }
    )

    @Test fun setup2() = capture(
        "VerreSetup2",
        onb {
            val f = FakeOnb(OnboardingUi(step = Step.GUARDIAN, guardianPhone = "+509 37 12 34 56"))
            com.cerclerouge.app.ui.onboarding.GuardianStep(f.initial, f) {}
        }
    )

    @Test fun setup3() = capture(
        "VerreSetup3",
        onb {
            val f = FakeOnb(OnboardingUi(step = Step.PIN, pin = "12"))
            com.cerclerouge.app.ui.onboarding.PinStep(f.initial, f)
        }
    )

    @Test fun setup4() = capture(
        "VerreSetup4",
        onb {
            com.cerclerouge.app.ui.onboarding.PhraseContent(com.cerclerouge.app.ui.listen.ListenUi(samples = 1), true, {}, {}, {})
        }
    )

    @Test fun setup5() = capture(
        "VerreSetup5",
        onb {
            val f =
                FakeOnb(
                    OnboardingUi(step = Step.FINISH, retention = Retention.HOURS_24),
                    ContactBook(listOf(Fixtures.contact("Jean", 0, guardian = true)))
                )
            com.cerclerouge.app.ui.onboarding.FinishStep(f.initial, f) {}
        }
    )

    private val sketch: @Composable (Boolean, Boolean, Modifier) -> Unit = { armed, _, m ->
        com.cerclerouge.app.ui.components.SketchMap(
            m,
            me = if (armed) com.cerclerouge.app.ui.components.DEFAULT_ROUTE.first() else androidx.compose.ui.geometry.Offset(44f, 60f),
            animate = false
        )
    }

    @Test fun homeOn() = capture("VerreAccueilActif") {
        com.cerclerouge.app.ui.home.HomeConsole(
            com.cerclerouge.alert.EngineState(
                protection = com.cerclerouge.domain.ProtectionState.ARMED_FULL,
                micGranted = true,
                locationGranted = true
            ),
            {},
            3,
            {},
            {},
            {},
            sketch,
            audioStatus = com.cerclerouge.app.audio.AudioStatus.RUNNING,
            explanation = "trajet habituel, rien d'anormal",
            since = "21 h 04",
            trip = com.cerclerouge.app.ui.home.HomeTrip(3.2, 14),
            stats = com.cerclerouge.app.ui.home.WatcherStats(0.62f, 0.48f, 4f)
        )
    }

    @Test fun homeOff() = capture("VerreAccueilCoupe") {
        com.cerclerouge.app.ui.home.HomeConsole(com.cerclerouge.alert.EngineState(), {}, 3, {}, {}, {}, sketch, lastOut = "hier, 22 h 10")
    }

    @Test fun splash() = capture("VerreSplash") { com.cerclerouge.app.ui.components.Splash() }

    @Test fun watcher() = capture("VerreVeilleur") {
        com.cerclerouge.app.ui.brain.WatcherContent(
            armed = true,
            decision = null,
            thought = "trajet habituel, heure habituelle",
            stats = com.cerclerouge.app.ui.home.WatcherStats(0.62f, 0.48f, 4f),
            lines = listOf(
                com.cerclerouge.app.ui.brain.TalkLine(true, "Est-ce que je peux rentrer à pied ?"),
                com.cerclerouge.app.ui.brain.TalkLine(
                    false,
                    "Je ne peux pas prédire un danger. Jean et Nadège suivent ton trajet ; si tu t'arrêtes plus de 10 min, je les préviens."
                )
            ),
            onBack = {},
            onTalk = {},
            onJournal = {}
        )
    }

    @Test fun talk() = capture("VerreParler") {
        val card = com.cerclerouge.brain.Card("conduite-suivi", "Si quelqu'un te suit", "", validated = false)
        com.cerclerouge.app.ui.brain.TalkContent(
            listOf(
                com.cerclerouge.app.ui.brain.TalkLine(true, "J'ai peur, quelqu'un me suit"),
                com.cerclerouge.app.ui.brain.TalkLine(
                    false,
                    "Va vers un endroit éclairé et fréquenté. Je lance un trajet suivi et je garde Jean prêt.",
                    com.cerclerouge.brain.TalkReply("", card, com.cerclerouge.brain.TalkAction.OPEN_TRIP)
                ),
                com.cerclerouge.app.ui.brain.TalkLine(true, "Annule l'alerte"),
                com.cerclerouge.app.ui.brain.TalkLine(false, "Je ne peux pas le faire à ta place : il faut ton code.")
            ),
            { _, _ -> },
            {},
            {},
            {},
            {},
            {},
            {}
        )
    }

    @Test fun level() = capture("VerreEcoute") {
        val now = 1_000_000_000L
        val vals = listOf(42, 40, 36, 38, 95, 12, 62, 30, 48, 22, 40, 18, 80, 25, 55, 30)
        val peaks = vals.mapIndexed { i, v -> (now - 600_000L + i * 40_000L) to v / 100f }
        com.cerclerouge.app.ui.listen.SoundLevelContent(
            com.cerclerouge.app.audio.SoundHistory(peaks, List(31) { now - it * 1000L }, listOf(now - 480_000L)),
            emptyList(),
            emptyList(),
            now,
            alerts = 0,
            recording = false,
            muted = false,
            onBack = {},
            onMute = {},
            onTalk = {}
        )
    }

    @Test fun trip() = capture("VerreTrajet") {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                com.cerclerouge.app.ui.trip.TripActive(
                    com.cerclerouge.app.ui.trip.TripCard(
                        listOf(
                            "Jean",
                            "Nadège"
                        ),
                        14, 3.2, "21 h 58", 0.45f, 12, 10, 64, null, false, null
                    ),
                    map = { m ->
                        com.cerclerouge.app.ui.components.SketchMap(
                            m,
                            route = com.cerclerouge.app.ui.components.TRIP_ROUTE,
                            designW = 390f,
                            designH = 758f,
                            roadWidth = 8f,
                            routeWidth = 2.4f,
                            animate = false
                        )
                    },
                    onMore = {},
                    onArrived = {}
                )
            }
            com.cerclerouge.app.ui.components.MainNav(com.cerclerouge.app.ui.components.Tab.TRIP, {
            }, armed = true, onToggle = {}, insets = false)
        }
    }

    private val book = ContactBook(
        listOf(
            Fixtures.contact(
                "Jean",
                0,
                com.cerclerouge.contacts.ContactStatus.ACCEPTED,
                guardian = true,
                hasApp = true
            ).copy(relation = "Papa"),
            Fixtures.contact("Nadège", 1, com.cerclerouge.contacts.ContactStatus.ACCEPTED, hasApp = true),
            Fixtures.contact("Paul", 2)
        )
    )

    @Test fun contacts() = capture("VerreProches") {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                com.cerclerouge.app.ui.contacts.ContactsContent(book, null, null, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
            }
            com.cerclerouge.app.ui.components.MainNav(com.cerclerouge.app.ui.components.Tab.CONTACTS, {
            }, armed = true, onToggle = {}, insets = false)
        }
    }

    @Test fun addContact() = capture("VerreProcheAjout") {
        com.cerclerouge.app.ui.contacts.AddContactContent("Nadège", "+509 34 98 76 54", com.cerclerouge.contacts.Language.FR, null, {
        }, {}, {}, {}, {}, {}, {})
    }

    @Test fun sosHold() = capture("VerreSos") { com.cerclerouge.app.ui.home.SosHoldScreen({}, {}, listOf("Jean", "Nadège"), "114", {}) }

    @Test fun sosCountdown() = capture("VerreSosCompte") { com.cerclerouge.app.ui.home.CountdownScreen({}, {}, listOf("Jean", "Nadège")) }

    @Test fun verification() = capture("VerreVerification") {
        com.cerclerouge.app.ui.home.VerificationContent("Cris et choc près de toi", listOf("Jean", "Nadège"), 38, 60, {}, {})
    }

    @Test fun alert() = capture("VerreAlerte") {
        com.cerclerouge.app.ui.home.AlertContent(
            "Tes proches sont prévenus",
            "Jean, Nadège et Paul voient ta position. Jean est en route (6 min). Mets-toi en lieu sûr si tu peux.",
            listOf(
                com.cerclerouge.app.ui.home.AlertLine("21:32:05", "ok", "Jean · notification reçue"),
                com.cerclerouge.app.ui.home.AlertLine("21:32:09", "ok", "Paul · SMS livré"),
                com.cerclerouge.app.ui.home.AlertLine("21:32:12", "th", "preuves chiffrées")
            ),
            "114",
            {},
            {},
            face = { com.cerclerouge.app.ui.components.FaceUnlock(com.cerclerouge.app.ui.components.FacePhase.SUCCESS, it) }
        )
    }

    @Test fun pin() = capture("VerrePin") {
        com.cerclerouge.app.ui.home.PinContent("123", true, "Entre ton code pour confirmer que tu es en sécurité.", true, {}, {}, {}, {})
    }

    @Test fun settings() = capture("VerreReglages") {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { com.cerclerouge.app.ui.settings.SettingsMain({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
            com.cerclerouge.app.ui.components.MainNav(com.cerclerouge.app.ui.components.Tab.SETTINGS, {
            }, armed = true, onToggle = {}, insets = false)
        }
    }

    @Test fun journal() = capture("VerreJournal") {
        val now = System.currentTimeMillis()
        fun e(seq: Long, ago: Long, type: String, payload: String) =
            com.cerclerouge.privacy.log.ChainedEntry(seq, now - ago * 60_000, type, payload, "p", "h")
        com.cerclerouge.app.ui.journal.JournalContent(
            listOf(
                e(1, 36, "state", "OFF → ARMED_FULL : Arm"),
                e(2, 28, "audio", "sound family=context p=0.48 model=yamnet-1"),
                e(3, 22, "brain", "src=rules-1 ok=rien refus=-"),
                e(4, 9, "state", "ARMED_FULL → ALERT_PENDING : Detection"),
                e(5, 8, "state", "ALERT_PENDING → ARMED_FULL : ImSafe")
            ),
            null,
            {}
        )
    }

    @Test fun history() = capture("VerreHistorique") {
        val now = System.currentTimeMillis()
        com.cerclerouge.app.ui.history.HistoryContent(
            listOf(
                com.cerclerouge.privacy.db.HistoryEntity(
                    "t1",
                    "trip",
                    now - 86_400_000,
                    now - 86_400_000 + 14 * 60_000,
                    "WALK",
                    null,
                    "arrived"
                ),
                com.cerclerouge.privacy.db.HistoryEntity(
                    "a1",
                    "alert",
                    now - 90_000_000,
                    now - 90_000_000 + 3 * 60_000,
                    "DISTRESS_SCREAM",
                    1,
                    "safe"
                ),
                com.cerclerouge.privacy.db.HistoryEntity(
                    "a2",
                    "alert",
                    now - 600_000_000,
                    now - 600_000_000 + 9 * 60_000,
                    "SOS",
                    2,
                    "ended"
                )
            ),
            {},
            {}
        )
    }
}
