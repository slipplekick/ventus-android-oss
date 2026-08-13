package com.ventus.sys.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Entities grow incrementally as each screen lands (session history,
 * auto-add presets, master vault) rather than all being speculatively
 * defined up front — each addition is its own migration when it happens.
 *
 * Version 2 added [ResolvedNameEntity], version 3 added
 * [PlaylistPresetEntity] (Auto-Add), version 4 added
 * [MasterVaultMembershipEntity] (Master Vault) - all with no real Migration
 * (destructive fallback instead, DatabaseModule.provideDatabase), a
 * deliberate, temporary call while the app is still pre-release and gets
 * its DB cleared routinely during testing anyway. Once there's a real
 * release with user data worth preserving across an update, this needs
 * actual Migration steps instead.
 *
 * exportSchema is off for now — with schema versions not yet stable there's
 * nothing meaningful for it to verify. Room 2.8.4's KSP processor currently
 * crashes (AbstractMethodError inside its own schema-bundle serialization)
 * against this project's pinned Kotlin 2.0.21 toolchain regardless of
 * exportSchema's value — a real KSP/Kotlin compatibility gap, not something
 * exportSchema=false works around. Stay on 2.6.1 (already proven working)
 * until a deliberate Kotlin/AGP/Room version bump happens together, not in
 * isolation.
 */
@Database(
    entities = [
        VaultEntity::class,
        ProfileTrackEntity::class,
        ResolvedNameEntity::class,
        PlaylistPresetEntity::class,
        MasterVaultMembershipEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class VentusDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    abstract fun profileTrackDao(): ProfileTrackDao

    abstract fun resolvedNameDao(): ResolvedNameDao

    abstract fun playlistPresetDao(): PlaylistPresetDao

    abstract fun masterVaultMembershipDao(): MasterVaultMembershipDao
}
