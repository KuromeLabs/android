package com.kuromelabs.kurome.di

import android.app.Application
import androidx.room.Room
import com.kuromelabs.kurome.data.data_source.DeviceDatabase
import com.kuromelabs.kurome.data.database.DeviceRepositoryImpl
import com.kuromelabs.kurome.domain.repository.DeviceRepository
import com.kuromelabs.kurome.domain.use_case.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDeviceDatabase(app: Application): DeviceDatabase {
        return Room.databaseBuilder(
            app,
            DeviceDatabase::class.java,
            DeviceDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideDeviceRepository(database: DeviceDatabase): DeviceRepository {
        return DeviceRepositoryImpl(database.deviceDao)
    }

    @Provides
    @Singleton
    fun provideDeviceUseCases(repository: DeviceRepository): DeviceUseCases {
        return DeviceUseCases(
            getDevices = GetDevices(repository),
            pairDevice = PairDevice(repository),
            unpairDevice = UnpairDevice(repository),
            getDevice = GetDevice(repository)
        )
    }
}
