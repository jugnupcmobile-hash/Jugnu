package com.example.btchat.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.btchat.domain.BluetoothDeviceDomain
import com.example.btchat.domain.ConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

data class IncomingMessageEvent(
    val senderAddress: String,
    val senderName: String,
    val text: String
)

class BluetoothChatController(private val context: Context) {

    companion object {
        private const val SERVICE_NAME = "BTChatRFCOMM"
        private val SERVICE_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
        private const val HANDSHAKE_ACCEPT: Byte = 1
        private const val HANDSHAKE_DECLINE: Byte = 0
        private const val MAX_MESSAGE_BYTES = 1_048_576 // 1 MB safety cap
        private const val MAX_RECONNECT_ATTEMPTS = 3
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isBluetoothEnabled = MutableStateFlow(adapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceDomain>> = _pairedDevices.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDeviceDomain>> = _scannedDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<IncomingMessageEvent>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<IncomingMessageEvent> = _incomingMessages.asSharedFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var outputStream: DataOutputStream? = null

    private var serverJob: Job? = null
    private var connectionJob: Job? = null
    private var readJob: Job? = null

    private var pendingIncomingDecision: CompletableDeferred<Boolean>? = null
    private var lastInitiatedPeer: BluetoothDeviceDomain? = null
    @Volatile private var userRequestedDisconnect = false

    private var receiversRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = extractDevice(intent) ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        .takeIf { it != Short.MIN_VALUE }
                    val domain = device.toDomain(rssi = rssi)
                    _scannedDevices.update { current ->
                        val filtered = current.filterNot { it.address == domain.address }
                        filtered + domain
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val enabled = state == BluetoothAdapter.STATE_ON
                    _isBluetoothEnabled.value = enabled
                    if (enabled) {
                        refreshPairedDevices()
                        startServer()
                    } else {
                        _isScanning.value = false
                        disconnect(restartServer = false)
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    refreshPairedDevices()
                }
            }
        }
    }

