package com.cerclerouge.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.cerclerouge.alert.EngineState
import com.cerclerouge.app.audio.AudioStatus
import com.cerclerouge.app.audio.ListenActivity
import com.cerclerouge.app.audio.ListenEvent
import com.cerclerouge.app.audio.ListenEventKind
import com.cerclerouge.app.audio.ListenStage
import com.cerclerouge.app.ui.components.BarChart
import com.cerclerouge.app.ui.components.FacePhase
import com.cerclerouge.app.ui.components.FaceUnlock
import com.cerclerouge.app.ui.components.GradientOrb
import com.cerclerouge.app.ui.components.NavItem
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.Poster
import com.cerclerouge.app.ui.components.RadarChart
import com.cerclerouge.app.ui.components.TERM_BG
import com.cerclerouge.app.ui.components.TERM_RED
import com.cerclerouge.app.ui.components.TERM_TEAL
import com.cerclerouge.app.ui.components.TERM_VIOLET
import com.cerclerouge.app.ui.components.TraceLine
import com.cerclerouge.app.ui.components.TraceRow
import com.cerclerouge.app.ui.components.TraceStatus
import com.cerclerouge.app.ui.components.pixelGrid
import com.cerclerouge.app.ui.home.CountdownScreen
import com.cerclerouge.app.ui.home.HomeConsole
import com.cerclerouge.app.ui.home.SosHoldScreen
import com.cerclerouge.app.ui.journal.JournalContent
import com.cerclerouge.app.ui.theme.CercleRougeTheme
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.domain.ProtectionState
import com.cerclerouge.privacy.log.ChainedEntry
import java.io.File
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rend les écrans principaux en PNG (360×800 dp) dans build/screenshots : revue visuelle sans émulateur. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        // Les orbes animent sans fin : horloge manuelle, sinon Compose n'est jamais « au repos ».
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CercleRougeTheme {
                Box(Modifier.fillMaxSize().background(Cr.colors.surface).pixelGrid(Cr.colors.grid)) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(600)
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val nav = listOf(
        NavItem("Accueil", PixelIcon.HOME) {},
        NavItem("Carte", PixelIcon.PIN) {},
        NavItem("Trajet", PixelIcon.ARROW_RIGHT) {},
        NavItem("Journal", PixelIcon.CLOCK) {}
    )

    /**
     * MapLibre est natif : sur la JVM, la carte est remplacée par build/map-preview-{on,off}.png si ces captures existent
     * (rendu navigateur du même style), sinon par un fond uni.
     */
    private val fakeMap: @Composable (Boolean, Boolean, Modifier) -> Unit = { armed, _, modifier ->
        val file = File("build/map-preview-${if (armed) "on" else "off"}.png")
        if (file.exists()) {
            Image(BitmapFactory.decodeFile(file.path).asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
        } else {
            Box(modifier.background(if (armed) Color(0xFF14200F) else Color(0xFF16181B)))
        }
    }

    @Test fun homeOff() = capture("home-off") { HomeConsole(EngineState(), {}, {}, 0, {}, {}, {}, nav, fakeMap) }

    @Test fun homeOn() = capture("home-on") {
        HomeConsole(
            EngineState(protection = ProtectionState.ARMED_FULL, micGranted = true, locationGranted = true),
            {},
            {},
            2,
            {},
            {},
            {},
            nav,
            fakeMap,
            activity = ListenActivity(
                levels = List(40) { i -> (0.5f + 0.5f * kotlin.math.sin(i / 3.0).toFloat()).coerceIn(0f, 1f) * if (i > 25) 1f else 0.25f },
                stage = ListenStage.RESULT,
                events = listOf(
                    ListenEvent(ListenEventKind.VOICE),
                    ListenEvent(ListenEventKind.MISS, 24.1, 18.0)
                ),
                hasPhrase = true
            ),
            audioStatus = AudioStatus.RUNNING
        )
    }

    @Test fun detection() = capture("detection") {
        androidx.compose.foundation.layout.Column(
            Modifier.padding(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            com.cerclerouge.app.ui.listen.DetectionSettings(
                com.cerclerouge.app.protection.ListenPrefs(
                    ignored = mapOf(com.cerclerouge.alert.IgnoreContext.SPORT to java.time.Instant.parse("2026-11-12T22:00:00Z"))
                ),
                java.time.Instant.parse("2026-11-12T20:00:00Z"),
                {},
                {},
                { _, _ -> }
            )
        }
    }

    @Test fun contactEdit() = capture("contact-edit") {
        com.cerclerouge.app.ui.contacts.EditPanel(
            com.cerclerouge.contacts.TrustedContact(
                "c1", "Tante Marie", com.cerclerouge.contacts.E164("+50937123456"), com.cerclerouge.contacts.ContactGroup.FAMILY,
                com.cerclerouge.contacts.ContactRole.PRIMARY, com.cerclerouge.contacts.Language.HT,
                listOf(com.cerclerouge.contacts.Channel.SMS, com.cerclerouge.contacts.Channel.EMAIL),
                com.cerclerouge.contacts.ContactStatus.ACCEPTED, 0, email = "marie@example.org", relation = "tante"
            ),
            {},
            {}
        )
    }

    @Test fun sosHold() = capture("sos-hold") { SosHoldScreen({}, {}) }

    @Test fun countdown() = capture("countdown") { CountdownScreen({}, {}) }

    @Test fun poster() = capture("poster-1") {
        Poster(1, 3, PixelIcon.SHIELD, "Tu n'es pas seul.", "Cercle Rouge prévient tes proches quand tu es en danger.", "Continuer", {}, {})
    }

    /** Galerie des visualiseurs (orbe, biométrie, graphiques, terminal). */
    @Test fun visualizers() = capture("visualizers") {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GradientOrb(active = true, size = 110.dp)
                GradientOrb(active = false, size = 110.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { FacePhase.entries.forEach { FaceUnlock(it, 56.dp) } }
            BarChart(
                listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"),
                listOf(listOf(2, 3, 1, 0, 4, 2, 1), listOf(0, 1, 0, 0, 1, 0, 0)),
                listOf(Color(0xFFA0E705), Color(0xFFFFD23F))
            )
            RadarChart(listOf("Micro", "Position", "Notifs", "Contacts", "Journal"), listOf(1f, 1f, 0.1f, 0.6f, 1f), Color(0xFFA0E705))
            Column(Modifier.background(TERM_BG)) {
                TraceLine(
                    TraceRow(
                        1,
                        0,
                        'J',
                        TERM_VIOLET,
                        "Aujourd'hui",
                        "4 événements",
                        start = 0.3f,
                        length = 0.4f,
                        duration = "2h10",
                        status = TraceStatus.ERROR
                    ),
                    false
                )
                TraceLine(
                    TraceRow(2, 1, 'P', TERM_TEAL, "Protection activée", "state", listOf("08:12:40"), 0.34f, 0.2f, "1h05", TraceStatus.OK),
                    false
                )
                TraceLine(TraceRow(3, 1, 'A', TERM_RED, "Alerte SOS", "state", listOf("09:40:02"), 0.4f, 0f, "—", TraceStatus.ERROR), true)
            }
        }
    }

    private fun lateTonight() = java.time.LocalDate.now().atTime(23, 50).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Journal avec des entrées réalistes sur plusieurs jours : ne doit pas planter. */
    @Test fun journal() = capture("journal") {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        fun e(seq: Long, ago: Long, type: String, payload: String) = ChainedEntry(seq, now - ago, type, payload, "p", "h")
        JournalContent(
            listOf(
                e(1, 3 * day, "state", "OFF → ARMED_AUDIO_ONLY : Arm"),
                e(2, 3 * day - 3_600_000, "state", "ARMED_AUDIO_ONLY → OFF : Disarm"),
                e(3, day, "state", "OFF → ARMED_FULL : Arm"),
                e(4, day - 600_000, "state", "ARMED_FULL → EMERGENCY_MODE : Sos"),
                e(5, day - 500_000, "dispatch", "sms"),
                e(6, 3_600_000, "state", "OFF → ARMED_FULL : Arm"),
                e(7, 1_000_000, "engines", "x"),
                // Événement tard le soir : la barre de chronologie touche le bord droit.
                ChainedEntry(8, lateTonight(), "state", "OFF → ARMED_FULL : Arm", "p", "h")
            ),
            null,
            {}
        )
    }

    /** SOS : appui court = écran « garde appuyé » ; l'écran d'urgence et le journal doivent s'afficher. */
    @Test fun sosFlow() {
        rule.mainClock.autoAdvance = false
        val step = androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent {
            CercleRougeTheme {
                Box(Modifier.fillMaxSize().background(Cr.colors.surface)) {
                    when (step.intValue) {
                        0 -> HomeConsole(EngineState(), {}, {}, 0, {}, {}, { step.intValue = 1 }, nav, fakeMap)
                        1 -> SosHoldScreen({ step.intValue = 0 }, { step.intValue = 2 })
                        else -> CountdownScreen({ step.intValue = 0 }, {})
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(300)
        step.intValue = 1
        rule.mainClock.advanceTimeBy(1000)
        step.intValue = 2
        rule.mainClock.advanceTimeBy(3000)
    }

    /** Ligne de terminal en fin de journée (start proche de 1) : la barre ne doit pas planter. */
    @Test fun traceLineLateInDay() = capture("trace-late") {
        Column {
            TraceLine(TraceRow(1, 1, 'P', TERM_TEAL, "Protection activée", "state", listOf("23:50:00"), 0.99f, 0f, "—"), true)
            TraceLine(TraceRow(2, 1, 'P', TERM_TEAL, "Protection activée", "state", listOf("23:59:59"), 1.0f, 0.5f, "1h"), true)
        }
    }

    /** Vrai geste sur la tuile SOS de l'accueil : appui court → « garde appuyé », puis appui long → compte à rebours. */
    @Test fun sosTileGestures() {
        rule.mainClock.autoAdvance = true
        val step = androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent {
            CercleRougeTheme {
                Box(Modifier.fillMaxSize().background(Cr.colors.surface)) {
                    when (step.intValue) {
                        0 -> HomeConsole(
                            EngineState(),
                            {},
                            {},
                            0,
                            {},
                            { step.intValue = 2 },
                            { step.intValue = 1 },
                            nav,
                            fakeMap
                        )
                        1 -> SosHoldScreen({ step.intValue = 0 }, { step.intValue = 2 })
                        else -> CountdownScreen({ step.intValue = 0 }, {})
                    }
                }
            }
        }
        val sos = androidx.compose.ui.test.hasContentDescription("SOS", substring = true)
        rule.onNode(sos).performTouchInput {
            down(center)
            up()
        } // appui court
        rule.waitForIdle()
        assertEquals(1, step.intValue)
        step.intValue = 0
        rule.waitForIdle()
        rule.onNode(sos).performTouchInput { down(center) } // appui long
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1700)
        // Le compte à rebours est parti avant le relâchement : la tuile n'existe plus.
        assertEquals(2, step.intValue)
    }
}
