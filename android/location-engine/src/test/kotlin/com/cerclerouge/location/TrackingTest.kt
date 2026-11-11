package com.cerclerouge.location

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackingTest {
    @Test fun profilesFollowTheSpec() { // R-TRIP-03 : 5 s urgence, 10 s trajet, 30 s armé
        assertEquals(Duration.ofSeconds(5), Tracking.intervalFor(TrackingProfile.EMERGENCY, 80))
        assertEquals(Duration.ofSeconds(10), Tracking.intervalFor(TrackingProfile.TRIP, 80))
        assertEquals(Duration.ofSeconds(30), Tracking.intervalFor(TrackingProfile.ARMED, 80))
    }

    @Test fun lowBatteryDoublesTheInterval() {
        assertEquals(Duration.ofSeconds(20), Tracking.intervalFor(TrackingProfile.TRIP, 14))
        assertEquals(Duration.ofSeconds(10), Tracking.intervalFor(TrackingProfile.TRIP, 15)) // le seuil lui-même : normal
        assertEquals(Duration.ofSeconds(10), Tracking.intervalFor(TrackingProfile.EMERGENCY, null).multipliedBy(2))
    }

    @Test fun unknownBatteryKeepsTheNormalInterval() {
        assertEquals(Duration.ofSeconds(10), Tracking.intervalFor(TrackingProfile.TRIP, null))
    }
}
