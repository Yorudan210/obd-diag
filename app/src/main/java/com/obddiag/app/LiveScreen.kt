package com.obddiag.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun LiveScreen(vm: ObdViewModel) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val selected by vm.selectedPids
    val supported by vm.supportedPids
    val std by vm.liveStd
    val customs by vm.customPids
    val selectedCustom by vm.selectedCustom
    val liveCustom by vm.liveCustom
    val recording by vm.isRecording
    val rows by vm.recordedRows

    DisposableEffect(Unit) {
        vm.startLive()
        onDispose { vm.stopLive() }
    }

    // PIDs affichables : ceux que le véhicule déclare (ou tout le catalogue si la détection a échoué)
    val available = PidCatalog.all.filter { supported.isEmpty() || it.pid in supported }
    val active = available.filter { it.pid in selected }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picking = !picking }) {
                Text(if (picking) "Voir les valeurs" else "Choisir les mesures")
            }
            OutlinedButton(onClick = { vm.setRecording(!recording) }) {
                Text(if (recording) "Arrêter ($rows)" else "Enregistrer")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { Exporter.share(context, "mesures-obd", "csv", "text/csv", vm.recordingCsv()) },
                enabled = rows > 0
            ) { Text("Exporter CSV") }
            OutlinedButton(onClick = { vm.clearRecording() }, enabled = rows > 0) { Text("Vider") }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (picking) {
                PidCategory.values().forEach { cat ->
                    val inCat = available.filter { it.category == cat }
                    if (inCat.isNotEmpty()) {
                        item(key = "cat-${cat.name}") {
                            Text(cat.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(inCat, key = { "pid-${it.pid}" }) { p ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { vm.togglePid(p.pid) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = p.pid in selected, onCheckedChange = { vm.togglePid(p.pid) })
                                Text("${p.name}  (PID ${p.hex})", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (customs.isNotEmpty()) {
                    item(key = "cat-custom") {
                        Text("PIDs personnalisés", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(customs, key = { "custom-${it.name}" }) { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { vm.toggleCustom(c.name) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = c.name in selectedCustom, onCheckedChange = { vm.toggleCustom(c.name) })
                            Text("${c.name}  (${c.request})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                val shownCustom = customs.filter { it.name in selectedCustom }
                if (active.isEmpty() && shownCustom.isEmpty()) {
                    item { Text("Aucune mesure sélectionnée. Touchez « Choisir les mesures ».") }
                }
                items(active, key = { "v-${it.pid}" }) { p ->
                    val readings = std[p.pid]
                    if (readings == null) {
                        ValueCard(p.name, "…")
                    } else if (readings.isEmpty()) {
                        ValueCard(p.name, "non disponible")
                    } else {
                        readings.forEach { r -> ValueCard(r.label, "${formatValue(r.value)} ${r.unit}") }
                    }
                }
                items(shownCustom, key = { "vc-${it.name}" }) { c ->
                    val v = liveCustom[c.name]
                    ValueCard(c.name, if (v == null) "…" else "${formatValue(v)} ${c.unit}")
                }
            }
        }
    }
}

@Composable
private fun ValueCard(label: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
