package com.kuromelabs.kurome.application.devices

import Kurome.Fbs.Packet

interface Plugin {
    fun processPacket(packet: Packet)
}