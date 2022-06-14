package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.application.interfaces.DeviceAccessor
import com.kuromelabs.kurome.application.interfaces.DeviceAccessorFactory
import com.kuromelabs.kurome.application.interfaces.DeviceRepository
import com.kuromelabs.kurome.application.interfaces.Link
import com.kuromelabs.kurome.domain.Device
import kotlinx.coroutines.CoroutineScope

class Monitor(
    private val deviceAccessorFactory: DeviceAccessorFactory,
    val repository: DeviceRepository,
    var scope: CoroutineScope
) {
    operator fun invoke(link: Link, id: String, name: String): Result<DeviceAccessor> {
        val device = Device(id, name)
        val accessor = deviceAccessorFactory.create(link, device)
        accessor.start(scope)
        return Result.success(accessor)
    }
}