package com.obddiag.app

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Un calculateur trouvé sur le bus CAN : adresse de requête et adresse de réponse. */
data class ModuleHit(val requestId: String, val responseId: String)

data class ModuleInfoLine(val label: String, val value: String)

data class AdapterInfo(val version: String, val protocol: String, val voltage: String)

data class VehicleInfo(
    val vin: String? = null,
    val calIds: List<String> = emptyList(),
    val ecuNames: List<String> = emptyList(),
    val fuelType: String? = null,
    val status: MonitorStatus? = null
)

/** Logique OBD-II de haut niveau au-dessus de [ObdConnection]. */
class ObdSession(val connection: ObdConnection) {

    var supported: Set<Int> = emptySet()
        private set

    /** Numéro de protocole ELM (ATDPN) sans le préfixe automatique, ex. "6". */
    var protocolNumber: String = "0"
        private set

    private var currentHeader: String? = null
    private var currentRx: String? = null

    private val protocolNames = mapOf(
        "1" to "SAE J1850 PWM", "2" to "SAE J1850 VPW", "3" to "ISO 9141-2",
        "4" to "ISO 14230-4 KWP (5 bauds)", "5" to "ISO 14230-4 KWP (rapide)",
        "6" to "ISO 15765-4 CAN (11 bits, 500 kbps)", "7" to "ISO 15765-4 CAN (29 bits, 500 kbps)",
        "8" to "ISO 15765-4 CAN (11 bits, 250 kbps)", "9" to "ISO 15765-4 CAN (29 bits, 250 kbps)",
        "A" to "SAE J1939 CAN"
    )

    // ---------- Initialisation ----------

    suspend fun initialize(): AdapterInfo {
        val c = connection
        c.send("ATZ", 5000)
        c.send("ATE0")
        c.send("ATL0")
        c.send("ATS1")   // espaces entre octets : indispensables au décodage
        c.send("ATH0")   // pas d'en-têtes CAN dans les réponses
        c.send("ATCAF1") // formatage automatique des trames CAN
        c.send("ATAT1")  // temporisation adaptative
        c.send("ATSP0")  // protocole automatique
        currentHeader = null
        currentRx = null
        val version = c.send("ATI").lines.firstOrNull { it.contains("ELM", true) } ?: "ELM327 (version inconnue)"
        return AdapterInfo(version, "À détecter", readVoltage())
    }

    suspend fun readVoltage(): String =
        connection.send("ATRV").lines.firstOrNull { it.any { ch -> ch.isDigit() } } ?: "?"

    suspend fun readProtocol(): String {
        val line = connection.send("ATDPN").lines.firstOrNull() ?: return "Inconnu"
        val num = line.trim().removePrefix("A").uppercase()
        protocolNumber = num.ifEmpty { "0" }
        return protocolNames[protocolNumber] ?: "Protocole $num"
    }

    // ---------- PIDs supportés (mode 01) ----------

    suspend fun detectSupportedPids(): Set<Int> {
        val found = mutableSetOf<Int>()
        var base = 0x00
        while (base <= 0xC0) {
            val reply = connection.send("01%02X".format(base), if (base == 0) 12000 else 4000)
            if (!reply.ok) break
            val sup = ObdParser.supportedPids(reply.messages, base)
            found.addAll(sup)
            if ((base + 0x20) !in sup) break
            base += 0x20
        }
        supported = found.filter { it % 0x20 != 0 }.toSet()
        return supported
    }

    /** Lit un PID standard ; null si aucune donnée. */
    suspend fun query(pid: LivePid): List<Reading>? {
        val reply = connection.send("01" + pid.hex, 2500)
        if (!reply.ok) return null
        val data = ObdParser.pidData(reply.messages, pid.pid) ?: return null
        if (data.size < pid.minBytes) return null
        return try { pid.decode(data) } catch (_: Exception) { null }
    }

    // ---------- Défauts ----------

    suspend fun readMonitorStatus(): MonitorStatus? {
        val reply = connection.send("0101")
        if (!reply.ok) return null
        return ObdParser.pidData(reply.messages, 0x01)?.let { ObdParser.monitorStatus(it) }
    }

    /** mode : 3 = stockés, 7 = en attente, 0x0A = permanents. */
    suspend fun readDtcs(mode: Int): Result<List<String>> {
        val reply = connection.send("%02X".format(mode), 6000)
        if (reply.error != null && reply.error.contains("NO DATA", true)) return Result.success(emptyList())
        if (!reply.ok) return Result.failure(Exception(reply.error))
        return Result.success(ObdParser.parseDtcs(reply.messages, mode))
    }

    suspend fun clearDtcs(): Boolean {
        val reply = connection.send("04", 6000)
        return reply.ok && reply.messages.any { it.isNotEmpty() && it[0] == 0x44 }
    }

