package com.kuromelabs.kurome.application.interfaces

interface LinkProvider<T> {
    suspend fun createClientLink(connectionInfo: String): Link
    suspend fun createServerLink(client: T): Link
}