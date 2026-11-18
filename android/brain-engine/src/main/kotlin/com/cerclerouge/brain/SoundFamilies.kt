package com.cerclerouge.brain

/**
 * Les 521 classes de YAMNet ramenées à nos familles (IA.2). Seules ces classes comptent ; les autres (parole, animaux,
 * outils…) sont ignorées. Les seuils sont de départ : à calibrer sur les ambiances d'Haïti (`tools/audio-eval`).
 */
object SoundFamilies {
    val LABELS: Map<SoundFamily, Set<String>> = mapOf(
        SoundFamily.DISTRESS to setOf(
            "Screaming", "Shout", "Yell", "Children shouting", "Crying, sobbing", "Baby cry, infant cry", "Wail, moan",
            "Whimper", "Groan", "Gasp", "Bellow"
        ),
        SoundFamily.VIOLENCE to
            setOf("Slap, smack", "Smash, crash", "Breaking", "Shatter", "Thump, thud", "Whack, thwack", "Bang", "Crushing", "Slam"),
        SoundFamily.WEAPON to setOf("Gunshot, gunfire", "Machine gun", "Fusillade", "Artillery fire", "Cap gun", "Explosion"),
        SoundFamily.VEHICLE to setOf(
            "Skidding",
            "Tire squeal",
            "Car alarm",
            "Vehicle horn, car horn, honking",
            "Accelerating, revving, vroom",
            "Engine starting",
            "Door",
            "Car passing by"
        ),
        SoundFamily.CONTEXT to setOf(
            "Music", "Crowd", "Cheering", "Applause", "Chant", "Singing", "Drum", "Children playing", "Church bell", "Rain",
            "Rain on surface", "Laughter", "Television", "Radio", "Hubbub, speech noise, speech babble"
        )
    )

    /** Seuil par famille : la détresse et les armes un peu plus bas (rater coûte plus cher que vérifier). */
    val THRESHOLD: Map<SoundFamily, Float> = mapOf(
        SoundFamily.DISTRESS to 0.30f,
        SoundFamily.VIOLENCE to 0.35f,
        SoundFamily.WEAPON to 0.30f,
        SoundFamily.VEHICLE to 0.40f,
        SoundFamily.CONTEXT to 0.40f
    )

    /** Classe audio sujette aux contextes à ignorer (`IgnoreContext` de l'alert-engine). */
    fun audioClass(f: SoundFamily, label: String): String? = when (f) {
        SoundFamily.DISTRESS -> if (label.startsWith("Crying") || label.startsWith("Baby")) "crying" else "scream"
        SoundFamily.VIOLENCE -> "impact"
        SoundFamily.VEHICLE -> if (label in setOf("Skidding", "Tire squeal", "Vehicle horn, car horn, honking")) "horn_brake" else null
        SoundFamily.CONTEXT -> "music_crowd"
        SoundFamily.WEAPON -> null
    }

    /** Une famille entendue : la classe la plus probable de la famille et sa probabilité. */
    data class Heard(val family: SoundFamily, val label: String, val p: Float)

    /** Familles au-dessus de leur seuil dans une fenêtre de scores (un score par classe, dans l'ordre de [labels]). */
    fun heard(scores: FloatArray, labels: List<String>): List<Heard> {
        require(scores.size == labels.size) { "scores et classes de tailles différentes" }
        return LABELS.mapNotNull { (family, names) ->
            val best = labels.indices.filter { labels[it] in names }.maxByOrNull { scores[it] } ?: return@mapNotNull null
            val p = scores[best]
            if (p >= (THRESHOLD[family] ?: 1f)) Heard(family, labels[best], p) else null
        }.sortedByDescending { it.p }
    }
}
