package com.kuromelabs.kurome.application.interfaces

interface IdentityProvider {
    fun getEnvironmentName(): String
    fun getEnvironmentId(): String
}