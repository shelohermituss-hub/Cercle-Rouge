package com.cerclerouge.app.ui

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
import com.cerclerouge.alert.EngineState
import com.cerclerouge.app.ui.components.NavItem
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.Poster
import com.cerclerouge.app.ui.components.pixelGrid
import com.cerclerouge.app.ui.home.CountdownScreen
import com.cerclerouge.app.ui.home.HomeConsole
import com.cerclerouge.app.ui.home.SosHoldScreen
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

    @Test fun homeOff() = capture("home-off") { HomeConsole(EngineState(), {}, {}, {}, {}, nav) }

    @Test fun homeOn() = capture("home-on") {
        HomeConsole(EngineState(protection = ProtectionState.ARMED_AUDIO_ONLY, micGranted = true), {}, {}, {}, {}, nav)
    }

    @Test fun sosHold() = capture("sos-hold") { SosHoldScreen({}, {}) }

    @Test fun countdown() = capture("countdown") { CountdownScreen({}, {}) }

    @Test fun poster() = capture("poster-1") {
        Poster(1, 3, PixelIcon.SHIELD, "Tu n'es pas seul.", "Cercle Rouge prévient tes proches quand tu es en danger.", "Continuer", {}, {})
    }
}
