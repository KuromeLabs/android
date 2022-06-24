package com.kuromelabs.kurome.infrastructure.device

import com.kuromelabs.kurome.application.interfaces.DeviceAccessor
import com.kuromelabs.kurome.application.interfaces.DeviceAccessorFactory
import com.kuromelabs.kurome.application.interfaces.IdentityProvider
import com.kuromelabs.kurome.application.interfaces.Link
import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class DeviceAccessorFactoryImpl @Inject constructor(var identityProvider: IdentityProvider, var scope:CoroutineScope) :
    DeviceAccessorFactory {
    private val accessors = ConcurrentHashMap<String, DeviceAccessor>()
    override fun register(id: String, accessor: DeviceAccessor) {
        Timber.d("Registering device accessor for $id")
        accessors[id] = accessor
    }

    override fun unregister(id: String) {
        Timber.d("Unregistering device accessor for $id")
        accessors.remove(id)
    }

    override fun get(id: String): DeviceAccessor? {
        return accessors[id]
    }

    override fun create(link: Link, device: Device): DeviceAccessor {
        val accessor: DeviceAccessor =
            DeviceAccessorImpl(link, device, identityProvider, this, scope)
        register(device.id, accessor)
        return accessor
    }
}