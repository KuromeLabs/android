package com.kuromelabs.kurome.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.kuromelabs.kurome.application.data_source.DeviceDatabase
import com.kuromelabs.kurome.application.flatbuffers.FlatBufferHelper
import com.kuromelabs.kurome.application.interfaces.*
import com.kuromelabs.kurome.application.use_case.device.*
import com.kuromelabs.kurome.infrastructure.device.DeviceAccessorFactoryImpl
import com.kuromelabs.kurome.infrastructure.device.IdentityProviderImpl
import com.kuromelabs.kurome.infrastructure.network.LinkProviderImpl
import com.kuromelabs.kurome.infrastructure.network.SslService
import com.kuromelabs.kurome.infrastructure.repository.DeviceRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import java.net.Socket
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
        linkProvider: LinkProvider<Socket>,
        deviceAccessorFactory: DeviceAccessorFactory,
        scope: CoroutineScope
    ): DeviceUseCases {
        return DeviceUseCases(
            getDevices = GetDevices(repository),
            pairDevice = PairDevice(repository),
            unpairDevice = UnpairDevice(repository),
            getDevice = GetDevice(repository),
            connect = Connect(linkProvider),
            monitor = Monitor(deviceAccessorFactory, repository, scope)
        )
    }

    @Provides
    @Singleton
    fun provideLinkProvider(
        identityProvider: IdentityProvider,
        securityService: SecurityService<X509Certificate, KeyPair>,
        flatBufferHelper: FlatBufferHelper
    ): LinkProvider<Socket> {
        return LinkProviderImpl(identityProvider, securityService, flatBufferHelper)
    }

    @Provides
    @Singleton
    fun provideIdentityProvider(@ApplicationContext context: Context): IdentityProvider {
        return IdentityProviderImpl(context)
    }

    @Singleton
    @Provides
    fun provideSecurityService(identityProvider: IdentityProvider): SecurityService<X509Certificate, KeyPair> {
        return SslService(identityProvider)
    }

    @Singleton
    @Provides
    fun provideDeviceAccessorFactory(identityProvider: IdentityProvider, scope: CoroutineScope, flatBufferHelper: FlatBufferHelper): DeviceAccessorFactory {
        return DeviceAccessorFactoryImpl(identityProvider, scope, flatBufferHelper)
    }

    @Singleton
    @Provides
    fun provideFlatBufferHelper(): FlatBufferHelper {
        return FlatBufferHelper()
    }
}