    fun initialize() {
        if (!receiversRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(
                context,
                bluetoothReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            receiversRegistered = true
        }
        _isBluetoothEnabled.value = adapter?.isEnabled == true
        refreshPairedDevices()
        if (_isBluetoothEnabled.value) {
            startServer()
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        if (!hasConnectPermission()) return
        val bonded = adapter?.bondedDevices?.map { it.toDomain(isPaired = true) } ?: emptyList()
        _pairedDevices.value = bonded
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!hasScanPermission() || adapter?.isEnabled != true) return
        _scannedDevices.value = emptyList()
        if (adapter?.isDiscovering == true) {
            adapter?.cancelDiscovery()
        }
        val started = adapter?.startDiscovery() == true
        _isScanning.value = started
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (!hasScanPermission()) return
        if (adapter?.isDiscovering == true) {
            adapter?.cancelDiscovery()
        }
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun startServer() {
        if (!hasConnectPermission() || adapter?.isEnabled != true) return
        if (serverJob?.isActive == true) return

        serverJob = scope.launch {
            while (isActive && adapter?.isEnabled == true) {
                try {
                    serverSocket?.closeQuietly()
                    serverSocket = adapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                    if (_connectionState.value is ConnectionState.Disconnected) {
                        _connectionState.value = ConnectionState.Listening
                    }

                    val incomingSocket = serverSocket?.accept() ?: continue
                    val remoteDevice = incomingSocket.remoteDevice.toDomain()

                    val decision = CompletableDeferred<Boolean>()
                    pendingIncomingDecision = decision
                    _connectionState.value = ConnectionState.IncomingRequest(remoteDevice)

                    val accepted = decision.await()
                    pendingIncomingDecision = null

                    val out = DataOutputStream(incomingSocket.outputStream)
                    if (accepted) {
                        out.writeByte(HANDSHAKE_ACCEPT.toInt())
                        out.flush()
                        closeActiveConnectionOnly()
                        lastInitiatedPeer = null
                        userRequestedDisconnect = false
                        attachConnectedSocket(incomingSocket, remoteDevice)
                    } else {
                        out.writeByte(HANDSHAKE_DECLINE.toInt())
                        out.flush()
                        incomingSocket.closeQuietly()
                        if (activeSocket == null) {
                            _connectionState.value = ConnectionState.Listening
                        }
                    }
                } catch (_: IOException) {
                    if (!isActive) break
                    delay(1_000)
                }
            }
        }
    }

    fun respondToIncomingRequest(accept: Boolean) {
        pendingIncomingDecision?.complete(accept)
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDeviceDomain, attempt: Int = 1) {
        if (!hasConnectPermission() || adapter?.isEnabled != true) {
            _connectionState.value = ConnectionState.Error("Bluetooth is disabled or missing permission.")
            return
        }

        stopDiscovery()
        userRequestedDisconnect = false
        lastInitiatedPeer = device

        connectionJob?.cancel()
        connectionJob = scope.launch {
            closeActiveConnectionOnly()
            _connectionState.value = ConnectionState.Connecting(device, attempt)

            try {
                val remote = adapter?.getRemoteDevice(device.address)
                    ?: throw IOException("Device not found")
                val socket = remote.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()

                val input = DataInputStream(socket.inputStream)
                val response = input.readByte()

                if (response == HANDSHAKE_ACCEPT) {
                    attachConnectedSocket(socket, device)
                } else {
                    socket.closeQuietly()
                    lastInitiatedPeer = null
                    _connectionState.value = ConnectionState.Error("Connection declined by ${device.name}")
                }
            } catch (e: IOException) {
                if (!userRequestedDisconnect && attempt < MAX_RECONNECT_ATTEMPTS) {
                    delay(attempt * 1_500L)
                    connectToDevice(device, attempt + 1)
                } else {
                    _connectionState.value = ConnectionState.Error(
                        "Could not connect to ${device.name}: ${e.localizedMessage ?: "Out of range"}"
                    )
                }
            }
        }
    }

    private fun attachConnectedSocket(socket: BluetoothSocket, peer: BluetoothDeviceDomain) {
        activeSocket = socket
        outputStream = DataOutputStream(socket.outputStream)
        _connectionState.value = ConnectionState.Connected(peer)

        readJob?.cancel()
        readJob = scope.launch {
            val input = DataInputStream(socket.inputStream)
            try {
                while (isActive) {
                    val length = input.readInt()
                    if (length <= 0 || length > MAX_MESSAGE_BYTES) {
                        throw IOException("Invalid frame size: $length")
                    }
                    val buffer = ByteArray(length)
                    input.readFully(buffer)
                    val messageText = buffer.decodeToString()
                    _incomingMessages.emit(
                        IncomingMessageEvent(
                            senderAddress = peer.address,
                            senderName = peer.name,
                            text = messageText
                        )
                    )
                }
            } catch (_: IOException) {
                closeActiveConnectionOnly()
                val reconnectTarget = lastInitiatedPeer
                if (!userRequestedDisconnect && reconnectTarget != null && adapter?.isEnabled == true) {
                    connectToDevice(reconnectTarget, attempt = 1)
                } else {
                    _connectionState.value = if (adapter?.isEnabled == true) {
                        ConnectionState.Listening
                    } else {
                        ConnectionState.Disconnected
                    }
                }
            }
        }
    }

    suspend fun sendMessage(text: String): Boolean = withContext(Dispatchers.IO) {
        val stream = outputStream ?: return@withContext false
        return@withContext try {
            val bytes = text.encodeToByteArray()
            synchronized(stream) {
                stream.writeInt(bytes.size)
                stream.write(bytes)
                stream.flush()
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    fun disconnect(restartServer: Boolean = true) {
        userRequestedDisconnect = true
        lastInitiatedPeer = null
        pendingIncomingDecision?.complete(false)
        connectionJob?.cancel()
        closeActiveConnectionOnly()
        _connectionState.value = if (restartServer && adapter?.isEnabled == true) {
            startServer()
            ConnectionState.Listening
        } else {
            ConnectionState.Disconnected
        }
    }

    private fun closeActiveConnectionOnly() {
        readJob?.cancel()
        readJob = null
        outputStream = null
        activeSocket?.closeQuietly()
        activeSocket = null
    }

    fun release() {
        stopDiscovery()
        disconnect(restartServer = false)
        serverJob?.cancel()
        serverSocket?.closeQuietly()
        if (receiversRegistered) {
            runCatching { context.unregisterReceiver(bluetoothReceiver) }
            receiversRegistered = false
        }
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toDomain(
        rssi: Short? = null,
        isPaired: Boolean = bondState == BluetoothDevice.BOND_BONDED
    ): BluetoothDeviceDomain {
        val safeName = if (hasConnectPermission()) name ?: "Unknown Device" else "Unknown Device"
        return BluetoothDeviceDomain(
            name = safeName,
            address = address,
            rssi = rssi,
            isPaired = isPaired
        )
    }

    @Suppress("DEPRECATION")
    private fun extractDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else true

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun BluetoothSocket.closeQuietly() = runCatching { close() }
    private fun BluetoothServerSocket.closeQuietly() = runCatching { close() }
}
