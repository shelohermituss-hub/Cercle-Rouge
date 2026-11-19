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
        rule.mainClock.advanceTimeBy(700)
        val dir = File("build/verre").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class FakeOnb(ui: OnboardingUi, contacts: ContactBook = ContactBook()) : OnbActions {
        override val ui = MutableStateFlow(ui)
        override val login = MutableStateFlow(LoginUi())
        override val book = MutableStateFlow(contacts)
        override fun edit(change: OnboardingUi.() -> OnboardingUi) { ui.value = ui.value.change() }
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
    @Test fun permissions() = capture("VerrePermissions", onb { com.cerclerouge.app.ui.onboarding.PermissionsScreen(FakeOnb(OnboardingUi())) {} })
    @Test fun setup1() = capture("VerreSetup1", onb {
        val f = FakeOnb(OnboardingUi(step = Step.PROFILE, name = "Mia", month = "3", year = "2011", holderAttested = true))
        com.cerclerouge.app.ui.onboarding.ProfileStep(f.ui.value, f)
    })
    @Test fun setup2() = capture("VerreSetup2", onb {
        val f = FakeOnb(OnboardingUi(step = Step.GUARDIAN, guardianPhone = "+509 37 12 34 56"))
        com.cerclerouge.app.ui.onboarding.GuardianStep(f.ui.value, f) {}
    })
    @Test fun setup3() = capture("VerreSetup3", onb {
        val f = FakeOnb(OnboardingUi(step = Step.PIN, pin = "12"))
        com.cerclerouge.app.ui.onboarding.PinStep(f.ui.value, f)
    })
    @Test fun setup4() = capture("VerreSetup4", onb {
        com.cerclerouge.app.ui.onboarding.PhraseContent(com.cerclerouge.app.ui.listen.ListenUi(samples = 1), true, {}, {}, {})
    })
    @Test fun setup5() = capture("VerreSetup5", onb {
        val f = FakeOnb(OnboardingUi(step = Step.FINISH, retention = Retention.HOURS_24), ContactBook(listOf(Fixtures.contact("Jean", 0, guardian = true))))
        com.cerclerouge.app.ui.onboarding.FinishStep(f.ui.value, f) {}
    })

    private val sketch: @Composable (Boolean, Boolean, Modifier) -> Unit = { armed, _, m ->
        com.cerclerouge.app.ui.components.SketchMap(m, me = if (armed) com.cerclerouge.app.ui.components.DEFAULT_ROUTE.first() else androidx.compose.ui.geometry.Offset(44f, 60f), animate = false)
    }

    @Test fun homeOn() = capture("VerreAccueilActif") {
        com.cerclerouge.app.ui.home.HomeConsole(
            com.cerclerouge.alert.EngineState(protection = com.cerclerouge.domain.ProtectionState.ARMED_FULL, micGranted = true, locationGranted = true),
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
}
