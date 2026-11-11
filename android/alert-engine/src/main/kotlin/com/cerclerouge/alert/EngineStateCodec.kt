package com.cerclerouge.alert

import com.cerclerouge.domain.Alert
import com.cerclerouge.domain.AlertLevel
import com.cerclerouge.domain.Confidence
import com.cerclerouge.domain.ProtectionState
import com.cerclerouge.domain.Reason
import com.cerclerouge.domain.Trigger
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant

/**
 * Sérialisation de l'état du moteur, pour le persister AVANT chaque effet (R-SM-01, R-AL-02) et le restaurer après la mort du
 * processus. Format texte `clé=valeur`, une ligne par champ, sans dépendance. Un texte illisible ou d'une autre version donne
 * `null` : l'appelant repart d'un état vierge plutôt que de planter.
 */
object EngineStateCodec {
    private const val VERSION = "1"

    fun encode(s: EngineState): String = buildList {
        add("v=$VERSION")
        add("protection=${s.protection}")
        add("base=${s.base}")
        add("beforeTrip=${s.beforeTrip}")
        s.alert?.let {
            add("alert.id=${enc(it.id)}")
            add("alert.level=${it.level}")
            add("alert.trigger=${it.trigger}")
            add("alert.reason=${it.reason}")
            add("alert.confidence=${it.confidence}")
            add("alert.silent=${it.silent}")
            add("alert.openedAt=${it.openedAt.toEpochMilli()}")
        }
        s.verifyAt?.let { add("verifyAt=${it.toEpochMilli()}") }
        s.escalateAt?.let { add("escalateAt=${it.toEpochMilli()}") }
        s.expiresAt?.let { add("expiresAt=${it.toEpochMilli()}") }
        add("micGranted=${s.micGranted}")
        add("locationGranted=${s.locationGranted}")
        add("pinFailures=${s.pinFailures}")
        add("pinFailuresReported=${s.pinFailuresReported}")
        add("refractory=" + s.refractoryUntil.entries.joinToString(",") { "${it.key}:${it.value.toEpochMilli()}" })
        add("recent=" + s.recentInputIds.joinToString(",") { enc(it) })
    }.joinToString("\n")

    fun decode(text: String): EngineState? = runCatching {
        val m = text.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        if (m["v"] != VERSION) return null
        fun instant(key: String) = m[key]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
        val alert = m["alert.id"]?.let {
            Alert(
                id = dec(it),
                level = AlertLevel.valueOf(m.getValue("alert.level")),
                trigger = Trigger.valueOf(m.getValue("alert.trigger")),
                reason = Reason.valueOf(m.getValue("alert.reason")),
                confidence = Confidence.valueOf(m.getValue("alert.confidence")),
                silent = m.getValue("alert.silent").toBooleanStrict(),
                openedAt = Instant.ofEpochMilli(m.getValue("alert.openedAt").toLong())
            )
        }
        EngineState(
            protection = ProtectionState.valueOf(m.getValue("protection")),
            base = ProtectionState.valueOf(m.getValue("base")),
            beforeTrip = ProtectionState.valueOf(m.getValue("beforeTrip")),
            alert = alert,
            verifyAt = instant("verifyAt"),
            escalateAt = instant("escalateAt"),
            expiresAt = instant("expiresAt"),
            micGranted = m.getValue("micGranted").toBooleanStrict(),
            locationGranted = m.getValue("locationGranted").toBooleanStrict(),
            pinFailures = m.getValue("pinFailures").toInt(),
            pinFailuresReported = m.getValue("pinFailuresReported").toBooleanStrict(),
            refractoryUntil = m["refractory"].orEmpty().split(',').filter { it.isNotBlank() }.associate {
                Reason.valueOf(it.substringBefore(':')) to Instant.ofEpochMilli(it.substringAfter(':').toLong())
            },
            recentInputIds = m["recent"].orEmpty().split(',').filter { it.isNotBlank() }.map(::dec)
        )
    }.getOrNull()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
}
