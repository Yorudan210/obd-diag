package com.obddiag.app

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

class ObdViewModel(app: Application) : AndroidViewModel(app) {

    private val connection = ObdConnection()
    private val session = ObdSession(connection)
    private val store = CustomPidStore(app)

    // ---- Connexion ----
    var isConnected = mutableStateOf(false)
    var statusMessage = mutableStateOf("Non connecté")
    var isLoading = mutableStateOf(false)
    var adapter = mutableStateOf<AdapterInfo?>(null)
    var protocol = mutableStateOf("")

    // ---- Véhicule ----
    var vehicle = mutableStateOf(VehicleInfo())
    var supportedPids = mutableStateOf<Set<Int>>(emptySet())

    // ---- Défauts ----
    var dtcsRead = mutableStateOf(false)
    var storedDtcs = mutableStateOf<List<DtcInfo>>(emptyList())
    var pendingDtcs = mutableStateOf<List<DtcInfo>>(emptyList())
    var permanentDtcs = mutableStateOf<List<DtcInfo>>(emptyList())
    var freezeFrame = mutableStateOf<FreezeFrame?>(null)

    // ---- Temps réel ----
    var selectedPids = mutableStateOf(PidCatalog.defaultSelection)
    var liveStd = mutableStateOf<Map<Int, List<Reading>>>(emptyMap())
    var customPids = mutableStateOf(store.load())
    var selectedCustom = mutableStateOf<Set<String>>(emptySet())
    var liveCustom = mutableStateOf<Map<String, Double>>(emptyMap())
    var isRecording = mutableStateOf(false)
    var recordedRows = mutableStateOf(0)

    // ---- Expert ----
    var expertLog = mutableStateOf<List<String>>(emptyList())
    var ecuHits = mutableStateOf<List<EcuHit>>(emptyList())

    private var liveJob: Job? = null
    private val recording = mutableListOf<Pair<Long, Map<String, Double>>>()
    private var recordStart = 0L

    // ------------------------------------------------------------------
    // Connexion
    // ------------------------------------------------------------------

    fun getPairedDevices(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return connection.getPairedDevices(adapter)
    }

    @Suppress("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (isLoading.value) return
        isLoading.value = true
        statusMessage.value = "Connexion Bluetooth…"
        viewModelScope.launch {
            try {
                val result = connection.connect(device)
                if (result.isFailure) {
                    statusMessage.value = "Échec de connexion : ${result.exceptionOrNull()?.message}"
                    return@launch
                }
                isConnected.value = true
                statusMessage.value = "Initialisation du boîtier ELM327…"
                adapter.value = session.initialize()

                statusMessage.value = "Recherche du protocole et des PIDs (contact mis, jusqu'à ~15 s)…"
                val pids = session.detectSupportedPids()
                supportedPids.value = pids
                protocol.value = session.readProtocol()

                if (pids.isEmpty()) {
                    statusMessage.value =
                        "Boîtier connecté, mais le véhicule ne répond pas (contact mis ? moteur ?). " +
                            "Le mode Expert reste utilisable."
                } else {
                    statusMessage.value = "Lecture des informations du véhicule…"
                    vehicle.value = session.readVehicleInfo()
                    adapter.value = adapter.value?.copy(protocol = protocol.value)
                    statusMessage.value = "Connecté à ${device.name ?: device.address} - ${pids.size} PIDs supportés"
                }
            } catch (e: Exception) {
                handleIo(e)
            } finally {
                isLoading.value = false
            }
        }
    }

    fun disconnect() {
        stopLive()
        connection.disconnect()
        isConnected.value = false
        adapter.value = null
        vehicle.value = VehicleInfo()
        supportedPids.value = emptySet()
        dtcsRead.value = false
        storedDtcs.value = emptyList()
        pendingDtcs.value = emptyList()
        permanentDtcs.value = emptyList()
        freezeFrame.value = null
        statusMessage.value = "Déconnecté"
    }

    private fun handleIo(e: Exception) {
        if (e is IOException || !connection.isConnected) {
            stopLive()
            connection.disconnect()
            isConnected.value = false
            statusMessage.value = "Connexion perdue : ${e.message ?: "erreur Bluetooth"}"
        } else {
            statusMessage.value = "Erreur : ${e.message}"
        }
    }

