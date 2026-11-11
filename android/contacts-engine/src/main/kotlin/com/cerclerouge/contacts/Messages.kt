package com.cerclerouge.contacts

import com.cerclerouge.domain.Reason
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class MessageKind { ALERT, TEST, CLOSURE_SAFE, CLOSURE_FALSE, CLOSURE_ENDED, PIN_FAILURES }

/**
 * Données d'un SMS. `alertId` : seul un identifiant court part dans le message (R-ALR-02). `unconsented` : le destinataire
 * n'est pas inscrit (SOS manuel) : mention et STOP (ADR-0015). `custom` : message personnalisé de l'utilisateur, ajouté tel quel.
 */
data class MessageInput(
    val name: String,
    val reason: Reason,
    val kind: MessageKind,
    val at: Instant,
    val zone: ZoneId,
    val position: LocationShare,
    val link: String?,
    val language: Language,
    val alertId: String,
    val test: Boolean,
    val unconsented: Boolean,
    val custom: String?
)

/**
 * Gabarits des SMS d'alerte (FR, HT, EN), tous en alphabet GSM-7 : un test échoue sinon (R-ALR-08). Pas de ê â î ô û, pas de
 * guillemets ni d'apostrophe typographique. Seul `name` ou `custom` saisis par l'utilisateur peuvent forcer l'UCS-2.
 * ⚠ Les textes créoles sont à faire relire par des locuteurs natifs.
 */
object Messages {
    fun sms(m: MessageInput): String {
        val t = Texts.of(m.language)
        val id = m.alertId.replace("-", "").take(6)
        val time = DateTimeFormatter.ofPattern(t.timePattern).format(m.at.atZone(m.zone))
        val body = when (m.kind) {
            MessageKind.ALERT, MessageKind.TEST -> buildString {
                append(t.alert(m.name, t.reason(m.reason), time))
                append(' ').append(position(m, t))
                m.link?.let { append(' ').append(t.follow).append(' ').append(it) }
                append(" Ref ").append(id)
            }
            MessageKind.CLOSURE_SAFE -> "${t.closureSafe(m.name)} Ref $id"
            MessageKind.CLOSURE_FALSE -> "${t.closureFalse(m.name)} Ref $id"
            MessageKind.CLOSURE_ENDED -> "${t.closureEnded(m.name)} Ref $id"
            MessageKind.PIN_FAILURES -> "${t.pinFailures(m.name)} Ref $id"
        }
        return buildString {
            if (m.test || m.kind == MessageKind.TEST) append("[TEST] ").append(t.testNote).append(' ')
            append(body)
            if (m.unconsented) append(' ').append(t.unconsented(m.name))
            m.custom?.takeIf { it.isNotBlank() }?.let { append(' ').append(it.trim()) }
        }
    }

    private fun position(m: MessageInput, t: Texts): String = when (val p = m.position) {
        is LocationShare.Fresh -> t.position(p.fix.raw(), p.fix.accuracyMeters.toInt())
        is LocationShare.LastKnown -> t.lastKnown(p.fix.raw(), DateTimeFormatter.ofPattern(t.timePattern).format(p.fix.at.atZone(m.zone)))
        LocationShare.NotShared -> t.notShared
    }
}

private interface Texts {
    val timePattern: String
    val follow: String
    val testNote: String
    val notShared: String
    fun alert(name: String, reason: String, time: String): String
    fun reason(r: Reason): String
    fun position(coords: String, accuracy: Int): String
    fun lastKnown(coords: String, time: String): String
    fun unconsented(name: String): String
    fun closureSafe(name: String): String
    fun closureFalse(name: String): String
    fun closureEnded(name: String): String

    /** S7.01, R-SEC-02 : quelqu'un essaie de désactiver l'alerte avec de faux PIN. */
    fun pinFailures(name: String): String

    companion object {
        fun of(l: Language): Texts = when (l) {
            Language.FR -> Fr
            Language.HT -> Ht
            Language.EN -> En
        }
    }
}

