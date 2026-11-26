package com.example.btchat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.btchat.bluetooth.BluetoothChatController
import com.example.btchat.data.local.AppDatabase
import com.example.btchat.data.repository.ChatRepository
import com.example.btchat.domain.BluetoothDeviceDomain
import com.example.btchat.domain.ChatMessage
import com.example.btchat.domain.ConnectionState
import com.example.btchat.domain.MessageStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = ChatRepository(database.chatDao())
    private val bluetoothController = BluetoothChatController(application)

    val isBluetoothEnabled: StateFlow<Boolean> = bluetoothController.isBluetoothEnabled
    val isScanning: StateFlow<Boolean> = bluetoothController.isScanning
    val pairedDevices: StateFlow<List<BluetoothDeviceDomain>> = bluetoothController.pairedDevices
    val scannedDevices: StateFlow<List<BluetoothDeviceDomain>> = bluetoothController.scannedDevices
    val connectionState: StateFlow<ConnectionState> = bluetoothController.connectionState

    val recentConversations: StateFlow<List<ChatMessage>> = repository
        .observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPeer = MutableStateFlow<BluetoothDeviceDomain?>(null)
    val selectedPeer: StateFlow<BluetoothDeviceDomain?> = _selectedPeer.asStateFlow()

    val activeMessages: StateFlow<List<ChatMessage>> = _selectedPeer
        .flatMapLatest { peer ->
            if (peer == null) flowOf(emptyList())
            else repository.observeMessages(peer.address)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            bluetoothController.incomingMessages.collect { event ->
                repository.saveMessage(
                    ChatMessage(
                        deviceAddress = event.senderAddress,
                        deviceName = event.senderName,
                        text = event.text,
                        timestamp = System.currentTimeMillis(),
                        isFromLocalUser = false,
                        status = MessageStatus.SENT
                    )
                )
            }
        }
    }

    fun onPermissionsGranted() {
        bluetoothController.initialize()
    }

    fun startScan() = bluetoothController.startDiscovery()
    fun stopScan() = bluetoothController.stopDiscovery()
    fun refreshPaired() = bluetoothController.refreshPairedDevices()

    fun openConversation(device: BluetoothDeviceDomain, connectImmediately: Boolean) {
        _selectedPeer.value = device
        val currentState = connectionState.value
        val alreadyConnectedToPeer =
            currentState is ConnectionState.Connected && currentState.device.address == device.address
        if (connectImmediately && !alreadyConnectedToPeer) {
            bluetoothController.connectToDevice(device)
        }
    }

    fun respondToIncomingRequest(accept: Boolean) {
        val state = connectionState.value
        if (accept && state is ConnectionState.IncomingRequest) {
            _selectedPeer.value = state.device
        }
        bluetoothController.respondToIncomingRequest(accept)
    }

    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        val peer = _selectedPeer.value ?: return
        if (text.isEmpty()) return

        viewModelScope.launch {
            val id = repository.saveMessage(
                ChatMessage(
                    deviceAddress = peer.address,
                    deviceName = peer.name,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    isFromLocalUser = true,
                    status = MessageStatus.SENDING
                )
            )
            val sent = bluetoothController.sendMessage(text)
            repository.updateStatus(id, if (sent) MessageStatus.SENT else MessageStatus.FAILED)
        }
    }

    fun disconnect() = bluetoothController.disconnect()

    override fun onCleared() {
        super.onCleared()
        bluetoothController.release()
    }
}
