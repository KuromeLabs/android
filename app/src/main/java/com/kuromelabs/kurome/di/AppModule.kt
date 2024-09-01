package com.kuromelabs.kurome.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.kuromelabs.kurome.application.devices.DeviceRepository
import com.kuromelabs.kurome.application.interfaces.SecurityService
import com.kuromelabs.kurome.infrastructure.device.DefaultDeviceRepository
import com.kuromelabs.kurome.infrastructure.device.DeviceDatabase
import com.kuromelabs.kurome.infrastructure.device.DeviceService
import com.kuromelabs.kurome.infrastructure.device.IdentityProvider
import com.kuromelabs.kurome.infrastructure.network.NetworkService
import com.kuromelabs.kurome.infrastructure.network.SslService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    @Singleton // Provide always the same instance
    @Provides
    fun providesCoroutineScope(): CoroutineScope {
        // Run this code when providing an instance of CoroutineScope
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @Provides
    @Singleton
    fun provideDeviceService(
        scope: CoroutineScope,
        identityProvider: IdentityProvider,
        securityService: SecurityService<X509Certificate, KeyPair>,
        deviceRepository: DeviceRepository,
        networkService: NetworkService
    ): DeviceService {
        return DeviceService(scope, identityProvider, securityService, deviceRepository, networkService)
    }

    @Provides
    @Singleton
    fun provideIdentityProvider(@ApplicationContext context: Context): IdentityProvider {
        return IdentityProvider(context)
    }

    @Provides
    @Singleton
    fun provideNetworkService(
        scope: CoroutineScope,
        @ApplicationContext context: Context
    ): NetworkService {
        return NetworkService(scope, context)
    }

    @Provides
    @Singleton
    fun provideDeviceRepository(database: DeviceDatabase): DeviceRepository {
        return DefaultDeviceRepository(database.deviceDao)
    }

    @Singleton
    @Provides
    fun provideSecurityService(
        identityProvider: IdentityProvider
    ): SecurityService<X509Certificate, KeyPair> {
        return SslService(identityProvider)
    }
}
