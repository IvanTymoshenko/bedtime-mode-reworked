package app.bedtimemode

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile — the toggle ColorOS 17 removed from the shade. */
class BedtimeTileService : TileService() {

    companion object {
        fun refresh(ctx: Context) {
            runCatching { requestListeningState(ctx, ComponentName(ctx, BedtimeTileService::class.java)) }
        }
    }

    override fun onStartListening() = update()

    override fun onClick() {
        Bedtime.toggle(this)
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val st = Bedtime.evaluate(Prefs(this))
        tile.state = if (st.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = when {
            st.active && st.endsAt == Bedtime.FOREVER -> "On"
            st.active && st.endsAt != null -> "Until ${Format.time(st.endsAt)}"
            st.nextChangeAt != null -> "From ${Format.dayTime(st.nextChangeAt)}"
            else -> "Off"
        }
        tile.updateTile()
    }
}
