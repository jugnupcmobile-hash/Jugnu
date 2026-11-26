package com.example.btchat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.btchat.domain.BluetoothDeviceDomain
import com.example.btchat.domain.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isBluetoothEnabled: Boolean,
    conversations: List<ChatMessage>,
    onOpenDiscovery: () -> Unit,
    onSelectConversation: (BluetoothDeviceDomain) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("BT Chat") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenDiscovery,
                icon = { Icon(Icons.Default.BluetoothSearching, contentDescription = null) },
                text = { Text("Find Devices") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isBluetoothEnabled) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Bluetooth is turned off. Enable Bluetooth to chat with nearby devices.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No conversations yet.\nTap 'Find Devices' to connect nearby.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Top
                ) {
                    items(conversations, key = { it.deviceAddress }) { lastMsg ->
                        ListItem(
                            headlineContent = { Text(lastMsg.deviceName) },
                            supportingContent = {
                                Text(
                                    text = lastMsg.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                Text(
                                    text = formatTime(lastMsg.timestamp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.clickable {
                                onSelectConversation(
                                    BluetoothDeviceDomain(
                                        name = lastMsg.deviceName,
                                        address = lastMsg.deviceAddress
                                    )
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
