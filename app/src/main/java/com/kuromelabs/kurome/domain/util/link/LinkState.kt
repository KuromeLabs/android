package com.kuromelabs.kurome.domain.util.link

data class LinkState (
    val state: State,
    val link: Link
) {
    enum class State { CONNECTED, DISCONNECTED }
}
