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
import androidx.compose.ui.semantics.selected
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
class ContactsViewModel @Inject constructor(
    private val repo: ContactsRepository,
    private val server: ServerSync,
    private val directory: com.cerclerouge.app.contacts.DirectorySearch
) : ViewModel() {
    val directoryGranted: Boolean get() = directory.granted
    private val _found = MutableStateFlow<List<com.cerclerouge.app.contacts.PickedPhone>>(emptyList())

    /** Résultats de la recherche dans le répertoire (jusqu'à 20). */
    val found: StateFlow<List<com.cerclerouge.app.contacts.PickedPhone>> = _found

    fun search(query: String) = viewModelScope.launch { _found.value = directory.search(query) }

    fun clearSearch() {
        _found.value = emptyList()
    }

    /** Enregistre la fiche modifiée ; rend un message d'erreur (e-mail invalide) ou null. */
    fun save(id: String, edit: ContactEdit) = viewModelScope.launch {
        val error = repo.edit(id) { c ->
            c.copy(
                name = edit.name,
                email = edit.email,
                relation = edit.relation,
                role = edit.role,
                group = edit.group,
                channels = edit.channels
            )
        }
        _message.value = if (error == BookError.INVALID_EMAIL) R.string.contacts_invalid_email else null
    }

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
                BookError.INVALID_EMAIL -> R.string.contacts_invalid_email
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

/** Fiche modifiable d'un contact (PDF 12.1, 12.2). */
data class ContactEdit(
    val name: String,
    val email: String?,
    val relation: String?,
    val role: ContactRole,
    val group: com.cerclerouge.contacts.ContactGroup,
    val channels: List<com.cerclerouge.contacts.Channel>
)

/** Contacts de confiance : 5 au maximum, ordre d'alerte, répondant principal, langue des messages (module produit 7). */
@Composable
fun ContactsScreen(vm: ContactsViewModel, onWatch: () -> Unit = {}, onBack: () -> Unit) {
    val c = Cr.colors
    val book by vm.book.collectAsState()
    val message by vm.message.collectAsState()
    val context = LocalContext.current
    var manual by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TrustedContact?>(null) }
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
                ContactRow(contact, first = i == 0, last = i == book.contacts.lastIndex, vm = vm, onEdit = { editing = contact })
            }
            Spacer(Modifier.size(4.dp))
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PxButton(stringResource(R.string.contacts_pick), { picker.launch(Unit) }, icon = PixelIcon.USERS, enabled = !full)
            PxButton(stringResource(R.string.contacts_search), {
                searching = true
            }, variant = PxVariant.OUTLINE, icon = PixelIcon.USERS, enabled = !full)
            PxButton(stringResource(R.string.contacts_manual), { manual = true }, variant = PxVariant.OUTLINE, enabled = !full)
            PxButton(stringResource(R.string.watch_open), onWatch, variant = PxVariant.DARK, icon = PixelIcon.SHIELD)
        }
    }
    if (searching) {
        SearchDialog(vm, onDismiss = {
            searching = false
            vm.clearSearch()
        }) { picked ->
            vm.add(picked.name, picked.number) { searching = false }
        }
    }
    editing?.let { contact ->
        EditDialog(contact, onDismiss = { editing = null }) { edit ->
            vm.save(contact.id, edit)
            editing = null
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
private fun ContactRow(contact: TrustedContact, first: Boolean, last: Boolean, vm: ContactsViewModel, onEdit: () -> Unit) {
    val c = Cr.colors
    val accepted = contact.status == ContactStatus.ACCEPTED
    PxPanel(Modifier.fillMaxWidth(), border = if (accepted) c.lime else c.lineControl, padding = PaddingValues(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${contact.position + 1}", style = PxType.title, color = c.lime)
                Column(Modifier.weight(1f)) {
                    Text(contact.name, style = PxType.bodyBold, maxLines = 1)
                    Text(contact.phone.value, style = PxType.caption, color = c.muted)
                    val sub = listOfNotNull(
                        contact.relation,
                        stringResource(roleLabel(contact.role)).takeIf {
                            contact.role !=
                                ContactRole.NONE
                        }
                    )
                    if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = PxType.caption, color = c.ink, maxLines = 1)
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
                Chip(stringResource(R.string.contacts_edit)) { onEdit() }
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

@Composable
private fun roleLabel(r: ContactRole): Int = when (r) {
    ContactRole.PRIMARY -> R.string.contacts_role_primary
    ContactRole.BACKUP -> R.string.contacts_role_secondary
    ContactRole.MEDICAL -> R.string.contacts_role_medical
    ContactRole.PROFESSIONAL -> R.string.contacts_role_professional
    ContactRole.NEIGHBOR -> R.string.contacts_role_neighbor
    ContactRole.NONE -> R.string.contacts_role_none
}

@Composable
private fun groupLabel(g: com.cerclerouge.contacts.ContactGroup): Int = when (g) {
    com.cerclerouge.contacts.ContactGroup.FAMILY -> R.string.contacts_group_family
    com.cerclerouge.contacts.ContactGroup.FRIENDS -> R.string.contacts_group_friends
    com.cerclerouge.contacts.ContactGroup.NEIGHBORS -> R.string.contacts_group_neighbors
    com.cerclerouge.contacts.ContactGroup.WORK -> R.string.contacts_group_work
    com.cerclerouge.contacts.ContactGroup.CUSTOM -> R.string.contacts_group_other
}

@Composable
private fun channelLabel(ch: com.cerclerouge.contacts.Channel): Int = when (ch) {
    com.cerclerouge.contacts.Channel.SMS -> R.string.contacts_channel_sms
    com.cerclerouge.contacts.Channel.EMAIL -> R.string.contacts_channel_email
    com.cerclerouge.contacts.Channel.WHATSAPP -> R.string.contacts_channel_whatsapp
    com.cerclerouge.contacts.Channel.CALL -> R.string.contacts_channel_call
    com.cerclerouge.contacts.Channel.PUSH -> R.string.contacts_channel_sms
}

/** Fiche d'un contact (PDF 12.1, 12.2) : nom, e-mail, relation, rôle, groupe, canaux préférés. Le numéro ne se modifie pas. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun EditDialog(contact: TrustedContact, onDismiss: () -> Unit, onSave: (ContactEdit) -> Unit) {
    Dialog(onDismissRequest = onDismiss) { EditPanel(contact, onDismiss, onSave) }
}

/** Contenu de la fiche (séparé de la fenêtre pour pouvoir être capturé en test). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun EditPanel(contact: TrustedContact, onDismiss: () -> Unit, onSave: (ContactEdit) -> Unit) {
    val c = Cr.colors
    var name by remember { mutableStateOf(contact.name) }
    var email by remember { mutableStateOf(contact.email.orEmpty()) }
    var relation by remember { mutableStateOf(contact.relation.orEmpty()) }
    var role by remember { mutableStateOf(contact.role) }
    var group by remember { mutableStateOf(contact.group) }
    var channels by remember { mutableStateOf(contact.channels.filter { it != com.cerclerouge.contacts.Channel.PUSH }.toSet()) }
    PxPanel(border = c.lime, fill = c.surface, padding = PaddingValues(16.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.contacts_edit).uppercase(), style = PxType.title)
            Text(contact.phone.value, style = PxType.caption, color = c.muted)
            Field(name, { name = it.take(ContactBook.MAX_NAME) }, R.string.contacts_name, KeyboardType.Text)
            Field(email, { email = it.take(120) }, R.string.contacts_email, KeyboardType.Email)
            Field(relation, { relation = it.take(ContactBook.MAX_RELATION) }, R.string.contacts_relation, KeyboardType.Text)
            Text(stringResource(R.string.contacts_role).uppercase(), style = PxType.label, color = c.lime)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ContactRole.entries.forEach { r -> Pick(stringResource(roleLabel(r)), role == r) { role = r } }
            }
            Text(stringResource(R.string.contacts_group).uppercase(), style = PxType.label, color = c.lime)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                com.cerclerouge.contacts.ContactGroup.entries.forEach { g ->
                    Pick(stringResource(groupLabel(g)), group == g) { group = g }
                }
            }
            Text(stringResource(R.string.contacts_channels).uppercase(), style = PxType.label, color = c.lime)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    com.cerclerouge.contacts.Channel.SMS,
                    com.cerclerouge.contacts.Channel.EMAIL,
                    com.cerclerouge.contacts.Channel.WHATSAPP,
                    com.cerclerouge.contacts.Channel.CALL
                ).forEach { ch ->
                    Pick(stringResource(channelLabel(ch)), ch in channels) {
                        channels =
                            if (ch in channels) channels - ch else channels + ch
                    }
                }
            }
            Text(stringResource(R.string.contacts_channels_ex), style = PxType.caption, color = c.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { PxButton(stringResource(R.string.action_cancel), onDismiss, variant = PxVariant.OUTLINE) }
                Column(Modifier.weight(1f)) {
                    PxButton(stringResource(R.string.trip_save), {
                        onSave(ContactEdit(name, email.ifBlank { null }, relation.ifBlank { null }, role, group, channels.toList()))
                    })
                }
            }
        }
    }
}

@Composable
private fun Pick(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Cr.colors
    PxPanel(
        Modifier.clickable(role = Role.Checkbox, onClick = onClick).semantics { selected = on },
        border = if (on) c.lime else c.lineControl,
        padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) { Text(text.uppercase(), style = PxType.label, color = if (on) c.lime else c.ink) }
}

/** Recherche dans le répertoire du téléphone (READ_CONTACTS, demandée ici, au moment du besoin). */
@Composable
private fun SearchDialog(vm: ContactsViewModel, onDismiss: () -> Unit, onPick: (com.cerclerouge.app.contacts.PickedPhone) -> Unit) {
    val c = Cr.colors
    val found by vm.found.collectAsState()
    var granted by remember { mutableStateOf(vm.directoryGranted) }
    var query by remember { mutableStateOf("") }
    val ask = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        granted =
            it
    }
    LaunchedEffect(query, granted) { if (granted) vm.search(query) }
    Dialog(onDismissRequest = onDismiss) {
        PxPanel(border = c.lime, fill = c.surface, padding = PaddingValues(16.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.contacts_search).uppercase(), style = PxType.title)
                if (!granted) {
                    Text(stringResource(R.string.contacts_search_why), style = PxType.body)
                    PxButton(stringResource(R.string.contacts_search_allow), { ask.launch(android.Manifest.permission.READ_CONTACTS) })
                } else {
                    Field(query, { query = it.take(40) }, R.string.contacts_name, KeyboardType.Text)
                    if (query.trim().length >= 2 &&
                        found.isEmpty()
                    ) {
                        Text(stringResource(R.string.contacts_search_none), style = PxType.caption, color = c.muted)
                    }
                    found.forEach { p ->
                        PxPanel(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                onPick(p)
                            },
                            border = c.lineControl,
                            padding = PaddingValues(10.dp)
                        ) {
                            Column {
                                Text(p.name, style = PxType.bodyBold, maxLines = 1)
                                Text(p.number, style = PxType.caption, color = c.muted)
                            }
                        }
                    }
                }
                PxButton(stringResource(R.string.action_cancel), onDismiss, variant = PxVariant.OUTLINE)
            }
        }
    }
}
