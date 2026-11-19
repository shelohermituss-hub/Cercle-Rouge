package com.cerclerouge.app.ui.contacts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cerclerouge.app.BuildConfig
import com.cerclerouge.app.R
import com.cerclerouge.app.contacts.AddOutcome
import com.cerclerouge.app.contacts.ContactsRepository
import com.cerclerouge.app.contacts.PickPhone
import com.cerclerouge.app.server.ServerSync
import com.cerclerouge.app.ui.components.Avatar
import com.cerclerouge.app.ui.components.BackHeader
import com.cerclerouge.app.ui.components.BottomActions
import com.cerclerouge.app.ui.components.DashedAdd
import com.cerclerouge.app.ui.components.GlassIcon
import com.cerclerouge.app.ui.components.InkButton
import com.cerclerouge.app.ui.components.Marks
import com.cerclerouge.app.ui.components.MonoBox
import com.cerclerouge.app.ui.components.MonoLine
import com.cerclerouge.app.ui.components.PillChoice
import com.cerclerouge.app.ui.components.PixelIcon
import com.cerclerouge.app.ui.components.PixelIconView
import com.cerclerouge.app.ui.components.PxButton
import com.cerclerouge.app.ui.components.PxPanel
import com.cerclerouge.app.ui.components.PxVariant
import com.cerclerouge.app.ui.components.SectionLabel
import com.cerclerouge.app.ui.components.TabHeader
import com.cerclerouge.app.ui.components.TextLink
import com.cerclerouge.app.ui.components.Tone
import com.cerclerouge.app.ui.components.V
import com.cerclerouge.app.ui.components.VCard
import com.cerclerouge.app.ui.components.VField
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

    /** Ajoute le proche avec sa langue, puis lui envoie l'invitation par SMS (il devra accepter). */
    fun addAndInvite(name: String, phone: String, language: Language, onDone: () -> Unit) = viewModelScope.launch {
        val before = repo.book.value.contacts.map { it.id }.toSet()
        add(name, phone).join()
        val added = repo.book.value.contacts.firstOrNull { it.id !in before } ?: return@launch
        if (added.language != language) repo.setLanguage(added.id, language)
        invite(repo.book.value.contacts.first { it.id == added.id }).join()
        onDone()
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
    val book by vm.book.collectAsState()
    val message by vm.message.collectAsState()
    val sending by vm.sending.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TrustedContact?>(null) }
    // Accord des proches : relu toutes les 20 s tant que l'écran est ouvert.
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            vm.refreshStatuses()
        }
    }
    if (adding) {
        androidx.activity.compose.BackHandler { adding = false }
        AddContactScreen(vm, message, onBack = {
            adding = false
            vm.dismissMessage()
        })
    } else {
        ContactsContent(
            book,
            message,
            sending,
            onAdd = { adding = true },
            onOpen = { editing = it },
            onMove = { id, d -> vm.move(id, d) },
            onPrimary = { vm.setPrimary(it) },
            onLanguage = { vm.cycleLanguage(it) },
            onInvite = { if (sending == null) vm.invite(it) },
            onRemove = { vm.remove(it) },
            onSimulate = { vm.simulateAccepted(it) },
            onWatch = onWatch
        )
    }
    editing?.let { contact ->
        EditDialog(contact, onDismiss = { editing = null }) { edit ->
            vm.save(contact.id, edit)
            editing = null
        }
    }
}

/** Ligne de statut d'un proche (police terminal) : canal et rôle dans l'alerte, ou invitation en attente. */
@Composable
private fun statusLine(contact: TrustedContact): Pair<String, Color> = when (contact.status) {
    ContactStatus.ACCEPTED -> (
        "● " + stringResource(if (contact.hasApp) R.string.v_ct_app else R.string.v_ct_sms) + " · " +
            stringResource(if (contact.position == 0) R.string.v_ct_first else R.string.v_ct_follows)
        ) to Color(0xFF15803D)
    ContactStatus.PENDING -> (stringResource(R.string.v_ct_sms) + " · " + stringResource(R.string.v_ct_invited)) to Color(0xFF8A5300)
    ContactStatus.DECLINED -> stringResource(R.string.contacts_status_declined) to Color(0xFF9E0F15)
    ContactStatus.WITHDRAWN -> stringResource(R.string.contacts_status_withdrawn) to Color(0xFF566B80)
}

