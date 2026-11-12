package com.cerclerouge.sensor

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceStatusTest {
    @Test fun dataNeedsAValidatedNetwork() {
        assertFalse(DeviceStatus(network = NetworkKind.WIFI, validated = false).hasData) // portail captif
        assertTrue(DeviceStatus(network = NetworkKind.CELLULAR, validated = true).hasData)
        assertFalse(DeviceStatus(network = NetworkKind.NONE, validated = true).hasData)
    }

    @Test fun smsNeedsASimAndNoAirplaneMode() { // R-NET : SMS en secours seulement s'il peut partir
        assertTrue(DeviceStatus(simReady = true).canSendSms)
        assertFalse(DeviceStatus(simReady = true, airplaneMode = true).canSendSms)
        assertFalse(DeviceStatus(simReady = false).canSendSms)
    }

    @Test fun lowBatteryIgnoresChargingAndUnknownLevel() {
        assertTrue(DeviceStatus(batteryPct = 9).lowBattery)
        assertFalse(DeviceStatus(batteryPct = 9, charging = true).lowBattery)
        assertFalse(DeviceStatus(batteryPct = null).lowBattery)
        assertFalse(DeviceStatus(batteryPct = 15).lowBattery)
    }

    @Test fun batteryMustBeAPercentage() {
        assertFailsWith<IllegalArgumentException> { DeviceStatus(batteryPct = 120) }
    }
}
