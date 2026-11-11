package com.cerclerouge.app.ui.contacts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.app.BuildConfig
import com.cerclerouge.app.R
import com.cerclerouge.app.contacts.AddOutcome
import com.cerclerouge.app.contacts.ContactsRepository
import com.cerclerouge.app.contacts.PickPhone
import com.cerclerouge.app.server.ServerSync
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PixelIconView
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.components.TopBar
import com.cerclerouge.app.ui.theme.Cr
import com.cerclerouge.app.ui.theme.PxType
import com.cerclerouge.contacts.BookError
import com.cerclerouge.contacts.ContactBook
import com.cerclerouge.contacts.ContactRole
import com.cerclerouge.contacts.ContactStatus
import com.cerclerouge.contacts.Language
import com.cerclerouge.contacts.TrustedContact
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ContactsViewModel @Inject constructor(private val repo: ContactsRepository, private val server: ServerSync) : ViewModel() {
    val book: StateFlow<ContactBook> = repo.book
    private val _message = MutableStateFlow<Int?>(null)

    /** Dernier message d'erreur à afficher (ressource de chaîne), ou null. */
    val message: StateFlow<Int?> = _message

    private val _sending = MutableStateFlow<String?>(null)

    /** Contact dont l'invitation est en cours d'envoi. */
    val sending: StateFlow<String?> = _sending

    init {
        viewModelScope.launch {
            repo.load()
            refreshStatuses()
        }
    }

    /** Relit l'accord des proches sur le serveur (accepté, refusé, STOP). */
    suspend fun refreshStatuses() = repo.applyServerStatuses(server.contactStatuses())

    /** Invitation par SMS : le proche reçoit un lien pour accepter ou refuser (S3.03). */
    fun invite(contact: TrustedContact) = viewModelScope.launch {
        _sending.value = contact.id
        val error = server.inviteContact(contact)
        _sending.value = null
        _message.value = when (error) {
            null -> R.string.contacts_invite_sent
            "stopped" -> R.string.contacts_invite_stopped
            "rate_limited" -> R.string.contacts_invite_wait
            "already_accepted" -> null
            else -> R.string.contacts_invite_failed
        }
        refreshStatuses()
    }

    fun add(name: String, phone: String, onAdded: () -> Unit = {}) = viewModelScope.launch {
        when (val r = repo.add(name, phone)) {
            AddOutcome.Added -> {
                _message.value = null
                onAdded()
            }
            is AddOutcome.InvalidPhone -> _message.value = R.string.contacts_invalid_phone
            is AddOutcome.Refused -> _message.value = when (r.error) {
                BookError.FULL -> R.string.contacts_full
                BookError.DUPLICATE -> R.string.contacts_duplicate
                BookError.SECOND_PRIMARY -> R.string.contacts_full
            }
        }
    }

    fun remove(id: String) = viewModelScope.launch { repo.remove(id) }

    fun move(id: String, delta: Int) = viewModelScope.launch { repo.move(id, delta) }

    fun setPrimary(id: String) = viewModelScope.launch { repo.setPrimary(id) }

    fun cycleLanguage(c: TrustedContact) = viewModelScope.launch {
        repo.setLanguage(c.id, Language.entries[(c.language.ordinal + 1) % Language.entries.size])
    }

    fun simulateAccepted(id: String) = viewModelScope.launch { repo.simulateAccepted(id) }

    fun dismissMessage() {
        _message.value = null
    }
}

/** Contacts de confiance : 5 au maximum, ordre d'alerte, répondant principal, langue des messages (module produit 7). */
@Composable
fun ContactsScreen(vm: ContactsViewModel, onBack: () -> Unit) {
    val c = Cr.colors
    val book by vm.book.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    var manual by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(PickPhone(context)) { picked -> picked?.let { vm.add(it.name, it.number) } }
    // Accord des proches : relu toutes les 20 s tant que l'écran est ouvert.
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            vm.refreshStatuses()
        }
    }
    val full = book.contacts.size >= ContactBook.MAX

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.contacts_title), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.contacts_intro), style = PxType.body, color = c.muted)
            Text(stringResource(R.string.contacts_count, book.contacts.size).uppercase(), style = PxType.label, color = c.lime)
            message?.let {
                Text(
                    stringResource(it),
                    style = PxType.label,
                    color = if (it == R.string.contacts_invite_sent) c.lime else c.danger,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            if (book.contacts.isEmpty()) {
                PxPanel(Modifier.fillMaxWidth()) { Text(stringResource(R.string.contacts_empty), style = PxType.body) }
            }
            book.contacts.forEachIndexed { i, contact ->
                ContactRow(contact, first = i == 0, last = i == book.contacts.lastIndex, vm = vm)
            }
            Spacer(Modifier.size(4.dp))
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PxButton(stringResource(R.string.contacts_pick), { picker.launch(Unit) }, icon = PixelIcon.USERS, enabled = !full)
            PxButton(stringResource(R.string.contacts_manual), { manual = true }, variant = PxVariant.OUTLINE, enabled = !full)
        }
    }
    if (manual) {
        ManualDialog(
            onDismiss = {
                manual = false
                vm.dismissMessage()
            },
            onAdd = { name, phone -> vm.add(name, phone) { manual = false } },
            error = message
        )
    }
}

