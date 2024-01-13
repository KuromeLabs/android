package com.kuromelabs.kurome.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.kuromelabs.kurome.application.data_source.DeviceDatabase
import com.kuromelabs.kurome.application.interfaces.*
import com.kuromelabs.kurome.application.repository.DeviceRepository
import com.kuromelabs.kurome.application.use_case.device.*
import com.kuromelabs.kurome.infrastructure.network.SslService
import com.kuromelabs.kurome.application.repository.DeviceRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.KeyPair
import java.security.cert.X509Certificate
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
    fun provideDeviceUseCases(
        repository: DeviceRepository,
    ): DeviceUseCases {
        return DeviceUseCases(
            getSavedDevices = GetSavedDevices(repository),
            pairDevice = PairDevice(repository),
            unpairDevice = UnpairDevice(repository),
            getSavedDevice = GetSavedDevice(repository),
            getConnectedDevices = GetConnectedDevices(repository)
        )
    }

    @Singleton
    @Provides
    fun provideSecurityService(@ApplicationContext context: Context): SecurityService<X509Certificate, KeyPair> {
        return SslService(context)
    }
}
