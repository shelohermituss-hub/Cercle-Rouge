package com.cerclerouge.location

import java.time.Duration

/** Profils de suivi (R-TRIP-03) : fréquence selon l'état, pas plus que nécessaire. */
enum class TrackingProfile(val interval: Duration) {
    /** `ARMED_FULL` : basse consommation. */
    ARMED(Duration.ofSeconds(30)),

    /** `TRIP_ACTIVE` : précision équilibrée. */
    TRIP(Duration.ofSeconds(10)),

    /** `EMERGENCY_MODE` : haute précision. */
    EMERGENCY(Duration.ofSeconds(5))
}

object Tracking {
    private const val LOW_BATTERY_PERCENT = 15

    /** Intervalle de demande de position : doublé quand la batterie passe sous 15 % (R-TRIP-03). `batteryPercent` inconnu : normal. */
    fun intervalFor(profile: TrackingProfile, batteryPercent: Int?): Duration =
        if (batteryPercent != null && batteryPercent < LOW_BATTERY_PERCENT) profile.interval.multipliedBy(2) else profile.interval
}
