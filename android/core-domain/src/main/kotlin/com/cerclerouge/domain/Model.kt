package com.cerclerouge.domain

import java.time.Instant

/** États de la protection (docs/01-etats-et-escalade.md). */
enum class ProtectionState {
    OFF,
    ARMED_AUDIO_ONLY,
    ARMED_FULL,
    TRIP_ACTIVE,
    ALERT_PENDING,
    ALERT_SENT,
    EMERGENCY_MODE
    ;

    val isAlert: Boolean get() = this == ALERT_PENDING || this == ALERT_SENT || this == EMERGENCY_MODE
    val isArmed: Boolean get() = this == ARMED_AUDIO_ONLY || this == ARMED_FULL || this == TRIP_ACTIVE
}

/** Niveaux d'alerte 0 à 4. Le niveau 4 (autorités) n'est jamais atteint automatiquement (R-ESC-02). */
enum class AlertLevel(val value: Int) {
    INFO(0),
    VERIFICATION(1),
    CONTACTS(2),
    EXTENDED(3),
    AUTHORITIES(4)
}

/** Ce qui a ouvert l'alerte. */
enum class Trigger { SOS, SECRET_PHRASE, AUTOMATIC, TRIP, HEARTBEAT_LOSS }

/** Raisons affichées aux proches : liste fermée (module produit 5). */
enum class Reason {
    SOS,
    DISTRESS_SCREAM,
    VIOLENCE_SOUND,
    DISTRESS_KEYWORD,
    TRIP_DEVIATION,
    PROLONGED_STOP,
    CONTACT_LOST,
    ARRIVAL_OVERDUE
}

enum class Confidence { LOW, MEDIUM, HIGH }

/** Fin d'une alerte. */
enum class Closure { USER_SAFE, FALSE_ALARM, EXPIRED }

/** Profils de pondération du risque (ADR-0008, ADR-0009). */
enum class VulnerabilityProfile { YOUNG_12_14, TEEN_15_17, ADULT, ISOLATED }

/** Une alerte ouverte. Le niveau ne redescend que par une action de l'utilisateur. */
data class Alert(
    val id: String,
    val level: AlertLevel,
    val trigger: Trigger,
    val reason: Reason,
    val confidence: Confidence,
    val silent: Boolean,
    val openedAt: Instant
)
