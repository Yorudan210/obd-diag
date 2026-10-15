package com.obddiag.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VehicleScreen(vm: ObdViewModel) {
    val adapter by vm.adapter
    val protocol by vm.protocol
    val v by vm.vehicle
    val pids by vm.supportedPids
    val loading by vm.isLoading

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Button(onClick = { vm.refreshVehicle() }, enabled = !loading) {
                Text("Relire les informations")
            }
        }

        item {
            InfoCard(
                "Boîtier ELM327",
                listOf(
                    "Version" to (adapter?.version ?: "-"),
                    "Protocole" to protocol.ifEmpty { "-" },
                    "Tension mesurée" to (adapter?.voltage ?: "-")
                )
            )
        }

        item {
            InfoCard(
                "Véhicule",
                listOf(
                    "VIN" to (v.vin ?: "non fourni par le calculateur"),
                    "Carburant" to (v.fuelType ?: "-"),
                    "Calculateur" to (v.ecuNames.joinToString(", ").ifEmpty { "-" }),
                    "Référence logiciel" to (v.calIds.joinToString(", ").ifEmpty { "-" })
                )
            )
        }

        val st = v.status
        if (st != null) {
            item {
                InfoCard(
                    "Voyant moteur (MIL)",
                    listOf(
                        "Voyant" to if (st.milOn) "ALLUMÉ" else "éteint",
                        "Défauts confirmés" to st.dtcCount.toString(),
                        "Type de moteur" to if (st.diesel) "Diesel (allumage par compression)" else "Essence"
                    )
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Moniteurs d'autodiagnostic", style = MaterialTheme.typography.titleMedium)
                        val supported = st.monitors.filter { it.supported }
                        if (supported.isEmpty()) {
                            Text("Aucun moniteur déclaré", style = MaterialTheme.typography.bodySmall)
                        }
                        supported.forEach { m ->
                            InfoRow(m.name, if (m.complete) "Prêt" else "Incomplet")
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Mesures disponibles (OBD-II standard)", style = MaterialTheme.typography.titleMedium)
                    if (pids.isEmpty()) {
                        Text(
                            "Aucun PID détecté. Contact mis ? Essayez de relancer la connexion.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        val known = PidCatalog.all.count { it.pid in pids }
                        Text(
                            "${pids.size} PIDs déclarés par le calculateur, dont $known décodés par l'application.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            pids.sorted().joinToString(" ") { "%02X".format(it) },
                            style = MaterialTheme.typography.bodySmall
                        )
                        val hasFap = 0x7A in pids || 0x7C in pids
                        if (!hasFap) {
                            Text(
                                "Ce calculateur n'expose pas les données du FAP (pression différentielle, " +
                                    "températures) via l'OBD-II standard. Les valeurs spécifiques constructeur " +
                                    "(taux de colmatage, additif, correction injecteurs...) passent par un " +
                                    "protocole propriétaire : onglet Expert > PIDs perso.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