    private fun op(message: String, block: suspend () -> Unit) {
        if (!connection.isConnected || isLoading.value) return
        isLoading.value = true
        statusMessage.value = message
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                handleIo(e)
            } finally {
                isLoading.value = false
            }
        }
    }

    // ------------------------------------------------------------------
    // Véhicule
    // ------------------------------------------------------------------

    fun refreshVehicle() = op("Lecture des informations du véhicule…") {
        val volt = session.readVoltage()
        adapter.value = adapter.value?.copy(voltage = volt)
        vehicle.value = session.readVehicleInfo()
        statusMessage.value = "Informations mises à jour"
    }

    // ------------------------------------------------------------------
    // Défauts
    // ------------------------------------------------------------------

    private fun toInfo(codes: List<String>) = codes.map { DtcInfo(it, DtcCodes.describe(it)) }

    fun readAllDtcs() = op("Lecture des codes défauts…") {
        val stored = session.readDtcs(3)
        val pending = session.readDtcs(7)
        val permanent = session.readDtcs(0x0A)
        storedDtcs.value = toInfo(stored.getOrDefault(emptyList()))
        pendingDtcs.value = toInfo(pending.getOrDefault(emptyList()))
        permanentDtcs.value = toInfo(permanent.getOrDefault(emptyList()))
        dtcsRead.value = true
        val st = session.readMonitorStatus()
        if (st != null) vehicle.value = vehicle.value.copy(status = st)
        val failure = stored.exceptionOrNull()
        statusMessage.value = when {
            failure != null -> "Erreur lecture défauts : ${failure.message}"
            storedDtcs.value.isEmpty() && pendingDtcs.value.isEmpty() && permanentDtcs.value.isEmpty() ->
                "Aucun code défaut"
            else -> "${storedDtcs.value.size} stocké(s), ${pendingDtcs.value.size} en attente, " +
                "${permanentDtcs.value.size} permanent(s)"
        }
    }

    fun readFreezeFrame() = op("Lecture de l'image figée…") {
        val ff = session.readFreezeFrame()
        freezeFrame.value = ff
        statusMessage.value = if (ff == null) "Aucune image figée enregistrée" else "Image figée lue"
    }

    fun clearDtcs() = op("Effacement des défauts…") {
        val ok = session.clearDtcs()
        if (ok) {
            storedDtcs.value = emptyList()
            pendingDtcs.value = emptyList()
            freezeFrame.value = null
            dtcsRead.value = true
            statusMessage.value = "Défauts effacés (relire pour vérifier qu'ils ne reviennent pas)"
        } else {
            statusMessage.value = "Le calculateur n'a pas confirmé l'effacement (moteur à l'arrêt, contact mis ?)"
        }
    }

    fun dtcReport(): String {
        val sb = StringBuilder("Rapport de diagnostic OBD\n")
        vehicle.value.vin?.let { sb.append("VIN : $it\n") }
        fun section(title: String, list: List<DtcInfo>) {
            sb.append("\n$title : ${if (list.isEmpty()) "aucun" else list.size.toString()}\n")
            list.forEach { sb.append("- ${it.code} : ${it.description}\n") }
        }
        section("Défauts stockés", storedDtcs.value)
        section("Défauts en attente", pendingDtcs.value)
        section("Défauts permanents", permanentDtcs.value)
        freezeFrame.value?.let { ff ->
            sb.append("\nImage figée${ff.dtc?.let { " (défaut $it)" } ?: ""} :\n")
            ff.readings.forEach { sb.append("- ${it.label} : ${fmt(it.value)} ${it.unit}\n") }
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // Temps réel
    // ------------------------------------------------------------------

    fun togglePid(pid: Int) {
        selectedPids.value = if (pid in selectedPids.value) selectedPids.value - pid else selectedPids.value + pid
    }

    fun toggleCustom(name: String) {
        selectedCustom.value =
            if (name in selectedCustom.value) selectedCustom.value - name else selectedCustom.value + name
    }

    fun startLive() {
        if (!connection.isConnected) return
        stopLive()
        liveJob = viewModelScope.launch {
            while (isActive) {
                val std = PidCatalog.all.filter {
                    it.pid in selectedPids.value && (supportedPids.value.isEmpty() || it.pid in supportedPids.value)
                }
                val customs = customPids.value.filter { it.name in selectedCustom.value }
                if (std.isEmpty() && customs.isEmpty()) {
                    delay(300)
                    continue
                }
                val row = LinkedHashMap<String, Double>()
                try {
                    for (p in std) {
                        if (!isActive) break
                        val r = session.query(p)
                        if (r != null) {
                            liveStd.value = liveStd.value + (p.pid to r)
                            r.forEach { row[it.label] = it.value }
                        }
                    }
                    for (c in customs) {
                        if (!isActive) break
                        val v = session.queryCustom(c)
                        if (v != null) {
                            liveCustom.value = liveCustom.value + (c.name to v)
                            row[c.name] = v
                        }
                    }
                    if (customs.isNotEmpty()) session.restoreHeader()
                } catch (e: Exception) {
                    handleIo(e)
                    break
                }
                if (isRecording.value && row.isNotEmpty() && recording.size < 50000) {
                    recording.add((System.currentTimeMillis() - recordStart) to row)
                    recordedRows.value = recording.size
                }
            }
        }
    }

    fun stopLive() {
        liveJob?.cancel()
        liveJob = null
    }

    fun setRecording(on: Boolean) {
        if (on && !isRecording.value) {
            if (recording.isEmpty()) recordStart = System.currentTimeMillis()
        }
        isRecording.value = on
    }

    fun clearRecording() {
        recording.clear()
        recordedRows.value = 0
        isRecording.value = false
    }

    fun recordingCsv(): String {
        val cols = LinkedHashSet<String>()
        recording.forEach { cols.addAll(it.second.keys) }
        val sb = StringBuilder()
        sb.append("temps_s")
        cols.forEach { sb.append(',').append('"').append(it.replace("\"", "'")).append('"') }
        sb.append('\n')
        for ((t, values) in recording) {
            sb.append(String.format(Locale.US, "%.2f", t / 1000.0))
            cols.forEach { c ->
                sb.append(',')
                values[c]?.let { sb.append(String.format(Locale.US, "%.3f", it)) }
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // PIDs personnalisés
    // ------------------------------------------------------------------

    fun addCustomPid(p: CustomPid) {
        val normalized = p.copy(
            request = p.request.replace(" ", "").uppercase(),
            header = p.header.trim().uppercase(),
            rxId = p.rxId.trim().uppercase()
        )
        if (!normalized.isValid()) {
            statusMessage.value = "PID personnalisé invalide (nom, requête hexa paire, équation)"
            return
        }
        customPids.value = customPids.value.filter { it.name != normalized.name } + normalized
        store.save(customPids.value)
    }

    fun importCustomCsv(text: String): Int {
        val list = CustomPid.parseTorqueCsv(text)
        if (list.isEmpty()) return 0
        val names = list.map { it.name }.toSet()
        customPids.value = customPids.value.filter { it.name !in names } + list
        store.save(customPids.value)
        return list.size
    }

    fun deleteCustomPid(name: String) {
        customPids.value = customPids.value.filter { it.name != name }
        selectedCustom.value = selectedCustom.value - name
        liveCustom.value = liveCustom.value - name
        store.save(customPids.value)
    }

    // ------------------------------------------------------------------
    // Expert
    // ------------------------------------------------------------------

    private fun log(vararg lines: String) {
        val all = expertLog.value + lines.toList()
        expertLog.value = if (all.size > 400) all.takeLast(400) else all
    }

    fun clearLog() { expertLog.value = emptyList() }

    private fun describeReply(r: ElmReply) {
        if (r.lines.isEmpty()) log("< (aucune réponse)") else r.lines.forEach { log("< $it") }
        val dtcs = ObdParser.parseDiagDtcs(r.messages)
        if (dtcs.isNotEmpty()) dtcs.forEach { log("  ↳ défaut $it") }
        r.messages.firstOrNull { it.size >= 3 && it[0] == 0x7F }?.let {
            log("  ↳ refus négatif : service %02X, code %02X".format(it[1], it[2]))
        }
    }

    fun expertSend(header: String, rx: String, command: String) {
        val cmd = command.trim()
        if (cmd.isEmpty()) return
        op("Commande envoyée…") {
            log("> ${if (header.isNotBlank()) "[${header.trim()}] " else ""}$cmd")
            describeReply(session.expert(header, rx, cmd))
            statusMessage.value = "Réponse reçue"
        }
    }

    fun expertRestoreHeader() = op("Restauration des en-têtes…") {
        session.restoreHeader()
        log("En-têtes restaurés (protocole ${session.protocolNumber})")
    }

    fun expertSetProtocol(n: String) = op("Changement de protocole…") {
        log("> ATSP$n")
        describeReply(session.setProtocol(n))
        protocol.value = session.readProtocol()
        log("Protocole actif : ${protocol.value}")
    }

    fun expertScan() = op("Recherche des calculateurs…") {
        val hits = session.scanEcus()
        ecuHits.value = hits
        log("Scan : ${hits.size} calculateur(s) répondant en OBD-II")
        hits.forEach { log("  ${it.id} : ${it.payload}") }
    }

    private fun fmt(v: Double): String = String.format(Locale.FRANCE, "%.1f", v)

    override fun onCleared() {
        super.onCleared()
        liveJob?.cancel()
        connection.disconnect()
    }
}