/**
 * Onglet « Proches » (maquette VerreProches) : résumé, ordre d'alerte (« Modifier » montre les actions), proches avec leur
 * statut, ajout en pointillés, et les règles d'envoi en lignes de terminal.
 */
@Composable
internal fun ContactsContent(
    book: ContactBook,
    message: Int?,
    sending: String?,
    onAdd: () -> Unit,
    onOpen: (TrustedContact) -> Unit,
    onMove: (String, Int) -> Unit,
    onPrimary: (String) -> Unit,
    onLanguage: (TrustedContact) -> Unit,
    onInvite: (TrustedContact) -> Unit,
    onRemove: (String) -> Unit,
    onSimulate: (String) -> Unit,
    onWatch: () -> Unit,
    initialEdit: Boolean = false
) {
    val c = Cr.colors
    var edit by remember { mutableStateOf(initialEdit) }
    val contacts = book.contacts.sortedBy { it.position }
    val ready = contacts.count { it.status == ContactStatus.ACCEPTED }
    val pending = contacts.count { it.status == ContactStatus.PENDING }
    Column(Modifier.fillMaxSize().background(c.surface).verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        TabHeader(stringResource(R.string.v_contacts_title), subtitle = stringResource(R.string.v_contacts_sub))
        Row(
            Modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp
            ).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.raised).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassIcon(R.drawable.glass_proches, 40.dp)
            Column {
                Text(
                    stringResource(R.string.v_contacts_count, contacts.size, ContactBook.MAX),
                    style = V.text(17.sp, FontWeight.SemiBold),
                    color = c.ink
                )
                Text(stringResource(R.string.v_contacts_summary, ready, pending), style = V.text(14.sp), color = c.muted)
            }
        }
        message?.let {
            Text(
                stringResource(it),
                style = V.text(14.sp, FontWeight.Medium),
                color = if (it == R.string.contacts_invite_sent) c.forest else c.danger,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        if (contacts.isNotEmpty()) {
            SectionLabel(stringResource(R.string.v_alert_order), stringResource(if (edit) R.string.v_done else R.string.v_edit)) {
                edit =
                    !edit
            }
            VCard {
                contacts.forEachIndexed { i, ct ->
                    if (i > 0) com.cerclerouge.app.ui.components.Divider(Modifier.padding(horizontal = 14.dp))
                    PersonRow(
                        ct, i, edit, sending == ct.id, contacts.size,
                        onOpen, onMove, onPrimary, onLanguage, onInvite, onRemove, onSimulate
                    )
                }
            }
        } else {
            Text(
                stringResource(R.string.contacts_empty),
                style = V.body,
                color = c.muted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)
            )
        }
        Box(Modifier.padding(top = 12.dp)) {
            DashedAdd(
                androidx.compose.ui.res.pluralStringResource(
                    R.plurals.v_add_contact_places,
                    ContactBook.MAX - contacts.size,
                    ContactBook.MAX - contacts.size
                ),
                onAdd,
                enabled =
                contacts.size < ContactBook.MAX
            )
        }
        MonoBox(
            buildList {
                val alerted = contacts.filter { it.status == ContactStatus.ACCEPTED }.take(3).map { it.name.substringBefore(' ') }
                add(
                    MonoLine(
                        "›",
                        Marks.next,
                        if (alerted.isEmpty()) {
                            stringResource(
                                R.string.v_ct_rule_none
                            )
                        } else {
                            stringResource(R.string.v_ct_rule_alert, alerted.joinToString(", "))
                        }
                    )
                )
                add(MonoLine("›", Marks.next, stringResource(R.string.v_ct_rule_sms)))
                contacts.filter {
                    it.status == ContactStatus.PENDING
                }.forEach { add(MonoLine("›", Marks.wait, stringResource(R.string.v_ct_rule_pending, it.name.substringBefore(' ')))) }
            },
            Modifier.padding(top = 12.dp)
        )
        TextLink(stringResource(R.string.watch_open), onWatch, Modifier.padding(top = 8.dp), color = c.lime)
    }
}

