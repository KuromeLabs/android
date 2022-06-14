package com.kuromelabs.kurome.application.use_case.device

import com.kuromelabs.kurome.application.interfaces.Link
import com.kuromelabs.kurome.application.interfaces.LinkProvider
import java.net.Socket

class Connect(var linkProvider: LinkProvider<Socket>) {
    suspend operator fun invoke(ip: String, port: Int): Result<Link> {
        return try {
            val link = linkProvider.createClientLink("$ip:$port")
            Result.success(link)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}