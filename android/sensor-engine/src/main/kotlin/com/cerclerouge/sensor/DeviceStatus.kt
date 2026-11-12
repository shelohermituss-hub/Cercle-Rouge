package com.cerclerouge.sensor

/** Type de réseau actif. */
enum class NetworkKind { NONE, CELLULAR, WIFI, OTHER }

/**
 * État de l'appareil (docs/modules/sensor-engine.md) : batterie, réseau, GPS, mode avion, économie d'énergie, carte SIM.
 * Lu par l'accueil, les alertes (contenu des SMS, choix des canaux), les preuves et le battement de trajet.
 */
data class DeviceStatus(
    /** Niveau de batterie en %, null tant qu'il n'est pas connu. */
    val batteryPct: Int? = null,
    val charging: Boolean = false,
    val network: NetworkKind = NetworkKind.NONE,
    /** Accès Internet vérifié par Android (portail captif exclu). */
    val validated: Boolean = false,
    val gpsEnabled: Boolean = false,
    val airplaneMode: Boolean = false,
    val powerSave: Boolean = false,
    val simReady: Boolean = false
) {
    init {
        require(batteryPct == null || batteryPct in 0..100) { "batterie hors de [0, 100] : $batteryPct" }
    }

    /** Des données passent (canal serveur, lien de suivi). */
    val hasData: Boolean get() = network != NetworkKind.NONE && validated

    /** Un SMS peut partir du téléphone : carte SIM prête et mode avion coupé. */
    val canSendSms: Boolean get() = simReady && !airplaneMode

    /** Batterie faible (sous 15 %, hors charge) : à signaler dans les alertes et à l'accueil. */
    val lowBattery: Boolean get() = batteryPct != null && batteryPct < LOW_BATTERY_PCT && !charging

    companion object {
        const val LOW_BATTERY_PCT = 15
    }
}
