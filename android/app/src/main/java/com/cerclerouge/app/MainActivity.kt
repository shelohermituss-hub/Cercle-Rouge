package com.cerclerouge.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import com.cerclerouge.alert.Input
import com.cerclerouge.app.account.AccountRepository
import com.cerclerouge.app.audio.DeviceHealth
import com.cerclerouge.app.evidence.EvidencePurge
import com.cerclerouge.app.protection.ProtectionController
import com.cerclerouge.app.sos.SosIntents
import com.cerclerouge.app.trip.TripController
import com.cerclerouge.app.ui.contacts.ContactsScreen
import com.cerclerouge.app.ui.contacts.ContactsViewModel
import com.cerclerouge.app.ui.home.HomeScreen
import com.cerclerouge.app.ui.home.HomeViewModel
import com.cerclerouge.app.ui.journal.JournalScreen
import com.cerclerouge.app.ui.listen.ListenScreen
import com.cerclerouge.app.ui.listen.ListenViewModel
import com.cerclerouge.app.ui.onboarding.OnboardingScreen
import com.cerclerouge.app.ui.onboarding.OnboardingViewModel
import com.cerclerouge.app.ui.settings.SettingsScreen
import com.cerclerouge.app.ui.settings.SettingsViewModel
import com.cerclerouge.app.ui.theme.CercleRougeTheme
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.app.ui.trip.TripScreen
import com.cerclerouge.app.ui.trip.TripViewModel
import com.cerclerouge.app.ui.vault.VaultScreen
import com.cerclerouge.app.ui.vault.VaultViewModel
import com.cerclerouge.privacy.Settings
import com.cerclerouge.privacy.log.EventLog
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    val settings: Settings,
    val account: AccountRepository,
    val log: EventLog,
    val controller: ProtectionController,
    // Instancié dès le démarrage : il suit l'état de la protection.
    val trips: TripController,
    val purge: EvidencePurge,
    val health: DeviceHealth,
    val history: com.cerclerouge.app.history.HistoryRepository,
    val appearance: com.cerclerouge.app.settings.Appearance
) : ViewModel()

