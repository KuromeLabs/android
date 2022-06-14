package com.kuromelabs.kurome.application.interfaces

interface SecurityService<T, P> {
    fun getSecurityContext(): T
    fun getKeys(): P?
}