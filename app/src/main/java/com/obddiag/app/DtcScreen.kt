package com.obddiag.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun DtcScreen(vm: ObdViewModel) {
    val context = LocalContext.current
    val loading by vm.isLoading
    val read by vm.dtcsRead
    val stored by vm.storedDtcs
    val pending by vm.pendingDtcs
    val permanent by vm.permanentDtcs
    val freeze by vm.freezeFrame
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.readAllDtcs() }, enabled = !loading) { Text("Lire les défauts") }
                OutlinedButton(onClick = { vm.readFreezeFrame() }, enabled = !loading) { Text("Image figée") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmClear = true }, enabled = !loading) { Text("Effacer") }
                OutlinedButton(
                    onClick = {
                        Exporter.share(context, "diagnostic-obd", "txt", "text/plain", vm.dtcReport())
                    },
                    enabled = read
                ) { Text("Exporter le rapport") }
            }
        }

        dtcSection("Défauts stockés (confirmés)", stored, read)
        dtcSection("Défauts en attente (non confirmés)", pending, read)
        dtcSection("Défauts permanents", permanent, read)

        val ff = freeze
        if (ff != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Image figée" + (ff.dtc?.let { " - défaut $it" } ?: ""),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Valeurs mesurées au moment où le défaut est apparu.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        ff.readings.forEach { r ->
                            InfoRow(r.label, "${formatValue(r.value)} ${r.unit}")
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer les défauts ?") },
            text = {
                Text(
                    "Cela efface les codes défauts, l'image figée et remet à zéro l'état des moniteurs " +
                        "(contrôle technique : le véhicule sera « non prêt » pendant quelques cycles de conduite). " +
                        "Notez les codes avant d'effacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearDtcs()
                }) { Text("Effacer") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } }
        )
    }
}

private fun LazyListScope.dtcSection(title: String, list: List<DtcInfo>, read: Boolean) {
    item {
        Text(
            if (read) "$title (${list.size})" else title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    if (!read) {
        item { Text("Pas encore lus.", style = MaterialTheme.typography.bodySmall) }
    } else if (list.isEmpty()) {
        item { Text("Aucun.", style = MaterialTheme.typography.bodySmall) }
    }
    items(list) { dtc ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(dtc.code, style = MaterialTheme.typography.titleMedium)
                Text(dtc.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
