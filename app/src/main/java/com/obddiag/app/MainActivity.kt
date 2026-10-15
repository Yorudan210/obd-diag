package com.obddiag.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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

class MainActivity : ComponentActivity() {

    private val viewModel: ObdViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRoot(viewModel)
            }
        }
    }
}

enum class MainTab(val title: String) {
    VEHICLE("Véhicule"),
    DTC("Défauts"),
    LIVE("Temps réel"),
    EXPERT("Expert")
}

@Composable
fun AppRoot(viewModel: ObdViewModel) {
    var tab by remember { mutableStateOf(MainTab.VEHICLE) }
    val connected by viewModel.isConnected
    val loading by viewModel.isLoading
    val status by viewModel.statusMessage

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OBD Diag") },
                actions = {
                    if (connected) {
                        TextButton(onClick = {
                            viewModel.disconnect()
                            tab = MainTab.VEHICLE
                        }) { Text("Déconnecter") }
                    }
                }
            )
        },
        bottomBar = {
            if (connected) {
                NavigationBar {
                    MainTab.values().forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {},
                            label = { Text(t.title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (connected) {
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (!connected) {
                    ConnectScreen(viewModel)
                } else when (tab) {
                    MainTab.VEHICLE -> VehicleScreen(viewModel)
                    MainTab.DTC -> DtcScreen(viewModel)
                    MainTab.LIVE -> LiveScreen(viewModel)
                    MainTab.EXPERT -> ExpertScreen(viewModel)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ConnectScreen(viewModel: ObdViewModel) {
    var permissionsGranted by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    val status by viewModel.statusMessage
    val loading by viewModel.isLoading

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.values.all { it }
        if (permissionsGranted) {
            devices = viewModel.getPairedDevices()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(requiredPermissions)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Connexion à l'ELM327", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Branchez le boîtier sur la prise OBD, mettez le contact, puis appairez-le au préalable " +
                "dans les réglages Bluetooth d'Android (code PIN 1234 ou 0000).",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        Text(status)
        Spacer(Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }

        if (!permissionsGranted) {
            Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                Text("Autoriser le Bluetooth")
            }
        } else {
            Button(onClick = { devices = viewModel.getPairedDevices() }, enabled = !loading) {
                Text("Rafraîchir la liste des appareils appairés")
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(devices) { device ->
                    ListItem(
                        headlineContent = { Text(device.name ?: "Appareil inconnu") },
                        supportingContent = { Text(device.address) },
                        modifier = Modifier.clickable(enabled = !loading) { viewModel.connect(device) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
