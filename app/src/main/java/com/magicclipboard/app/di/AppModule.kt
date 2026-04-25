package com.magicclipboard.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.magicclipboard.data.ClipboardRepository
import com.magicclipboard.data.DefaultClipboardRepository
import com.magicclipboard.data.clip.ImageStorage
import com.magicclipboard.data.db.ClipDao
import com.magicclipboard.data.db.MagicClipboardDatabase
import com.magicclipboard.data.prefs.DataStoreSettingsRepository
import com.magicclipboard.data.prefs.SettingsRepository
import com.magicclipboard.data.security.AesGcmCipher
import com.magicclipboard.data.security.AndroidKeyStoreKeyProvider
import com.magicclipboard.data.security.PayloadCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("magicclipboard_settings.preferences_pb") },
        )
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MagicClipboardDatabase {
        return Room.databaseBuilder(
            context,
            MagicClipboardDatabase::class.java,
            "magicclipboard.db",
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideClipDao(database: MagicClipboardDatabase): ClipDao = database.clipDao()

    @Provides
    @Singleton
    fun providePayloadCipher(): PayloadCipher {
        return AesGcmCipher(AndroidKeyStoreKeyProvider().getOrCreateSecretKey())
    }

    @Provides
    @Singleton
    fun provideImageStorage(
        @ApplicationContext context: Context,
    ): ImageStorage = ImageStorage(context.filesDir)

    @Provides
    @Singleton
    fun provideClipboardRepository(
        @ApplicationContext context: Context,
        clipDao: ClipDao,
        payloadCipher: PayloadCipher,
        imageStorage: ImageStorage,
    ): ClipboardRepository {
        return DefaultClipboardRepository(context, clipDao, payloadCipher, imageStorage)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: DataStore<Preferences>,
    ): SettingsRepository = DataStoreSettingsRepository(dataStore)
}
