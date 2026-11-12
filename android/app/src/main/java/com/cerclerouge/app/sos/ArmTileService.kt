package com.cerclerouge.app.sos

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Tuile « Protection » des réglages rapides : un geste ouvre l'application qui arme la protection (désarmer demande le PIN). */
class ArmTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(SosIntents.armPending(this, 14))
        } else {
            @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
            @Suppress("DEPRECATION")
            val legacy = { startActivityAndCollapse(SosIntents.armIntent(this)) }
            legacy()
        }
    }
}