@Composable
private fun PersonRow(
    ct: TrustedContact,
    index: Int,
    edit: Boolean,
    sending: Boolean,
    count: Int,
    onOpen: (TrustedContact) -> Unit,
    onMove: (String, Int) -> Unit,
    onPrimary: (String) -> Unit,
    onLanguage: (TrustedContact) -> Unit,
    onInvite: (TrustedContact) -> Unit,
    onRemove: (String) -> Unit,
    onSimulate: (String) -> Unit
) {
    val c = Cr.colors
    val (line, lineColor) = statusLine(ct)
    val order = stringResource(R.string.v_alert_position, index + 1)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(ct) }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Avatar(ct.name, index)
            Column(Modifier.weight(1f)) {
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append(ct.name)
                        ct.relation?.takeIf { it.isNotBlank() }?.let {
                            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Normal, color = c.muted))
                            append(" · $it")
                            pop()
                        }
                    },
                    style = V.text(17.sp, FontWeight.SemiBold),
                    color = c.ink,
                    maxLines = 1
                )
                Text(line, style = V.mono, color = lineColor, maxLines = 2)
            }
            when {
                ct.isGuardian -> com.cerclerouge.app.ui.components.Chip(stringResource(R.string.v_referent), Tone.Ink)
                ct.status == ContactStatus.ACCEPTED -> com.cerclerouge.app.ui.components.Chip(
                    stringResource(R.string.contacts_status_accepted),
                    Tone.Calm
                )
                ct.status == ContactStatus.PENDING -> com.cerclerouge.app.ui.components.Chip(
                    stringResource(R.string.contacts_status_pending),
                    Tone.Wait
                )
                else -> com.cerclerouge.app.ui.components.Chip(stringResource(R.string.contacts_status_declined), Tone.Mute)
            }
            Text(
                "${index + 1}",
                style = V.text(13.sp, FontWeight.SemiBold),
                color = Color(0xFF66788A),
                modifier = Modifier.width(18.dp).semantics {
                    contentDescription =
                        order
                }
            )
        }
        if (edit) {
            Row(
                Modifier.fillMaxWidth().padding(start = 72.dp, end = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(PixelIcon.UP, stringResource(R.string.contacts_move_up), enabled = index > 0) { onMove(ct.id, -1) }
                IconButton(PixelIcon.DOWN, stringResource(R.string.contacts_move_down), enabled = index < count - 1) { onMove(ct.id, 1) }
                IconButton(PixelIcon.STAR, stringResource(R.string.contacts_primary), selected = ct.role == ContactRole.PRIMARY) {
                    onPrimary(ct.id)
                }
                Chip(ct.language.name) { onLanguage(ct) }
                if (ct.status != ContactStatus.ACCEPTED && ct.status != ContactStatus.WITHDRAWN) {
                    Chip(stringResource(if (sending) R.string.contacts_inviting else R.string.contacts_invite)) { onInvite(ct) }
                }
                if (BuildConfig.DEBUG &&
                    ct.status != ContactStatus.ACCEPTED
                ) {
                    Chip(stringResource(R.string.contacts_simulate)) { onSimulate(ct.id) }
                }
                Spacer(Modifier.weight(1f))
                IconButton(PixelIcon.CROSS, stringResource(R.string.contacts_remove)) { onRemove(ct.id) }
            }
        }
    }
}

/**
 * Ajouter un proche (maquette VerreProcheAjout) : prénom, numéro, choix dans les contacts du téléphone, langue des messages,
 * aperçu du SMS d'invitation, « Envoyer l'invitation » (ajout puis invitation par SMS).
 */
