package com.cerclerouge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.cerclerouge.alert.Input
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.protection.ProtectionController
import com.cerclerouge.app.ui.components.Orb
import com.cerclerouge.app.ui.home.HomeScreen
import com.cerclerouge.app.ui.home.HomeViewModel
import com.cerclerouge.app.ui.journal.JournalScreen
import com.cerclerouge.app.ui.onboarding.OnboardingScreen
import com.cerclerouge.app.ui.onboarding.OnboardingViewModel
import com.cerclerouge.app.ui.theme.CercleRougeTheme
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.privacy.Settings
import com.cerclerouge.privacy.log.EventLog
import com.valentinerutto.orbmotion.orbs.OrbState
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    val settings: Settings,
    val account: AccountRepository,
    val log: EventLog,
    val controller: ProtectionController
) : ViewModel()

private enum class Screen { LOADING, ONBOARDING, HOME, JOURNAL }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val app: AppViewModel by viewModels()
    private val onboarding: OnboardingViewModel by viewModels()
    private val home: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CercleRougeTheme {
                var screen by remember { mutableStateOf(Screen.LOADING) }
                LaunchedEffect(Unit) {
                    app.account.load()
                    screen = if (app.settings.onboardingDone()) Screen.HOME else Screen.ONBOARDING
                }
                Box(Modifier.fillMaxSize().background(Cr.colors.bg)) {
                    when (screen) {
                        Screen.LOADING -> Orb(OrbState.BREATHING, 96.dp, Cr.colors.mint, Modifier.align(Alignment.Center))
                        Screen.ONBOARDING -> OnboardingScreen(
                            onboarding,
                            onSos = { app.controller.send { Input.Sos(it) } },
                            onDone = { screen = Screen.HOME }
                        )
                        Screen.HOME -> HomeScreen(home, onJournal = { screen = Screen.JOURNAL })
                        Screen.JOURNAL -> {
                            BackHandler { screen = Screen.HOME }
                            Box(Modifier.safeDrawingPadding()) { JournalScreen(app.log) { screen = Screen.HOME } }
                        }
                    }
                }
            }
        }
    }
}
