package com.kuromelabs.kurome.di

import com.kuromelabs.kurome.application.interfaces.DeviceAccessorFactory
import com.kuromelabs.kurome.application.interfaces.DeviceAccessorImplFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceAccessorModule {
    @Binds
    abstract fun bindDeviceAccessorFactory(factory: DeviceAccessorImplFactory): DeviceAccessorFactory
}