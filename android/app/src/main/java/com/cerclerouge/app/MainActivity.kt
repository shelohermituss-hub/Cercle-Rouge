package com.cerclerouge.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.cerclerouge.alert.Input
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.protection.ProtectionController
import com.cerclerouge.app.ui.components.Orb
import com.cerclerouge.app.ui.contacts.ContactsScreen
import com.cerclerouge.app.ui.contacts.ContactsViewModel
import com.cerclerouge.app.ui.home.HomeScreen
import com.cerclerouge.app.ui.home.HomeViewModel
import com.cerclerouge.app.ui.journal.JournalScreen
import com.cerclerouge.app.ui.onboarding.OnboardingScreen
import com.cerclerouge.app.ui.onboarding.OnboardingViewModel
import com.cerclerouge.app.ui.theme.CercleRougeTheme
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
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

private enum class Screen { LOADING, ONBOARDING, HOME, JOURNAL, CONTACTS }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val app: AppViewModel by viewModels()
    private val onboarding: OnboardingViewModel by viewModels()
    private val home: HomeViewModel by viewModels()
    private val contacts: ContactsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CercleRougeTheme {
                var screen by remember { mutableStateOf(Screen.LOADING) }
                LaunchedEffect(Unit) {
                    app.account.load()
                    app.controller.ensureRestored() // état persisté (alerte en cours, protection interrompue…)
                    screen = if (app.settings.onboardingDone()) Screen.HOME else Screen.ONBOARDING
                }
                CrashReport()
                Box(Modifier.fillMaxSize().background(Cr.colors.surface)) {
                    when (screen) {
                        Screen.LOADING -> Orb(OrbState.BREATHING, 96.dp, Cr.colors.lime, Modifier.align(Alignment.Center))
                        Screen.ONBOARDING -> OnboardingScreen(
                            onboarding,
                            onSos = { app.controller.send { Input.Sos(it) } },
                            onDone = { screen = Screen.HOME }
                        )
                        Screen.HOME -> HomeScreen(home, onJournal = { screen = Screen.JOURNAL }, onContacts = { screen = Screen.CONTACTS })
                        Screen.CONTACTS -> {
                            BackHandler { screen = Screen.HOME }
                            Box(Modifier.safeDrawingPadding()) { ContactsScreen(contacts) { screen = Screen.HOME } }
                        }
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

/** Version de test : propose de partager la trace du dernier plantage (rien n'est envoyé sans ton geste). */
@Composable
private fun CrashReport() {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current
    val file = remember { crashFile(context.applicationContext as android.app.Application) }
    var report by remember { mutableStateOf(runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()) }
    val text = report ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.crash_body))
                Text(text.take(1500), style = PxType.caption)
            }
        },
        confirmButton = {
            TextButton({
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, null))
            }) { Text(stringResource(R.string.crash_share)) }
        },
        dismissButton = {
            TextButton({
                file.delete()
                report = null
            }) { Text(stringResource(R.string.crash_close)) }
        }
    )
}
