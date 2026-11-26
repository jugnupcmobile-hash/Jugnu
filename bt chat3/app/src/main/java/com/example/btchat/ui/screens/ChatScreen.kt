package com.example.btchat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.btchat.domain.BluetoothDeviceDomain
import com.example.btchat.domain.ChatMessage
import com.example.btchat.domain.ConnectionState
import com.example.btchat.domain.MessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peer: BluetoothDeviceDomain,
    connectionState: ConnectionState,
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val isConnectedToPeer =
        connectionState is ConnectionState.Connected && connectionState.device.address == peer.address

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(peer.name, style = MaterialTheme.typography.titleMedium)
                        Text(peer.address, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isConnectedToPeer) {
                        IconButton(onClick = onDisconnect) {
                            Icon(Icons.Default.LinkOff, contentDescription = "Disconnect")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            ConnectionStatusBanner(
                peer = peer,
                state = connectionState,
                onReconnect = onReconnect
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (isConnectedToPeer) "Type a message..." else "Connect to send messages")
                    },
                    enabled = isConnectedToPeer,
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        onSendMessage(inputText)
                        inputText = ""
                    },
                    enabled = isConnectedToPeer && inputText.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusBanner(
    peer: BluetoothDeviceDomain,
    state: ConnectionState,
    onReconnect: () -> Unit
) {
    val (label, containerColor, contentColor, showReconnect) = when {
        state is ConnectionState.Connected && state.device.address == peer.address ->
            Quad("Connected", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, false)
        state is ConnectionState.Connecting && state.device.address == peer.address ->
            Quad("Connecting (attempt ${state.attempt})...", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, false)
        state is ConnectionState.Error ->
            Quad(state.message, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, true)
        else ->
            Quad("Disconnected (Viewing offline history)", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, true)
    }

    Surface(color = containerColor, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            if (showReconnect) {
                TextButton(onClick = onReconnect) {
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.isFromLocalUser) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isFromLocalUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(text = message.text, style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (message.isFromLocalUser) {
                        val (icon, tint) = when (message.status) {
                            MessageStatus.SENDING -> Icons.Default.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
                            MessageStatus.SENT -> Icons.Default.Check to MaterialTheme.colorScheme.primary
                            MessageStatus.FAILED -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = message.status.name,
                            tint = tint,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
