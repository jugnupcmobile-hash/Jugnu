package com.example.btchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.btchat.domain.ConnectionState
import com.example.btchat.ui.screens.ChatScreen
import com.example.btchat.ui.screens.DiscoveryScreen
import com.example.btchat.ui.screens.HomeScreen
import com.example.btchat.ui.screens.PermissionGate
import com.example.btchat.ui.theme.BTChatTheme
import com.example.btchat.ui.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BTChatTheme {
                BTChatApp()
            }
        }
    }
}

@Composable
private fun BTChatApp(viewModel: ChatViewModel = viewModel()) {
    val navController = rememberNavController()
    val isBtEnabled by viewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val pairedDevices by viewModel.pairedDevices.collectAsStateWithLifecycle()
    val scannedDevices by viewModel.scannedDevices.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val conversations by viewModel.recentConversations.collectAsStateWithLifecycle()
    val selectedPeer by viewModel.selectedPeer.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()

    PermissionGate(onAllGranted = { viewModel.onPermissionsGranted() }) {

        LaunchedEffect(connectionState) {
            if (connectionState is ConnectionState.Connected) {
                val route = navController.currentDestination?.route
                if (route != "chat") {
                    navController.navigate("chat")
                }
            }
        }

        val incoming = connectionState as? ConnectionState.IncomingRequest
        if (incoming != null) {
            AlertDialog(
                onDismissRequest = { viewModel.respondToIncomingRequest(false) },
                title = { Text("Incoming Chat Request") },
                text = { Text("${incoming.device.name} (${incoming.device.address}) wants to connect.") },
                confirmButton = {
                    TextButton(onClick = { viewModel.respondToIncomingRequest(true) }) {
                        Text("Accept")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.respondToIncomingRequest(false) }) {
                        Text("Decline")
                    }
                }
            )
        }

        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    isBluetoothEnabled = isBtEnabled,
                    conversations = conversations,
                    onOpenDiscovery = {
                        viewModel.refreshPaired()
                        navController.navigate("discovery")
                    },
                    onSelectConversation = { device ->
                        viewModel.openConversation(device, connectImmediately = false)
                        navController.navigate("chat")
                    }
                )
            }
            composable("discovery") {
                DiscoveryScreen(
                    isBluetoothEnabled = isBtEnabled,
                    isScanning = isScanning,
                    pairedDevices = pairedDevices,
                    scannedDevices = scannedDevices,
                    onStartScan = { viewModel.startScan() },
                    onStopScan = { viewModel.stopScan() },
                    onDeviceClick = { device ->
                        viewModel.openConversation(device, connectImmediately = true)
                        navController.navigate("chat")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("chat") {
                selectedPeer?.let { peer ->
                    ChatScreen(
                        peer = peer,
                        connectionState = connectionState,
                        messages = activeMessages,
                        onSendMessage = { viewModel.sendMessage(it) },
                        onReconnect = { viewModel.openConversation(peer, connectImmediately = true) },
                        onDisconnect = { viewModel.disconnect() },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
