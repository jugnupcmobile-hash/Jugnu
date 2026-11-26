package com.example.btchat.domain

enum class MessageStatus {
    SENDING, SENT, FAILED
}

data class BluetoothDeviceDomain(
    val name: String,
    val address: String,
    val rssi: Short? = null,
    val isPaired: Boolean = false
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Listening : ConnectionState
    data class Connecting(val device: BluetoothDeviceDomain, val attempt: Int = 1) : ConnectionState
    data class IncomingRequest(val device: BluetoothDeviceDomain) : ConnectionState
    data class Connected(val device: BluetoothDeviceDomain) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

data class ChatMessage(
    val id: Long = 0,
    val deviceAddress: String,
    val deviceName: String,
    val text: String,
    val timestamp: Long,
    val isFromLocalUser: Boolean,
    val status: MessageStatus
)