private object Fr : Texts {
    override val timePattern = "HH'h'mm"
    override val follow = "Suivi:"
    override val testNote = "Ceci est un test."
    override val notShared = "Position non partagée."
    override fun alert(name: String, reason: String, time: String) = "Alerte Cercle Rouge: $name demande de l'aide ($reason). $time."
    override fun reason(r: Reason) = when (r) {
        Reason.SOS -> "SOS déclenché"
        Reason.DISTRESS_SCREAM -> "cri de détresse détecté"
        Reason.VIOLENCE_SOUND -> "bruit de violence détecté"
        Reason.DISTRESS_KEYWORD -> "mot de détresse prononcé"
        Reason.TRIP_DEVIATION -> "déviation de trajet"
        Reason.PROLONGED_STOP -> "immobilité prolongée"
        Reason.CONTACT_LOST -> "perte de contact"
        Reason.ARRIVAL_OVERDUE -> "arrivée non confirmée"
    }
    override fun position(coords: String, accuracy: Int) = "Position: $coords (précision $accuracy m)."
    override fun lastKnown(coords: String, time: String) = "Dernière position connue à $time: $coords."
    override fun unconsented(name: String) =
        "Tu n'es pas inscrit(e) à Cercle Rouge; message envoyé par $name. Pour ne plus en recevoir: STOP."
    override fun closureSafe(name: String) = "Cercle Rouge: $name va bien. Alerte terminée."
    override fun closureFalse(name: String) = "Cercle Rouge: Fausse alerte de $name. Tout va bien."
    override fun closureEnded(name: String) = "Cercle Rouge: l'alerte de $name est terminée."
    override fun pinFailures(name: String) =
        "Cercle Rouge: plusieurs tentatives de désactiver l'alerte de $name avec un code faux. L'alerte continue."
}

private object Ht : Texts {
    override val timePattern = "HH:mm"
    override val follow = "Swiv li:"
    override val testNote = "Sa a se yon tès."
    override val notShared = "Pozisyon pa pataje."
    override fun alert(name: String, reason: String, time: String) = "Alèt Cercle Rouge: $name bezwen èd ($reason). $time."
    override fun reason(r: Reason) = when (r) {
        Reason.SOS -> "li voye SOS"
        Reason.DISTRESS_SCREAM -> "yo tande yon rèl detrès"
        Reason.VIOLENCE_SOUND -> "yo tande bri vyolans"
        Reason.DISTRESS_KEYWORD -> "yo tande yon mo detrès"
        Reason.TRIP_DEVIATION -> "li soti nan wout li"
        Reason.PROLONGED_STOP -> "li rete kanpe twò lontan"
        Reason.CONTACT_LOST -> "nou pa ka jwenn li"
        Reason.ARRIVAL_OVERDUE -> "li poko di li rive"
    }
    override fun position(coords: String, accuracy: Int) = "Pozisyon: $coords (presizyon $accuracy m)."
    override fun lastKnown(coords: String, time: String) = "Dènye pozisyon li konnen a $time: $coords."
    override fun unconsented(name: String) = "Ou pa enskri sou Cercle Rouge; $name voye mesaj sa a. Pou ou pa resevwa lòt: STOP."
    override fun closureSafe(name: String) = "Cercle Rouge: $name byen. Alèt la fini."
    override fun closureFalse(name: String) = "Cercle Rouge: Fo alèt $name. Tout bagay byen."
    override fun closureEnded(name: String) = "Cercle Rouge: alèt $name la fini."
    override fun pinFailures(name: String) = "Cercle Rouge: yo eseye plizyè fwa fèmen alèt $name ak yon move kòd. Alèt la kontinye."
}

private object En : Texts {
    override val timePattern = "HH:mm"
    override val follow = "Follow:"
    override val testNote = "This is a test."
    override val notShared = "Location not shared."
    override fun alert(name: String, reason: String, time: String) = "Cercle Rouge alert: $name needs help ($reason). $time."
    override fun reason(r: Reason) = when (r) {
        Reason.SOS -> "SOS triggered"
        Reason.DISTRESS_SCREAM -> "distress scream detected"
        Reason.VIOLENCE_SOUND -> "violent noise detected"
        Reason.DISTRESS_KEYWORD -> "distress word spoken"
        Reason.TRIP_DEVIATION -> "route deviation"
        Reason.PROLONGED_STOP -> "prolonged stop"
        Reason.CONTACT_LOST -> "contact lost"
        Reason.ARRIVAL_OVERDUE -> "arrival not confirmed"
    }
    override fun position(coords: String, accuracy: Int) = "Location: $coords (accuracy $accuracy m)."
    override fun lastKnown(coords: String, time: String) = "Last known location at $time: $coords."
    override fun unconsented(name: String) = "You are not signed up to Cercle Rouge; message sent by $name. To stop receiving them: STOP."
    override fun closureSafe(name: String) = "Cercle Rouge: $name is safe. Alert ended."
    override fun closureFalse(name: String) = "Cercle Rouge: False alarm from $name. All is well."
    override fun closureEnded(name: String) = "Cercle Rouge: $name's alert has ended."
    override fun pinFailures(name: String) =
        "Cercle Rouge: several attempts to turn off $name's alert with a wrong code. The alert continues."
}
