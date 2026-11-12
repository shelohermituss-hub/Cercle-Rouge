package com.cerclerouge.contacts

import com.cerclerouge.domain.AlertLevel

/** `canSendSms` : réseau GSM disponible (SIM, signal, pas de mode avion). `hasData` : Internet disponible. */
data class DeviceStatus(val hasData: Boolean, val canSendSms: Boolean)

/** Un envoi à faire : `unconsented` = le contact n'a pas encore accepté (SOS manuel seulement, avec mention et STOP). */
data class Delivery(val contactId: String, val channel: Channel, val unconsented: Boolean)

/**
 * Destinataires et canaux d'une alerte (R-CE-01 : fonction pure).
 * - Niveaux 0, 1 et 4 : personne (le niveau 4 passe par le composeur, jamais par un envoi : R-ESC-02).
 * - Seuls les contacts ACCEPTED, sauf SOS manuel : il part aussi vers les contacts PENDING (ADR-0015), jamais vers un
 *   contact qui a refusé ou s'est retiré.
 * - Niveau 2 : répondant principal, puis backups, puis les autres, 3 au maximum (ADR-0017). Niveau 3 : tous, 5 au maximum.
 * - Par contact, jusqu'à deux canaux indépendants (R-ALR-01), dans l'ordre choisi ; sans données, SMS d'abord. Un contact non
 *   consenti ne reçoit que du SMS. Si aucun canal n'est utilisable maintenant, SMS quand même : la file d'attente le garde.
 */
fun resolveRecipients(level: AlertLevel, manualSos: Boolean, contacts: List<TrustedContact>, device: DeviceStatus): List<Delivery> {
    if (level != AlertLevel.CONTACTS && level != AlertLevel.EXTENDED) return emptyList()
    val eligible = contacts
        .filter {
            if (manualSos) {
                it.status == ContactStatus.ACCEPTED || it.status == ContactStatus.PENDING
            } else {
                it.status ==
                    ContactStatus.ACCEPTED
            }
        }
        .sortedBy { it.position }
    val chosen = if (level == AlertLevel.CONTACTS) {
        eligible.sortedWith(compareBy { rank(it.role) }).take(MAX_LEVEL_2)
    } else {
        eligible.take(ContactBook.MAX)
    }
    return chosen.flatMap { c -> channelsFor(c, device).map { Delivery(c.id, it, unconsented = c.status != ContactStatus.ACCEPTED) } }
}

private const val MAX_LEVEL_2 = 3

private fun rank(role: ContactRole) = when (role) {
    ContactRole.PRIMARY -> 0
    ContactRole.BACKUP -> 1
    else -> 2
}

private fun channelsFor(c: TrustedContact, device: DeviceStatus): List<Channel> {
    val consented = c.status == ContactStatus.ACCEPTED
    val preferred = if (consented) c.channels.distinct() else listOf(Channel.SMS)
    val ordered = if (device.hasData) preferred else preferred.sortedBy { if (it == Channel.SMS) 0 else 1 }
    val usable = ordered.filter {
        when (it) {
            Channel.SMS -> device.canSendSms
            Channel.PUSH -> device.hasData && c.hasApp && consented
            // L'e-mail part du serveur (étape 1.8) : il faut des données, une adresse et l'accord du proche.
            Channel.EMAIL -> device.hasData && consented && !c.email.isNullOrBlank()
            // WhatsApp et appel automatisé : choisis par l'utilisateur, mais pas encore envoyés (phase 2).
            Channel.WHATSAPP, Channel.CALL -> false
        }
    }
    return usable.take(2).ifEmpty { listOf(Channel.SMS) }
}
