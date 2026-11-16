package com.cerclerouge.evidence

import com.cerclerouge.domain.Retention
import java.time.Duration
import java.time.Instant

/** Ce que le téléphone sait d'une alerte enregistrée, pour la rétention. */
data class EvidenceAlert(
    val alertId: String,
    val openedAt: Instant,
    /** Clôture de l'alerte ; null tant qu'elle est ouverte (ou si l'app a été tuée avant). */
    val closedAt: Instant?,
    /** Dernier segment écrit : sert de clôture de repli si l'alerte n'a jamais été close. */
    val lastWriteAt: Instant,
    /** « Conserver cette preuve » : date choisie, 90 j au plus depuis l'action. */
    val keptUntil: Instant? = null
)

/**
 * Rétention des preuves (S6.08, ADR-0017) : 24 h, 7 j, 30 j après la **clôture**, ou à la clôture ; 24 h si rien n'est choisi.
 * `MANUAL` (ADR-0027, choix explicite de l'utilisateur) : aucune suppression automatique, seulement à sa demande. Sinon
 * jamais de conservation illimitée (N6) : une alerte jamais close est traitée comme close à son dernier segment + 1 h, et
 * « Conserver » est plafonné à 90 jours.
 */
object RetentionPolicy {
    val MAX_KEEP: Duration = Duration.ofDays(90)
    private val ORPHAN_GRACE: Duration = Duration.ofHours(1)

    /** Délai après la clôture ; null pour `MANUAL` (pas de suppression automatique). */
    fun duration(choice: Retention?): Duration? = when (choice ?: Retention.HOURS_24) {
        Retention.HOURS_24 -> Duration.ofHours(24)
        Retention.DAYS_7 -> Duration.ofDays(7)
        Retention.DAYS_30 -> Duration.ofDays(30)
        Retention.ON_CLOSURE -> Duration.ZERO
        Retention.MANUAL -> null
    }

    /** Date de suppression ; null tant que l'alerte est ouverte et récente, ou si la conservation est manuelle. */
    fun deleteAt(a: EvidenceAlert, choice: Retention?, now: Instant): Instant? {
        val keep = duration(choice) ?: return null
        val closed = a.closedAt ?: a.lastWriteAt.plus(ORPHAN_GRACE).takeIf { !it.isAfter(now) } ?: return null
        val auto = closed.plus(keep)
        return a.keptUntil?.takeIf { it.isAfter(auto) } ?: auto
    }

    /** Alertes dont les preuves doivent être détruites maintenant. Idempotent : rejouer la purge ne change rien (R-EE-05). */
    fun due(alerts: List<EvidenceAlert>, choice: Retention?, now: Instant): List<String> =
        alerts.filter { a -> deleteAt(a, choice, now)?.let { !it.isAfter(now) } ?: false }.map { it.alertId }

    /** Garde serveur maximale de la copie de secours (ADR-0028), même en `MANUAL`. */
    val SERVER_MAX: Duration = Duration.ofDays(30)

    /** Heures de garde de la copie serveur à partir de `now` : l'échéance locale, plafonnée à 30 jours ; `MANUAL` : 30 jours. */
    fun serverKeepHours(a: EvidenceAlert, choice: Retention?, now: Instant): Int {
        val local = deleteAt(a, choice, now) ?: now.plus(SERVER_MAX)
        val hours = Duration.between(now, local).toHours()
        return hours.coerceIn(0, SERVER_MAX.toHours()).toInt()
    }

    /** « Conserver cette preuve » : plafonné à 90 jours à partir de maintenant. */
    fun keep(now: Instant, wanted: Duration): Instant = now.plus(if (wanted > MAX_KEEP) MAX_KEEP else wanted)
}

/** Règles de l'enregistrement (S6.01). */
object RecordingPolicy {
    const val SEGMENT_SECONDS = 30
    const val PRE_BUFFER_SECONDS = 5
    const val MAX_MINUTES = 60

    /** R-EVD-08 : sous 200 Mo libres, pas de vidéo ; sous 20 Mo, plus rien (le téléphone doit rester utilisable). */
    const val MIN_FREE_BYTES_VIDEO = 200L * 1024 * 1024
    const val MIN_FREE_BYTES_AUDIO = 20L * 1024 * 1024

    fun maxSegments(): Int = MAX_MINUTES * 60 / SEGMENT_SECONDS

    fun canRecordAudio(freeBytes: Long): Boolean = freeBytes >= MIN_FREE_BYTES_AUDIO
}
