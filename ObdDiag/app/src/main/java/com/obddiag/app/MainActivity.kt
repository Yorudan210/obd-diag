package com.obddiag.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

enum class Screen { CONNECT, DTC, LIVE }

@Composable
fun AppRoot(viewModel: ObdViewModel) {
    var screen by remember { mutableStateOf(Screen.CONNECT) }
    val isConnected by viewModel.isConnected

    Scaffold(
        topBar = { TopAppBar(title = { Text("OBD Diag") }) },
        bottomBar = {
            if (isConnected) {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == Screen.DTC,
                        onClick = { screen = Screen.DTC },
                        icon = {},
                        label = { Text("Codes défauts") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.LIVE,
                        onClick = { screen = Screen.LIVE },
                        icon = {},
                        label = { Text("Temps réel") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = {
                            viewModel.disconnect()
                            screen = Screen.CONNECT
                        },
                        icon = {},
                        label = { Text("Déconnecter") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                !isConnected -> ConnectScreen(viewModel)
                screen == Screen.DTC -> DtcScreen(viewModel)
                screen == Screen.LIVE -> LiveDataScreen(viewModel)
                else -> ConnectScreen(viewModel)
            }
        }
    }
}

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
            "Le boîtier doit déjà être appairé via les réglages Bluetooth d'Android avant de continuer.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        Text(status)
        Spacer(Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator()
        }

        Spacer(Modifier.height(16.dp))

        if (!permissionsGranted) {
            Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                Text("Autoriser le Bluetooth")
            }
        } else {
            Button(onClick = { devices = viewModel.getPairedDevices() }) {
                Text("Rafraîchir la liste des appareils appairés")
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(devices) { device ->
                    ListItem(
                        headlineContent = { Text(device.name ?: "Appareil inconnu") },
                        supportingContent = { Text(device.address) },
                        modifier = Modifier.clickableRow { viewModel.connect(device) }
                    )
                    Divider()
                }
            }
        }
    }
}

// Petit helper pour rendre un ListItem cliquable sans dépendance supplémentaire
@Composable
fun Modifier.clickableRow(onClick: () -> Unit): Modifier {
    return this.then(
        Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun DtcScreen(viewModel: ObdViewModel) {
    val dtcs by viewModel.dtcList
    val loading by viewModel.isLoading
    val status by viewModel.statusMessage

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Codes défauts", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(status, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        Row {
            Button(onClick = { viewModel.readDtcs() }, enabled = !loading) {
                Text("Lire les codes")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { viewModel.clearDtcs() }, enabled = !loading) {
                Text("Effacer les codes")
            }
        }

        if (loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(dtcs) { dtc ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(dtc.code, style = MaterialTheme.typography.titleMedium)
                        Text(dtc.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveDataScreen(viewModel: ObdViewModel) {
    val values by viewModel.liveValues

    DisposableEffect(Unit) {
        viewModel.startLiveData()
        onDispose { viewModel.stopLiveData() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Données temps réel", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        LazyColumn {
            items(values.entries.toList()) { (name, pair) ->
                val (value, unit) = pair
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name)
                        Text("%.1f %s".format(value, unit), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