internal enum class Screen {
    LOADING,
    ONBOARDING,
    HOME,
    JOURNAL,
    CONTACTS,
    TRIP,
    LISTEN,
    VAULT,
    SETTINGS,
    WATCH,
    HISTORY,
    TALK,
    WATCHER,
    LEVEL
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() { // FragmentActivity : requis par BiometricPrompt (S7.02)
    internal val app: AppViewModel by viewModels()
    private val onboarding: OnboardingViewModel by viewModels()
    private val home: HomeViewModel by viewModels()
    private val contacts: ContactsViewModel by viewModels()
    private val trip: TripViewModel by viewModels()
    private val listen: ListenViewModel by viewModels()
    private val vault: VaultViewModel by viewModels()
    private val settingsVm: SettingsViewModel by viewModels()
    private val watch: com.cerclerouge.app.ui.watch.WatchViewModel by viewModels()
    private val talk: com.cerclerouge.app.ui.brain.TalkViewModel by viewModels()
    private val level: com.cerclerouge.app.ui.listen.SoundLevelViewModel by viewModels()
    private val disarmRequested = androidx.compose.runtime.mutableStateOf(false)
    private val sosRequested = androidx.compose.runtime.mutableStateOf(false)
    private val armRequested = androidx.compose.runtime.mutableStateOf(false)

    internal fun armRequestedState() = armRequested

    internal fun disarmRequestedState() = disarmRequested

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(SosIntents.EXTRA_SOS, false)) sosRequested.value = true
        if (intent.getBooleanExtra(SosIntents.EXTRA_ARM, false)) armRequested.value = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true) // le SOS de la tuile, du widget ou de la notification s'ouvre par-dessus l'écran verrouillé
        sosRequested.value = intent.getBooleanExtra(SosIntents.EXTRA_SOS, false)
        armRequested.value = intent.getBooleanExtra(SosIntents.EXTRA_ARM, false)
        setContent {
            val themeMode by app.appearance.mode.collectAsState()
            LaunchedEffect(Unit) { app.appearance.load() }
            CercleRougeTheme(themeMode) {
                var screen by remember { mutableStateOf(Screen.LOADING) }
                LaunchedEffect(Unit) {
                    app.account.load()
                    app.controller.ensureRestored() // état persisté (alerte en cours, protection interrompue…)
                    app.health.start() // mode dégradé de l'écoute (batterie, surchauffe)
                    app.purge.schedule()
                    app.purge.run() // rattrapage des suppressions de preuves (R-EVD-03)
                    screen = if (app.settings.onboardingDone()) Screen.HOME else Screen.ONBOARDING
                }
                CrashReport()
                Box(Modifier.fillMaxSize().background(Cr.colors.surface)) {
                    when (screen) {
                        Screen.LOADING -> com.cerclerouge.app.ui.components.Splash()
                        Screen.ONBOARDING -> OnboardingScreen(
                            onboarding,
                            listen,
                            onSos = { app.controller.send { Input.Sos(it) } },
                            onDone = { screen = Screen.HOME }
                        )
                        Screen.HOME -> HomeScreen(
                            home,
                            onJournal = { screen = Screen.JOURNAL },
                            onContacts = { screen = Screen.CONTACTS },
                            onTrip = { screen = Screen.TRIP },
                            onListen = { screen = Screen.LISTEN },
                            onVault = { screen = Screen.VAULT },
                            onSettings = { screen = Screen.SETTINGS },
                            onHistory = { screen = Screen.HISTORY },
                            sosRequested = sosRequested.value,
                            onSosConsumed = { sosRequested.value = false },
                            armRequested = armRequested.value,
                            onArmConsumed = { armRequested.value = false },
                            disarmRequested = disarmRequested.value,
                            onDisarmConsumed = { disarmRequested.value = false },
                            onTalk = { screen = Screen.WATCHER },
                            onLevel = { screen = Screen.LEVEL }
                        )
                        Screen.WATCHER -> {
                            BackHandler { screen = Screen.HOME }
                            Box(Modifier.safeDrawingPadding()) {
                                com.cerclerouge.app.ui.brain.WatcherScreen(
                                    home,
                                    talk,
                                    onBack = { screen = Screen.HOME },
                                    onTalk = { screen = Screen.TALK },
                                    onJournal = { screen = Screen.JOURNAL }
                                )
                            }
                        }
                        Screen.LEVEL -> {
                            BackHandler { screen = Screen.HOME }
                            Box(Modifier.safeDrawingPadding()) {
                                com.cerclerouge.app.ui.listen.SoundLevelScreen(level, onBack = { screen = Screen.HOME }, onTalk = {
                                    screen =
                                        Screen.TALK
                                })
                            }
                        }
                        Screen.TALK -> {
                            BackHandler { screen = Screen.HOME }
                            Box(Modifier.safeDrawingPadding()) {
                                com.cerclerouge.app.ui.brain.TalkScreen(
                                    talk,
                                    onBack = { screen = Screen.HOME },
                                    // Armer seulement : la page d'accueil n'arme que si la protection est coupée.
                                    onArm = {
                                        armRequested.value = true
                                        screen = Screen.HOME
                                    },
                                    onTrip = { screen = Screen.TRIP },
                                    onContacts = { screen = Screen.CONTACTS },
                                    onHome = { screen = Screen.HOME }
                                )
                            }
                        }
                        Screen.CONTACTS -> {
                            BackHandler { screen = Screen.HOME }
                            TabFrame(com.cerclerouge.app.ui.components.Tab.CONTACTS, { screen = it }) {
                                ContactsScreen(contacts, onWatch = {
                                    screen =
                                        Screen.WATCH
                                }) { screen = Screen.HOME }
                            }
                        }
                        Screen.WATCH -> {
                            BackHandler { screen = Screen.CONTACTS }
                            Box(Modifier.safeDrawingPadding()) {
                                com.cerclerouge.app.ui.watch.WatchScreen(watch) { screen = Screen.CONTACTS }
                            }
                        }
                        Screen.TRIP -> {
                            BackHandler { screen = Screen.HOME }
                            TabFrame(com.cerclerouge.app.ui.components.Tab.TRIP, {
                                screen = it
                            }) { TripScreen(trip) { screen = Screen.HOME } }
                        }
                        Screen.LISTEN -> {
                            BackHandler { screen = Screen.HOME }
                            Box(Modifier.safeDrawingPadding()) { ListenScreen(listen) { screen = Screen.HOME } }
                        }
                        Screen.VAULT -> {
                            BackHandler { screen = Screen.HOME }
                            Box(Modifier.safeDrawingPadding()) { VaultScreen(vault) { screen = Screen.HOME } }
                        }
                        Screen.SETTINGS -> {
                            BackHandler { screen = Screen.HOME }
                            TabFrame(com.cerclerouge.app.ui.components.Tab.SETTINGS, { screen = it }) {
                                SettingsScreen(settingsVm, onBack = {
                                    screen =
                                        Screen.HOME
                                }, onListen = { screen = Screen.LISTEN }, onContacts = { screen = Screen.CONTACTS }, onVault = {
                                    screen =
                                        Screen.VAULT
                                })
                            }
                        }
                        Screen.JOURNAL -> {
                            BackHandler { screen = Screen.HOME }
                            Box(Modifier.safeDrawingPadding()) {
                                JournalScreen(app.log, onHistory = { screen = Screen.HISTORY }) { screen = Screen.HOME }
                            }
                        }
                        Screen.HISTORY -> {
                            BackHandler { screen = Screen.HOME }
                            Box(Modifier.safeDrawingPadding()) {
                                com.cerclerouge.app.ui.history.HistoryScreen(app.history) { screen = Screen.HOME }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Onglet (Trajet, Proches, Réglages) : contenu et barre du bas des maquettes. Le bouton central renvoie à l'accueil, qui arme
 * (protection coupée) ou demande le code pour couper.
 */
@Composable
private fun MainActivity.TabFrame(tab: com.cerclerouge.app.ui.components.Tab, go: (Screen) -> Unit, content: @Composable () -> Unit) {
    val state by app.controller.state.collectAsState()
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).windowInsetsPadding(
                androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(
                    androidx.compose.foundation.layout.WindowInsetsSides.Top +
                        androidx.compose.foundation.layout.WindowInsetsSides.Horizontal
                )
            )
        ) {
            content()
        }
        com.cerclerouge.app.ui.components.MainNav(
            tab,
            { t ->
                go(
                    when (t) {
                        com.cerclerouge.app.ui.components.Tab.HOME -> Screen.HOME
                        com.cerclerouge.app.ui.components.Tab.TRIP -> Screen.TRIP
                        com.cerclerouge.app.ui.components.Tab.CONTACTS -> Screen.CONTACTS
                        com.cerclerouge.app.ui.components.Tab.SETTINGS -> Screen.SETTINGS
                    }
                )
            },
            armed = state.protection.isArmed,
            onToggle = {
                if (state.protection.isArmed) disarmRequestedState().value = true else armRequestedState().value = true
                go(Screen.HOME)
            }
        )
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