    suspend fun readFreezeFrame(): FreezeFrame? {
        var dtc: String? = null
        val head = connection.send("020200")
        if (head.ok) {
            val d = ObdParser.freezeData(head.messages, 0x02)
            if (d != null && d.size >= 2 && !(d[0] == 0 && d[1] == 0)) dtc = ObdParser.dtcFromBytes(d[0], d[1])
        }
        val readings = mutableListOf<Reading>()
        for (p in PidCatalog.freezeFramePids) {
            if (supported.isNotEmpty() && p.pid !in supported) continue
            val r = connection.send("02%02X00".format(p.pid), 2500)
            if (!r.ok) continue
            val data = ObdParser.freezeData(r.messages, p.pid) ?: continue
            if (data.size < p.minBytes) continue
            try { readings.addAll(p.decode(data)) } catch (_: Exception) {}
        }
        if (dtc == null && readings.isEmpty()) return null
        return FreezeFrame(dtc, readings)
    }

    // ---------- Infos véhicule (mode 09) ----------

    suspend fun readVehicleInfo(): VehicleInfo {
        val status = readMonitorStatus()
        val vin = connection.send("0902", 10000).takeIf { it.ok }?.let { ObdParser.parseVin(it.messages) }
        val cal = connection.send("0904", 8000).takeIf { it.ok }?.let { ObdParser.parseCalIds(it.messages) } ?: emptyList()
        val ecu = connection.send("090A", 8000).takeIf { it.ok }?.let { ObdParser.parseEcuNames(it.messages) } ?: emptyList()
        val fuel = connection.send("0151").takeIf { it.ok }
            ?.let { ObdParser.pidData(it.messages, 0x51) }?.firstOrNull()?.let { PidCatalog.fuelType(it) }
        return VehicleInfo(vin, cal, ecu, fuel, status)
    }

    // ---------- En-têtes / PIDs personnalisés / Expert ----------

    /** Applique l'en-tête et le filtre de réception demandés (seulement s'ils changent). */
    suspend fun applyHeader(header: String, rx: String) {
        val h = header.trim().uppercase()
        val r = rx.trim().uppercase()
        if (h.isEmpty() && r.isEmpty()) {
            restoreHeader()
            return
        }
        if (h.isNotEmpty() && h != currentHeader) {
            connection.send("ATSH$h")
            currentHeader = h
        }
        if (r != (currentRx ?: "")) {
            connection.send(if (r.isEmpty()) "ATCRA" else "ATCRA$r")
            currentRx = r.ifEmpty { null }
        }
    }

    /** Revient aux en-têtes par défaut du protocole courant. */
    suspend fun restoreHeader() {
        if (currentHeader == null && currentRx == null) return
        if (currentRx != null) connection.send("ATCRA")
        when (protocolNumber) {
            "6", "8" -> connection.send("ATSH7DF")
            "7", "9" -> connection.send("ATSH18DB33F1")
            else -> connection.send("ATSP$protocolNumber")
        }
        currentHeader = null
        currentRx = null
    }

    suspend fun queryCustom(p: CustomPid): Double? {
        applyHeader(p.header, p.rxId)
        val reply = connection.send(p.request, 3000)
        if (!reply.ok) return null
        val req = ObdParser.hexBytes(p.request) ?: return null
        val msg = reply.messages.firstOrNull { it.isNotEmpty() && it[0] == req[0] + 0x40 } ?: return null
        return p.decode(msg)
    }

    suspend fun setProtocol(n: String): ElmReply {
        val r = connection.send("ATSP$n", 6000)
        currentHeader = null
        currentRx = null
        return r
    }

    suspend fun scanEcus(): List<EcuHit> {
        connection.send("ATH1")
        val reply = connection.send("0100", 12000)
        connection.send("ATH0")
        return ObdParser.parseEcuHits(reply.lines)
    }

    /** Requête brute pour la console Expert (en-tête et filtre optionnels, restaurés ensuite). */
    suspend fun expert(header: String, rx: String, command: String, timeoutMs: Long = 6000): ElmReply {
        applyHeader(header, rx)
        return connection.send(command.trim(), timeoutMs)
    }

    // ---------- Modules multi-marque (CAN 11 bits, UDS / KWP2000) ----------

    val isCan: Boolean get() = protocolNumber in setOf("6", "7", "8", "9")

    /** Remet les en-têtes OBD-II par défaut quoi qu'il se soit passé avant. */
    suspend fun resetHeaders() {
        connection.send("ATCRA")
        when (protocolNumber) {
            "6", "8" -> connection.send("ATSH7DF")
            "7", "9" -> connection.send("ATSH18DB33F1")
        }
        currentHeader = null
        currentRx = null
    }