@Composable
private fun ContactRow(contact: TrustedContact, first: Boolean, last: Boolean, vm: ContactsViewModel) {
    val c = Cr.colors
    val accepted = contact.status == ContactStatus.ACCEPTED
    PxPanel(Modifier.fillMaxWidth(), border = if (accepted) c.lime else c.lineControl, padding = PaddingValues(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${contact.position + 1}", style = PxType.title, color = c.lime)
                Column(Modifier.weight(1f)) {
                    Text(contact.name, style = PxType.bodyBold, maxLines = 1)
                    Text(contact.phone.value, style = PxType.caption, color = c.muted)
                }
                Text(statusLabel(contact.status).uppercase(), style = PxType.label, color = if (accepted) c.lime else c.warn)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(PixelIcon.UP, stringResource(R.string.contacts_move_up), enabled = !first) { vm.move(contact.id, -1) }
                IconButton(PixelIcon.DOWN, stringResource(R.string.contacts_move_down), enabled = !last) { vm.move(contact.id, 1) }
                IconButton(
                    PixelIcon.STAR,
                    stringResource(R.string.contacts_primary),
                    selected = contact.role == ContactRole.PRIMARY
                ) { vm.setPrimary(contact.id) }
                Chip(contact.language.name) { vm.cycleLanguage(contact) }
                Spacer(Modifier.weight(1f))
                if (!accepted && contact.status != ContactStatus.WITHDRAWN) {
                    val sending by vm.sending.collectAsState()
                    Chip(stringResource(if (sending == contact.id) R.string.contacts_inviting else R.string.contacts_invite)) {
                        if (sending == null) vm.invite(contact)
                    }
                }
                if (BuildConfig.DEBUG && !accepted) Chip(stringResource(R.string.contacts_simulate)) { vm.simulateAccepted(contact.id) }
                IconButton(PixelIcon.CROSS, stringResource(R.string.contacts_remove)) { vm.remove(contact.id) }
            }
            if (contact.isGuardian) Text(stringResource(R.string.contacts_guardian).uppercase(), style = PxType.label, color = c.muted)
        }
    }
}

@Composable
private fun statusLabel(s: ContactStatus) = stringResource(
    when (s) {
        ContactStatus.PENDING -> R.string.contacts_status_pending
        ContactStatus.ACCEPTED -> R.string.contacts_status_accepted
        ContactStatus.DECLINED -> R.string.contacts_status_declined
        ContactStatus.WITHDRAWN -> R.string.contacts_status_withdrawn
    }
)

/** Bouton carré de 48 dp (zone tactile minimale) à icône pixel ; le libellé est lu par TalkBack. */
@Composable
private fun IconButton(icon: PixelIcon, label: String, enabled: Boolean = true, selected: Boolean = false, onClick: () -> Unit) {
    val c = Cr.colors
    PxPanel(
        Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        border = if (selected) {
            c.lime
        } else if (enabled) {
            c.lineControl
        } else {
            c.line
        },
        fill = if (selected) c.forestShade else c.raised,
        padding = PaddingValues(0.dp)
    ) {
        PixelIconView(
            icon,
            22.dp,
            if (selected) {
                c.lime
            } else if (enabled) {
                c.ink
            } else {
                c.muted
            },
            Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    val c = Cr.colors
    PxPanel(
        Modifier.clickable(role = Role.Button, onClick = onClick),
        border = c.lineControl,
        padding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Text(text.uppercase(), style = PxType.label, color = c.ink)
    }
}

@Composable
private fun ManualDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit, error: Int?) {
    val c = Cr.colors
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        PxPanel(border = c.lime, fill = c.surface, padding = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.contacts_manual).uppercase(), style = PxType.title)
                Field(name, { name = it.take(40) }, R.string.contacts_name, KeyboardType.Text)
                Field(phone, { phone = it.take(20) }, R.string.contacts_phone, KeyboardType.Phone)
                error?.let { Text(stringResource(it), style = PxType.label, color = c.danger) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { PxButton(stringResource(R.string.action_cancel), onDismiss, variant = PxVariant.OUTLINE) }
                    Column(Modifier.weight(1f)) {
                        PxButton(stringResource(R.string.contacts_add), { onAdd(name, phone) }, enabled = phone.isNotBlank())
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int, keyboard: KeyboardType) {
    val c = Cr.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label).uppercase(), style = PxType.label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true,
        textStyle = PxType.bodyBold,
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.lime,
            unfocusedBorderColor = c.lineControl,
            focusedLabelColor = c.lime,
            unfocusedLabelColor = c.muted,
            cursorColor = c.lime,
            focusedContainerColor = c.raised,
            unfocusedContainerColor = c.raised
        ),
        modifier = Modifier.fillMaxWidth().width(280.dp)
    )
}
