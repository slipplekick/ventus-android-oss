package com.ventus.sys.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved "playlist preset" — Auto-Add's target-playlist shortcut list.
 * Ports app.js's PRESET_DEFAULT/loadPresets/savePresets (app.js:1513-1526,
 * persisted there via localStorage; here via Room like every other list this
 * port persists). No default locked preset is seeded — app.js's own default
 * (`{name: '◈ TRACK VAULT', id: '1rxvJh0wWgia4ylocdC42Y', locked: true}`)
 * points at the desktop author's personal playlist, not something to ship
 * as a default for every install.
 */
@Entity(tableName = "playlist_presets")
data class PlaylistPresetEntity(
    @PrimaryKey val playlistId: String,
    val name: String,
)
