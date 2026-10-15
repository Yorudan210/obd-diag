package com.obddiag.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
fun ExpertScreen(vm: ObdViewModel) {
    var sub by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = sub) {
            listOf("Console", "Calculateurs", "PIDs perso").forEachIndexed { i, t ->
                Tab(selected = sub == i, onClick = { sub = i }, text = { Text(t) })
            }
        }
        when (sub) {
            0 -> ConsolePane(vm)
            1 -> EcuPane(vm)
            else -> CustomPane(vm)
        }
    }
}

// Services UDS/KWP/OBD qui peuvent effacer, écrire ou piloter : confirmation obligatoire.
private val riskyServices = setOf(0x04, 0x08, 0x11, 0x14, 0x27, 0x2E, 0x2F, 0x31, 0x34, 0x35, 0x36, 0x37, 0x3D)

private fun isRisky(command: String): Boolean {
    val c = command.replace(" ", "")
    if (c.length < 2 || c.startsWith("AT", ignoreCase = true) || c.startsWith("ST", ignoreCase = true)) return false
    val sid = c.substring(0, 2).toIntOrNull(16) ?: return false
    return sid in riskyServices
}

@Composable
private fun ConsolePane(vm: ObdViewModel) {
    val context = LocalContext.current
    val log by vm.expertLog
    val loading by vm.isLoading
    var header by remember { mutableStateOf("") }
    var rx by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }
    var danger by remember { mutableStateOf<String?>(null) }

    fun send(command: String) {
        if (isRisky(command)) danger = command else vm.expertSend(header, rx, command)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Envoie des requêtes brutes au boîtier. En-tête et filtre sont facultatifs " +
                "(ex. en-tête 7E0, filtre 7E8). Réservé aux utilisateurs avertis.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = header, onValueChange = { header = it.uppercase() },
                label = { Text("En-tête (ATSH)") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = rx, onValueChange = { rx = it.uppercase() },
                label = { Text("Filtre (ATCRA)") }, singleLine = true, modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = cmd, onValueChange = { cmd = it },
            label = { Text("Commande (AT... ou hexa, ex. 0100)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { send(cmd) }, enabled = !loading && cmd.isNotBlank()) { Text("Envoyer") }
            OutlinedButton(onClick = { vm.expertRestoreHeader() }, enabled = !loading) { Text("Restaurer en-têtes") }
        }

        Text("Raccourcis (lecture seule)", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "ATRV" to "Tension boîtier",
                "ATDP" to "Protocole",
                "0100" to "PIDs 01-20",
                "18 02 FF 00" to "Défauts KWP",
                "19 02 FF" to "Défauts UDS"
            ).forEach { (c, label) ->
                OutlinedButton(onClick = { cmd = c; send(c) }, enabled = !loading) { Text(label) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { Exporter.share(context, "journal-obd", "txt", "text/plain", log.joinToString("\n")) },
                enabled = log.isNotEmpty()
            ) { Text("Partager le journal") }
            OutlinedButton(onClick = { vm.clearLog() }, enabled = log.isNotEmpty()) { Text("Effacer") }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                if (log.isEmpty()) "Journal vide." else log.joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    val pendingCmd = danger
    if (pendingCmd != null) {
        AlertDialog(
            onDismissRequest = { danger = null },
            title = { Text("Commande potentiellement modifiante") },
            text = {
                Text(
                    "La commande « $pendingCmd » peut effacer, écrire ou piloter des organes du véhicule. " +
                        "À n'utiliser que si vous savez exactement ce qu'elle fait : véhicule à l'arrêt, " +
                        "batterie chargée, sans interrompre la communication. Envoyer quand même ?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    danger = null
                    vm.expertSend(header, rx, pendingCmd)
                }) { Text("Envoyer") }
            },
            dismissButton = { TextButton(onClick = { danger = null }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun EcuPane(vm: ObdViewModel) {
    val hits by vm.ecuHits
    val protocol by vm.protocol
    val loading by vm.isLoading

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Protocole actif : ${protocol.ifEmpty { "inconnu" }}", style = MaterialTheme.typography.titleSmall)
        Text("Changer de protocole (ATSP) :", style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "0" to "Auto", "6" to "CAN 11b 500k", "7" to "CAN 29b 500k", "8" to "CAN 11b 250k",
                "9" to "CAN 29b 250k", "5" to "KWP rapide", "4" to "KWP 5 bauds", "3" to "ISO 9141"
            ).forEach { (n, label) ->
                OutlinedButton(onClick = { vm.expertSetProtocol(n) }, enabled = !loading) { Text(label) }
            }
        }
        Button(onClick = { vm.expertScan() }, enabled = !loading) { Text("Scanner les calculateurs OBD-II") }
        hits.forEach { h ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Calculateur ${h.id}", style = MaterialTheme.typography.titleSmall)
                    Text(h.payload, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(
            "Le scan utilise l'OBD-II (requête 0100) : seuls les calculateurs liés aux émissions " +
                "(moteur, boîte) répondent. ABS, airbags, BSI, climatisation... utilisent le protocole " +
                "constructeur : interrogez-les depuis la console avec leur en-tête (adresse CAN) et " +
                "le service de lecture de défauts (18 02 FF 00 ou 19 02 FF).",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CustomPane(vm: ObdViewModel) {
    val list by vm.customPids
    var name by remember { mutableStateOf("") }
    var request by remember { mutableStateOf("") }
    var equation by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var header by remember { mutableStateOf("") }
    var rx by remember { mutableStateOf("") }
    var csv by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Ajoutez des mesures spécifiques constructeur (FAP, injecteurs...) avec leur requête et leur " +
                "équation. L'application ne connaît pas ces identifiants : prenez-les dans une liste " +
                "fiable de votre moteur (ex. fichiers PIDs au format Torque). Équation : A, B, C = octets " +
                "de données après l'écho de la requête, ex. (A*256+B)/10.",
            style = MaterialTheme.typography.bodySmall
        )

        list.forEach { p ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "requête ${p.request} - en-tête ${p.header.ifEmpty { "défaut" }}" +
                            (if (p.rxId.isNotEmpty()) " - filtre ${p.rxId}" else "") +
                            "\nformule ${p.equation} ${p.unit}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { vm.deleteCustomPid(p.name) }) { Text("Supprimer") }
                }
            }
        }

        Text("Ajouter un PID", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Nom") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = request, onValueChange = { request = it.uppercase() },
                label = { Text("Requête (hexa)") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = unit, onValueChange = { unit = it },
                label = { Text("Unité") }, singleLine = true, modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = equation, onValueChange = { equation = it }, label = { Text("Équation") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = header, onValueChange = { header = it.uppercase() },
                label = { Text("En-tête (opt.)") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = rx, onValueChange = { rx = it.uppercase() },
                label = { Text("Filtre (opt.)") }, singleLine = true, modifier = Modifier.weight(1f)
            )
        }
        Button(onClick = {
            val p = CustomPid(name.trim(), request.trim(), equation.trim(), unit.trim(), header.trim(), rx.trim())
            if (p.copy(request = p.request.replace(" ", "")).isValid()) {
                vm.addCustomPid(p)
                message = "PID « ${p.name} » ajouté. Cochez-le dans Temps réel."
                name = ""; request = ""; equation = ""; unit = ""; header = ""; rx = ""
            } else {
                message = "PID invalide : nom, requête hexa (nombre pair de caractères) et équation requis."
            }
        }) { Text("Ajouter") }

        Text("Importer (CSV au format Torque)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Colonnes : Name, ShortName, ModeAndPID, Equation, Min Value, Max Value, Unit, Header",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = csv, onValueChange = { csv = it }, label = { Text("Coller le CSV ici") },
            minLines = 4, modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            val n = vm.importCustomCsv(csv)
            message = if (n > 0) "$n PID(s) importé(s)." else "Aucun PID valide trouvé dans ce texte."
            if (n > 0) csv = ""
        }, enabled = csv.isNotBlank()) { Text("Importer") }

        if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
    }
}
