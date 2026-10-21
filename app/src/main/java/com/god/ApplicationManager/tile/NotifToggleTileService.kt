package com.god.ApplicationManager.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.god.ApplicationManager.DB.SettingsDB

/** QS Tile (F27): toggles the master switch for notification blocking. */
class NotifToggleTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        SettingsDB.toggleDisableAutoTurnOffNotificationState()
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val disabled = try {
            SettingsDB.getInstance().isDisableTurnOffNotification
        } catch (e: Exception) {
            false
        }
        tile.state = if (disabled) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
