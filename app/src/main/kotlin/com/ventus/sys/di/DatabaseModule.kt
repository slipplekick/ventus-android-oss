package com.ventus.sys.di

import android.content.Context
import androidx.room.Room
import com.ventus.sys.data.local.MasterVaultMembershipDao
import com.ventus.sys.data.local.PlaylistPresetDao
import com.ventus.sys.data.local.ProfileTrackDao
import com.ventus.sys.data.local.ResolvedNameDao
import com.ventus.sys.data.local.VaultDao
import com.ventus.sys.data.local.VentusDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): VentusDatabase =
        Room
            .databaseBuilder(context, VentusDatabase::class.java, "ventus.db")
            // See VentusDatabase's doc comment on version 2 — deliberate and
            // temporary while pre-release, not a real migration strategy.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideVaultDao(db: VentusDatabase): VaultDao = db.vaultDao()

    @Provides
    fun provideProfileTrackDao(db: VentusDatabase): ProfileTrackDao = db.profileTrackDao()

    @Provides
    fun provideResolvedNameDao(db: VentusDatabase): ResolvedNameDao = db.resolvedNameDao()

    @Provides
    fun providePlaylistPresetDao(db: VentusDatabase): PlaylistPresetDao = db.playlistPresetDao()

    @Provides
    fun provideMasterVaultMembershipDao(db: VentusDatabase): MasterVaultMembershipDao = db.masterVaultMembershipDao()
}
