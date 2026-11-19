package com.cerclerouge.app.ui.verre

import com.cerclerouge.contacts.Channel
import com.cerclerouge.contacts.ContactGroup
import com.cerclerouge.contacts.ContactRole
import com.cerclerouge.contacts.ContactStatus
import com.cerclerouge.contacts.E164
import com.cerclerouge.contacts.Language
import com.cerclerouge.contacts.TrustedContact

/** Données d'exemple des maquettes (Jean, Nadège, Paul). */
object Fixtures {
    fun contact(name: String, position: Int, status: ContactStatus = ContactStatus.PENDING, guardian: Boolean = false, hasApp: Boolean = false) = TrustedContact(
        id = "c$position",
        name = name,
        phone = E164("+5093712345$position"),
        group = ContactGroup.FAMILY,
        role = if (position == 0) ContactRole.PRIMARY else ContactRole.BACKUP,
        language = Language.FR,
        channels = listOf(Channel.SMS),
        status = status,
        position = position,
        isGuardian = guardian,
        hasApp = hasApp
    )
}