    /**
     * Envoie un TesterPresent (3E 00) à chaque adresse de [from] à [to] et note
     * celles qui répondent (réponse positive ou refus : le module est là).
     * Inoffensif : aucune écriture, aucun changement de session.
     */
    suspend fun scanModules(from: Int, to: Int, onProgress: (done: Int, total: Int, id: String) -> Unit): List<ModuleHit> {
        val hits = mutableListOf<ModuleHit>()
        val total = to - from + 1
        connection.send("ATH1")
        connection.send("ATCRA")
        connection.send("ATST19") // attente courte (~100 ms) par adresse
        try {
            for (id in from..to) {
                currentCoroutineContext().ensureActive()
                if (id == 0x7DF) continue
                val idHex = "%03X".format(id)
                onProgress(id - from, total, idHex)
                connection.send("ATSH$idHex")
                val r = connection.send("3E00", 800)
                val responder = ObdParser.moduleResponder(r.lines)
                if (responder != null) hits.add(ModuleHit(idHex, responder))
            }
        } finally {
            withContext(NonCancellable) {
                connection.send("ATST32")
                connection.send("ATH0")
                resetHeaders()
            }
        }
        return hits
    }

    /** Requête vers un module ; relance quelques fois si le module répond « en attente » (7F xx 78). */
    private suspend fun moduleRequest(hit: ModuleHit, command: String, retryPending: Boolean = true): ElmReply {
        applyHeader(hit.requestId, hit.responseId)
        var reply = connection.send(command, 4000)
        var tries = 0
        while (retryPending && tries < 3 && ObdParser.negativeResponse(reply.messages)?.second == 0x78) {
            delay(700)
            reply = connection.send(command, 6000)
            tries++
        }
        return reply
    }

    private fun refusal(reply: ElmReply): String? {
        val neg = ObdParser.negativeResponse(reply.messages)
        if (neg != null) return ObdParser.nrcName(neg.second)
        val err = reply.error
        return if (err != null) (if (err.contains("NO DATA", true)) "pas de réponse du module" else err) else null
    }

    /** Lit les défauts d'un module : UDS (19 02 FF) puis, s'il ne comprend pas, KWP2000 (18 02 FF 00). */
    suspend fun readModuleDtcs(hit: ModuleHit): Result<List<String>> {
        var r = moduleRequest(hit, "1902FF")
        var neg = ObdParser.negativeResponse(r.messages)
        if (neg != null && (neg.second == 0x11 || neg.second == 0x12)) {
            r = moduleRequest(hit, "1802FF00")
            neg = ObdParser.negativeResponse(r.messages)
        }
        refusal(r)?.let { return Result.failure(Exception(it)) }
        if (r.messages.none { it.isNotEmpty() && (it[0] == 0x59 || it[0] == 0x58) }) {
            return Result.failure(Exception("réponse inattendue : ${r.lines.joinToString(" / ").take(80)}"))
        }
        return Result.success(ObdParser.parseDiagDtcs(r.messages))
    }

    /** Efface les défauts d'un module : UDS (14 FF FF FF) puis KWP2000 (14 FF 00). Sans nouvelle tentative. */
    suspend fun clearModuleDtcs(hit: ModuleHit): Result<Unit> {
        var r = moduleRequest(hit, "14FFFFFF", retryPending = false)
        var neg = ObdParser.negativeResponse(r.messages)
        if (neg != null && (neg.second == 0x11 || neg.second == 0x12 || neg.second == 0x13 || neg.second == 0x31)) {
            r = moduleRequest(hit, "14FF00", retryPending = false)
        }
        refusal(r)?.let { return Result.failure(Exception(it)) }
        return if (r.messages.any { it.isNotEmpty() && it[0] == 0x54 }) Result.success(Unit)
        else Result.failure(Exception("effacement non confirmé"))
    }

    private val standardDids = listOf(
        0xF190 to "VIN",
        0xF187 to "Référence pièce",
        0xF188 to "N° logiciel",
        0xF189 to "Version logiciel",
        0xF18A to "Équipementier",
        0xF18C to "N° de série",
        0xF191 to "N° matériel",
        0xF194 to "N° logiciel équipementier",
        0xF195 to "Version logiciel équipementier",
        0xF197 to "Nom du système"
    )

    /** Identification d'un module via les identifiants standard ISO 14229 (22 F1xx). */
    suspend fun readModuleInfo(hit: ModuleHit): List<ModuleInfoLine> {
        val out = mutableListOf<ModuleInfoLine>()
        var unsupported = 0
        for ((did, label) in standardDids) {
            val r = moduleRequest(hit, "22%04X".format(did))
            val v = ObdParser.parseDid(r.messages, did)
            if (v != null) {
                out.add(ModuleInfoLine(label, v))
                unsupported = 0
            } else if (ObdParser.negativeResponse(r.messages)?.second == 0x11) {
                out.add(ModuleInfoLine("Identification", "service 22 non supporté par ce module"))
                break
            } else if (++unsupported >= 5 && out.isEmpty()) {
                break
            }
        }
        if (out.isEmpty()) out.add(ModuleInfoLine("Identification", "aucun identifiant standard fourni"))
        return out
    }
}
