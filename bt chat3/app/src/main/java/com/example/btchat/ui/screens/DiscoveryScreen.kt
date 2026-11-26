package com.example.btchat.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.btchat.domain.BluetoothDeviceDomain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    isBluetoothEnabled: Boolean,
    isScanning: Boolean,
    pairedDevices: List<BluetoothDeviceDomain>,
    scannedDevices: List<BluetoothDeviceDomain>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceClick: (BluetoothDeviceDomain) -> Unit,
    onBack: () -> Unit
) {
    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    DisposableEffect(Unit) {
        onDispose { onStopScan() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discover Devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isBluetoothEnabled) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        }
                    ) {
                        Text("Turn On Bluetooth")
                    }
                } else {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { if (isScanning) onStopScan() else onStartScan() }
                    ) {
                        Text(if (isScanning) "Stop Scan" else "Scan Nearby")
                    }

                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                            }
                            discoverableLauncher.launch(intent)
                        }
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Visible")
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    SectionHeader("Paired Devices (${pairedDevices.size})")
                }
                items(pairedDevices, key = { "paired_${it.address}" }) { device ->
                    DeviceRow(device = device, onClick = { onDeviceClick(device) })
                }

                item {
                    SectionHeader("Nearby Discovered Devices (${scannedDevices.size})")
                }
                items(scannedDevices, key = { "scanned_${it.address}" }) { device ->
                    DeviceRow(device = device, onClick = { onDeviceClick(device) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun DeviceRow(
    device: BluetoothDeviceDomain,
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
        headlineContent = { Text(device.name) },
        supportingContent = { Text(device.address) },
        trailingContent = {
            device.rssi?.let { rssi ->
                Text(
                    text = "$rssi dBm",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}
