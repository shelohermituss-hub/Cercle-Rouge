package com.cerclerouge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.cerclerouge.alert.Input
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.protection.ProtectionController
import com.cerclerouge.app.ui.home.HomeScreen
import com.cerclerouge.app.ui.home.HomeViewModel
import com.cerclerouge.app.ui.onboarding.OnboardingScreen
import com.cerclerouge.app.ui.onboarding.OnboardingViewModel
import com.cerclerouge.app.ui.theme.CercleRougeTheme
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.HudType
import com.cerclerouge.privacy.Settings
import com.cerclerouge.privacy.log.ChainedEntry
import com.cerclerouge.privacy.log.EventLog
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
                Box(Modifier.fillMaxSize().background(Cr.colors.bgBase)) {
                    when (screen) {
                        Screen.LOADING -> Unit
                        Screen.ONBOARDING -> OnboardingScreen(
                            onboarding,
                            onSos = { app.controller.send { Input.Sos(it) } },
                            onDone = { screen = Screen.HOME }
                        )
                        Screen.HOME -> HomeScreen(home, onJournal = { screen = Screen.JOURNAL })
                        Screen.JOURNAL -> {
                            BackHandler { screen = Screen.HOME }
                            JournalScreen(app.log) { screen = Screen.HOME }
                        }
                    }
                }
            }
        }
    }
}

/** Journal local (P7, R-AUD-04) avec vérification de la chaîne de hachages. */
@Composable
private fun JournalScreen(log: EventLog, onBack: () -> Unit) {
    var entries by remember { mutableStateOf(emptyList<ChainedEntry>()) }
    var broken by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        entries = log.latest(200)
        broken = log.firstBreak()
    }
    val fmt = remember { DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault()) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.journal_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.journal_body), style = MaterialTheme.typography.bodySmall, color = Cr.colors.textSecondary)
        val status = broken?.let { stringResource(R.string.journal_broken, it.toInt()) } ?: stringResource(R.string.journal_intact)
        Text(status.uppercase(), style = HudType.label, color = if (broken == null) Cr.colors.protege else Cr.colors.alerte)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(entries, key = { it.seq }) { e ->
                Text(
                    "${fmt.format(Instant.ofEpochMilli(e.utcMillis))}  ${e.payload}",
                    style = HudType.label,
                    color = Cr.colors.textSecondary
                )
            }
        }
    }
}
