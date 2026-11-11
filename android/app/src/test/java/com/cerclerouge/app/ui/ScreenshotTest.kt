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
import androidx.compose.ui.unit.dp
import com.cerclerouge.alert.EngineState
import com.cerclerouge.app.ui.components.BarChart
import com.cerclerouge.app.ui.components.CrIcon
import com.cerclerouge.app.ui.components.FacePhase
import com.cerclerouge.app.ui.components.FaceUnlock
import com.cerclerouge.app.ui.components.GradientOrb
import com.cerclerouge.app.ui.components.KpiTile
import com.cerclerouge.app.ui.components.NavItem
import com.cerclerouge.app.ui.components.RadarChart
import com.cerclerouge.app.ui.components.SectionHeader
import com.cerclerouge.app.ui.components.TraceLine
import com.cerclerouge.app.ui.components.TraceRow
import com.cerclerouge.app.ui.components.TraceStatus
import com.cerclerouge.app.ui.components.gridBackground
import com.cerclerouge.app.ui.home.CountdownScreen
import com.cerclerouge.app.ui.home.HomeConsole
import com.cerclerouge.app.ui.home.SosHoldScreen
import com.cerclerouge.app.ui.onboarding.Poster
import com.cerclerouge.app.ui.theme.CercleRougeTheme
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.domain.ProtectionState
import java.io.File
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
                Box(Modifier.fillMaxSize().background(Cr.colors.bg).gridBackground(Cr.colors.line.copy(alpha = 0.35f))) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(600)
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private val nav = listOf(
        NavItem("Accueil", CrIcon.HOME) {},
        NavItem("Carte", CrIcon.MAP) {},
        NavItem("Trajet", CrIcon.ROUTE) {},
        NavItem("Journal", CrIcon.HISTORY) {}
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

    @Test fun homeOff() = capture("home-off") { HomeConsole(EngineState(), {}, {}, {}, {}, {}, nav, fakeMap) }

    @Test fun homeOn() = capture("home-on") {
        HomeConsole(
            EngineState(protection = ProtectionState.ARMED_FULL, micGranted = true, locationGranted = true),
            {},
            {},
            {},
            {},
            {},
            nav,
            fakeMap
        )
    }

    @Test fun sosHold() = capture("sos-hold") { SosHoldScreen({}, {}) }

    @Test fun countdown() = capture("countdown") { CountdownScreen({}, {}) }

    @Test fun poster() = capture("poster-2") {
        Poster(2, 3, {
            GradientOrb(active = true, size = 190.dp)
        }, "L'IA écoute sur ton téléphone.", "Rien n'est envoyé. Elle cherche seulement un mot de détresse.", "Continuer", {}, {})
    }

    /** Galerie des visualiseurs (orbe, biométrie, terminal, graphiques, tuiles). */
    @Test fun visualizers() = capture("visualizers") {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GradientOrb(active = true, size = 110.dp)
                GradientOrb(active = false, size = 110.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FacePhase.entries.forEach { FaceUnlock(it, 56.dp) }
            }
            SectionHeader("Données")
            KpiTile(CrIcon.USERS, "Contacts", "3", unit = "/ 5")
            BarChart(
                listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"),
                listOf(listOf(2, 3, 1, 0, 4, 2, 1), listOf(0, 1, 0, 0, 1, 0, 0)),
                listOf(Color(0xFF1DB46C), Color(0xFFF2B226))
            )
            Row {
                RadarChart(listOf("Micro", "Position", "Notifs", "Contacts", "Journal"), listOf(1f, 1f, 0.1f, 0.6f, 1f), Color(0xFF4BE38C))
            }
            Column(Modifier.background(Color(0xFF070B14))) {
                TraceLine(
                    TraceRow(
                        1, 0, 'J',
                        Color(
                            0xFF6366F1
                        ),
                        "Aujourd'hui", "4 événements", start = 0.3f, length = 0.4f, duration = "2h10", status = TraceStatus.ERROR
                    ),
                    false
                )
                TraceLine(
                    TraceRow(
                        2, 1, 'P',
                        Color(
                            0xFF14C8A8
                        ),
                        "Protection activée", "state", listOf("08:12:40"), 0.34f, 0.2f, "1h05", TraceStatus.OK
                    ),
                    false
                )
                TraceLine(
                    TraceRow(3, 1, 'A', Color(0xFFE5202B), "Alerte SOS", "state", listOf("09:40:02"), 0.4f, 0f, "—", TraceStatus.ERROR),
                    true
                )
            }
        }
    }
}
