package dev.hyprconnect.app.di

import dev.hyprconnect.app.data.repository.DeviceRepositoryImpl
import dev.hyprconnect.app.data.repository.SettingsRepositoryImpl
import dev.hyprconnect.app.domain.repository.DeviceRepository
import dev.hyprconnect.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(
        deviceRepositoryImpl: DeviceRepositoryImpl
    ): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository
}
