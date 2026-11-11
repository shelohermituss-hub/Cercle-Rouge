package com.cerclerouge.app.sos

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Tuile « SOS » des réglages rapides : un geste depuis le volet, même écran verrouillé, mène au compte à rebours. */
class SosTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(SosIntents.pending(this, 11))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(SosIntents.launch(this))
        }
    }
}
