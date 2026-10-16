package com.obddiag.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun ModulesScreen(vm: ObdViewModel) {
    val context = LocalContext.current
    val loading by vm.isLoading
    val modules by vm.modules
    val labels by vm.moduleLabels
    val dtcs by vm.moduleDtcs
    val info by vm.moduleInfo
    val notes by vm.moduleNotes
    val progress by vm.scanProgress

    var from by remember { mutableStateOf("700") }
    var to by remember { mutableStateOf("7FF") }
    var clearTarget by remember { mutableStateOf<ModuleHit?>(null) }
    var renameTarget by remember { mutableStateOf<ModuleHit?>(null) }
    var renameText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text(
                "Cherche tous les calculateurs qui répondent sur le bus CAN (toutes marques compatibles " +
                    "UDS / KWP2000 sur CAN), puis lit leurs défauts et leur identification. " +
                    "Le scan n'envoie qu'un message « présence » : il n'écrit rien dans le véhicule.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = from, onValueChange = { from = it.uppercase().take(3) },
                    label = { Text("De (hexa)") }, singleLine = true, modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = to, onValueChange = { to = it.uppercase().take(3) },
                    label = { Text("À (hexa)") }, singleLine = true, modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Text(
                "700 à 7FF couvre la plupart des véhicules. Si rien n'apparaît, essayez 600 à 6FF " +
                    "(ou 500 à 5FF). Compter environ 40 secondes pour 256 adresses.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val f = from.toIntOrNull(16)
                        val t = to.toIntOrNull(16)
                        if (f != null && t != null) vm.scanModules(f, t)
                    },
                    enabled = !loading
                ) { Text("Scanner les modules") }
                if (loading && progress.isNotEmpty()) {
                    OutlinedButton(onClick = { vm.cancelScan() }) { Text("Annuler") }
                }
            }
        }
        if (progress.isNotEmpty()) {
            item { Text(progress, style = MaterialTheme.typography.bodySmall) }
        }
        if (modules.isNotEmpty()) {
            item {
                OutlinedButton(onClick = {
                    Exporter.share(context, "modules-obd", "txt", "text/plain", vm.modulesReport())
                }) { Text("Exporter le rapport") }
            }
        }

        items(modules, key = { it.requestId }) { hit ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(vm.moduleName(hit), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "requête ${hit.requestId} → réponse ${hit.responseId}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    info[hit.requestId]?.forEach { InfoRow(it.label, it.value) }

                    val d = dtcs[hit.requestId]
                    if (d != null) {
                        Text(
                            if (d.isEmpty()) "Aucun défaut" else "${d.size} défaut(s) :",
                            style = MaterialTheme.typography.titleSmall
                        )
                        d.forEach { code ->
                            val base = code.substringBefore('-').substringBefore(' ')
                            Text(code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                            val desc = DtcCodes.describe(base)
                            if (!desc.startsWith("Description absente")) {
                                Text(desc, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    notes[hit.requestId]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.readModuleDtcs(hit) }, enabled = !loading) { Text("Défauts") }
                        TextButton(onClick = { vm.readModuleInfo(hit) }, enabled = !loading) { Text("Infos") }
                        TextButton(onClick = { clearTarget = hit }, enabled = !loading) { Text("Effacer") }
                        TextButton(onClick = {
                            renameTarget = hit
                            renameText = labels[hit.requestId] ?: ""
                        }) { Text("Nom") }
                    }
                }
            }
        }
    }

    val clear = clearTarget
    if (clear != null) {
        AlertDialog(
            onDismissRequest = { clearTarget = null },
            title = { Text("Effacer les défauts de ce module ?") },
            text = {
                Text(
                    "Les défauts de « ${vm.moduleName(clear)} » seront effacés. Notez-les avant : un défaut " +
                        "réel reviendra, et un module de sécurité (airbag, ABS) doit rester sans défaut " +
                        "après la réparation. Véhicule à l'arrêt, contact mis."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clearTarget = null
                    vm.clearModuleDtcs(clear)
                }) { Text("Effacer") }
            },
            dismissButton = { TextButton(onClick = { clearTarget = null }) { Text("Annuler") } }
        )
    }

    val rename = renameTarget
    if (rename != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Nom du module ${rename.requestId}") },
            text = {
                OutlinedTextField(
                    value = renameText, onValueChange = { renameText = it },
                    label = { Text("Ex. ABS, Airbag, BSI...") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameModule(rename, renameText)
                    renameTarget = null
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Annuler") } }
        )
    }
}
