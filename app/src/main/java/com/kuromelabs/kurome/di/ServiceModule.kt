package com.kuromelabs.kurome.di

import com.kuromelabs.kurome.application.interfaces.LinkProvider
import com.kuromelabs.kurome.application.use_case.device.DeviceUseCases
import com.kuromelabs.kurome.background.DeviceConnectionHandler
import com.kuromelabs.kurome.background.TcpListenerService
import com.kuromelabs.kurome.background.UdpListenerService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.CoroutineScope
import java.net.Socket

@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @ServiceScoped
    @Provides
    fun provideTcpListenerService(scope: CoroutineScope): TcpListenerService {
        return TcpListenerService(scope)
    }

    @ServiceScoped
    @Provides
    fun provideUdpListenerService(
        scope: CoroutineScope, handler: DeviceConnectionHandler
    ): UdpListenerService {
        return UdpListenerService(scope, handler)
    }

    @ServiceScoped
    @Provides
    fun provideDeviceConnectionHandler(
        linkProvider: LinkProvider<Socket>,
        deviceUseCases: DeviceUseCases
    ): DeviceConnectionHandler {
        return DeviceConnectionHandler(linkProvider, deviceUseCases)
    }
}