package com.cerclerouge.contacts

/**
 * Alphabet GSM 03.38 (SMS) : 160 septets par SMS seul, 153 par segment si le message est découpé. Hors alphabet, tout le
 * message passe en UCS-2 : 70 caractères, ou 67 par segment (docs/produit/05, mesuré au prototype S0.20).
 */
object Gsm7 {
    private const val BASIC =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001bÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private const val EXTENSION = "\u000c^{}\\[~]|€"

    fun isGsm7(text: String) = text.all { it in BASIC || it in EXTENSION }

    /** Septets utilisés (un caractère de la table d'extension en vaut 2). */
    fun septets(text: String) = text.sumOf { if (it in EXTENSION) 2 else 1 }

    fun segments(text: String): Int {
        if (text.isEmpty()) return 0
        val (single, multi, size) = if (isGsm7(text)) Triple(160, 153, septets(text)) else Triple(70, 67, text.length)
        return if (size <= single) 1 else (size + multi - 1) / multi
    }
}