@Composable
private fun AddContactScreen(vm: ContactsViewModel, message: Int?, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+509") }
    var lang by remember { mutableStateOf(Language.FR) }
    var searching by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(PickPhone(context)) { picked ->
        picked?.let {
            name = it.name
            phone = it.number
        }
    }
    AddContactContent(
        name,
        phone,
        lang,
        message,
        onName = { name = it.take(40) },
        onPhone = { phone = it.take(20) },
        onLang = { lang = it },
        onPick = { picker.launch(Unit) },
        onSearch = { searching = true },
        onSend = { vm.addAndInvite(name.trim(), phone, lang, onBack) },
        onBack = onBack
    )
    if (searching) {
        SearchDialog(vm, onDismiss = {
            searching = false
            vm.clearSearch()
        }) { picked ->
            name = picked.name
            phone = picked.number
            searching = false
            vm.clearSearch()
        }
    }
}

@Composable
internal fun AddContactContent(
    name: String,
    phone: String,
    lang: Language,
    message: Int?,
    onName: (String) -> Unit,
    onPhone: (String) -> Unit,
    onLang: (Language) -> Unit,
    onPick: () -> Unit,
    onSearch: () -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit
) {
    val c = Cr.colors
    Column(Modifier.fillMaxSize().background(c.surface)) {
        BackHeader(stringResource(R.string.v_add_title), onBack, stringResource(R.string.v_add_sub))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            VField(stringResource(R.string.v_first_name), name, onName)
            VField(
                stringResource(R.string.v_number),
                phone,
                onPhone,
                hint = stringResource(R.string.v_number_hint),
                error = message?.takeIf { it != R.string.contacts_invite_sent }?.let { stringResource(it) },
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Box(Modifier.padding(horizontal = 16.dp)) {
                com.cerclerouge.app.ui.components.WhiteButton(stringResource(R.string.v_pick_contacts), onPick, icon = PixelIcon.USERS)
            }
            TextLink(stringResource(R.string.contacts_search), onSearch, color = c.lime)
            Column {
                Text(
                    stringResource(R.string.v_msg_language),
                    style = V.text(14.sp, FontWeight.SemiBold),
                    color = c.ink,
                    modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                )
                PillChoice(listOf(Language.FR to "Français", Language.HT to "Kreyòl", Language.EN to "English"), lang, onLang)
            }
            Column(
                Modifier.padding(
                    horizontal = 16.dp
                ).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.raised).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(stringResource(R.string.v_sms_preview), style = V.text(14.sp, FontWeight.SemiBold), color = c.ink)
                Text(stringResource(R.string.v_sms_preview_body), style = V.text(14.sp, line = 20.sp), color = Color(0xFF2C3A47))
            }
        }
        BottomActions {
            InkButton(stringResource(R.string.v_send_invite), onSend, enabled = name.isNotBlank() && phone.count(Char::isDigit) >= 8)
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
                Text(stringResource(R.string.contacts_manual), style = PxType.title)
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
            Text(stringResource(R.string.contacts_edit), style = PxType.title)
            Text(contact.phone.value, style = PxType.caption, color = c.muted)
            Field(name, { name = it.take(ContactBook.MAX_NAME) }, R.string.contacts_name, KeyboardType.Text)
            Field(email, { email = it.take(120) }, R.string.contacts_email, KeyboardType.Email)
            Field(relation, { relation = it.take(ContactBook.MAX_RELATION) }, R.string.contacts_relation, KeyboardType.Text)
            Text(stringResource(R.string.contacts_role).uppercase(), style = PxType.label, color = c.muted)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ContactRole.entries.forEach { r -> Pick(stringResource(roleLabel(r)), role == r) { role = r } }
            }
            Text(stringResource(R.string.contacts_group).uppercase(), style = PxType.label, color = c.muted)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                com.cerclerouge.contacts.ContactGroup.entries.forEach { g ->
                    Pick(stringResource(groupLabel(g)), group == g) { group = g }
                }
            }
            Text(stringResource(R.string.contacts_channels).uppercase(), style = PxType.label, color = c.muted)
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
                Text(stringResource(R.string.contacts_search), style = PxType.title)
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
