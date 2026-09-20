package com.kuromelabs.kurome.infrastructure.network

/**
 * A peer advertised over mDNS as `_kurome._tcp`.
 */
data class DiscoveredDevice(
    val id: String,
    val name: String,
    val addresses: List<String>,
    val port: Int,
    val serviceName: String = id,
)
