package dev.hyprconnect.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import dev.hyprconnect.app.data.remote.HyprConnectClient
import javax.inject.Inject

/**
 * Quick Settings tile: "Send Clipboard to PC"
 *
 * Android 10+ prevents background apps from reading the clipboard, so we cannot
 * read it directly inside onClick(). Instead we launch [ClipboardSendActivity] —
 * a transparent, no-history activity that gains focus, reads the clipboard, sends
 * to the PC, and immediately finishes. The user sees only the tile tap + a toast.
 *
 * Setup for users: swipe down → long-press an empty tile slot → find "Send Clipboard"
 * → drag it to the active area.
 */
@AndroidEntryPoint
class ClipboardSyncTile : TileService() {

    @Inject lateinit var client: HyprConnectClient

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, ClipboardSendActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val connected = client.isConnected.value
        tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.contentDescription = if (connected) "Connected — tap to send clipboard" else "Not connected to PC"
        tile.updateTile()
    }
}
