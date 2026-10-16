package com.obddiag.app

/** Octets d'une réponse décodée, plus quelques structures de résultat. */
data class Monitor(val name: String, val supported: Boolean, val complete: Boolean)

data class MonitorStatus(
    val milOn: Boolean,
    val dtcCount: Int,
    val diesel: Boolean,
    val monitors: List<Monitor>
)

data class FreezeFrame(val dtc: String?, val readings: List<Reading>)

data class EcuHit(val id: String, val payload: String)

/**
 * Décodage des réponses texte de l'ELM327 (avec ATH0, ATS1) en octets, puis en
 * structures OBD-II (SAE J1979). Pur Kotlin : testable hors Android.
 */
object ObdParser {

    private val MULTI = Regex("^([0-9A-Fa-f]):\\s*(.*)$")
    private val LENGTH = Regex("^[0-9A-Fa-f]{3}$")
    private val PAIR = Regex("^[0-9A-Fa-f]{2}$")

    // ---------- Texte brut -> lignes -> messages ----------

    fun cleanLines(raw: String): List<String> =
        raw.split('\r', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != ">" && !it.equals("SEARCHING...", ignoreCase = true) }

    /** Retourne le premier message d'erreur ELM327 trouvé, ou null. */
    fun errorOf(lines: List<String>): String? {
        for (l in lines) {
            val u = l.uppercase()
            if (u == "?") return "?"
            if (u.contains("NO DATA") || u.contains("UNABLE TO CONNECT") ||
                u.contains("ERROR") || u.contains("BUS BUSY") ||
                u.contains("BUFFER FULL") || u.contains("STOPPED") ||
                u.contains("LV RESET") || u.contains("ACT ALERT")
            ) return l
        }
        return null
    }

    fun hexBytes(text: String): List<Int>? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val tokens = t.split(Regex("\\s+"))
        if (tokens.size == 1 && t.length >= 4 && t.length % 2 == 0 && t.all { it.isHex() }) {
            return t.chunked(2).map { it.toInt(16) } // forme sans espaces
        }
        if (!tokens.all { PAIR.matches(it) }) return null
        return tokens.map { it.toInt(16) }
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /**
     * Reconstitue les messages OBD : trames simples (une ligne = un message) et
     * trames multiples ISO-TP ("0: ...", "1: ..." précédées d'une ligne de longueur).
     */
    fun parseMessages(lines: List<String>): List<List<Int>> {
        val messages = mutableListOf<MutableList<Int>>()
        var current: MutableList<Int>? = null
        for (line in lines) {
            val m = MULTI.matchEntire(line)
            if (m != null) {
                val idx = m.groupValues[1].toInt(16)
                val bytes = hexBytes(m.groupValues[2]) ?: emptyList()
                if (idx == 0 || current == null) {
                    current = mutableListOf()
                    messages.add(current)
                }
                current.addAll(bytes)
                continue
            }
            if (LENGTH.matches(line)) continue
            val bytes = hexBytes(line) ?: continue
            current = null
            messages.add(bytes.toMutableList())
        }
        return messages
    }

    // ---------- Mode 01 / 02 ----------

    /** Données d'un PID mode 01 (octets après "41 PID"), ou null. */
    fun pidData(messages: List<List<Int>>, pid: Int): List<Int>? {
        for (m in messages) if (m.size >= 2 && m[0] == 0x41 && m[1] == pid) return m.drop(2)
        return null
    }

    /** Union des bitmaps de PIDs supportés (PID 00/20/40...) de tous les calculateurs. */
    fun supportedPids(messages: List<List<Int>>, base: Int): Set<Int> {
        val out = mutableSetOf<Int>()
        for (m in messages) {
            if (m.size < 6 || m[0] != 0x41 || m[1] != base) continue
            for (i in 0 until 32) {
                val bit = (m[2 + i / 8] shr (7 - i % 8)) and 1
                if (bit == 1) out.add(base + i + 1)
            }
        }
        return out
    }

    fun monitorStatus(data: List<Int>): MonitorStatus? {
        if (data.size < 4) return null
        val a = data[0]; val b = data[1]; val c = data[2]; val d = data[3]
        val diesel = (b and 0x08) != 0
        val list = mutableListOf<Monitor>()
        fun cont(name: String, bit: Int) =
            list.add(Monitor(name, ((b shr bit) and 1) == 1, ((b shr (bit + 4)) and 1) == 0))
        cont("Ratés de combustion", 0)
        cont("Système d'alimentation", 1)
        cont("Composants", 2)
        val names = if (diesel) listOf(
            "Catalyseur NMHC", "Catalyseur NOx / SCR", null, "Pression de suralimentation",
            null, "Capteur gaz d'échappement", "Filtre à particules (FAP)", "EGR / VVT"
        ) else listOf(
            "Catalyseur", "Catalyseur chauffé", "EVAP", "Air secondaire",
            "Climatisation", "Sonde O2", "Chauffage sonde O2", "EGR"
        )
        for (bit in 0 until 8) {
            val n = names[bit] ?: continue
            list.add(Monitor(n, ((c shr bit) and 1) == 1, ((d shr bit) and 1) == 0))
        }
        return MonitorStatus((a and 0x80) != 0, a and 0x7F, diesel, list)
    }

    /** Réponse mode 02 : "42 PID FRAME data..." */
    fun freezeData(messages: List<List<Int>>, pid: Int): List<Int>? {
        for (m in messages) if (m.size >= 3 && m[0] == 0x42 && m[1] == pid) return m.drop(3)
        return null
    }

    // ---------- Codes défauts (modes 03 / 07 / 0A) ----------

    fun dtcFromBytes(b1: Int, b2: Int): String {
        val letter = when ((b1 shr 6) and 0x03) { 0 -> "P"; 1 -> "C"; 2 -> "B"; else -> "U" }
        val d1 = (b1 shr 4) and 0x03
        val d2 = b1 and 0x0F
        val d3 = (b2 shr 4) and 0x0F
        val d4 = b2 and 0x0F
        return "$letter$d1${d2.toString(16)}${d3.toString(16)}${d4.toString(16)}".uppercase()
    }

    /** mode = 3, 7 ou 0x0A. Gère l'octet de comptage des réponses CAN. */
    fun parseDtcs(messages: List<List<Int>>, mode: Int): List<String> {
        val resp = mode + 0x40
        val out = LinkedHashSet<String>()
        for (m in messages) {
            if (m.isEmpty() || m[0] != resp) continue
            var body = m.drop(1)
            if (body.size % 2 == 1) body = body.drop(1)
            var i = 0
            while (i + 1 < body.size) {
                val b1 = body[i]; val b2 = body[i + 1]
                i += 2
                if (b1 == 0 && b2 == 0) continue
                out.add(dtcFromBytes(b1, b2))
            }
        }
        return out.toList()
    }

    // ---------- Mode 09 ----------

    fun ascii(bytes: List<Int>): String =
        bytes.filter { it in 0x20..0x7E }.map { it.toChar() }.joinToString("")

    fun parseVin(messages: List<List<Int>>): String? {
        val parts = messages.filter { it.size >= 3 && it[0] == 0x49 && it[1] == 0x02 }
            .sortedBy { it[2] }
        if (parts.isEmpty()) return null
        val s = ascii(parts.flatMap { it.drop(3) })
        return if (s.length >= 17) s.take(17) else s.ifBlank { null }
    }

    private fun mode09Chunks(messages: List<List<Int>>, pid: Int, size: Int): List<String> {
        val out = mutableListOf<String>()
        for (m in messages) {
            if (m.size < 4 || m[0] != 0x49 || m[1] != pid) continue
            m.drop(3).chunked(size).forEach { c ->
                val s = ascii(c.filter { it != 0 }).trim()
                if (s.isNotEmpty()) out.add(s)
            }
        }
        return out.distinct()
    }

    fun parseCalIds(messages: List<List<Int>>) = mode09Chunks(messages, 0x04, 16)
    fun parseEcuNames(messages: List<List<Int>>) = mode09Chunks(messages, 0x0A, 20)

    // ---------- Expert : scan des calculateurs (ATH1) ----------

    fun parseEcuHits(lines: List<String>): List<EcuHit> {
        val out = linkedMapOf<String, String>()
        for (l in lines) {
            val t = l.trim().split(Regex("\\s+"))
            if (t.size < 2) continue
            val id = t[0]
            if ((id.length == 3 || id.length == 8) && id.all { it.isHex() } &&
                t.drop(1).all { PAIR.matches(it) }
            ) {
                if (id !in out) out[id] = t.drop(1).joinToString(" ")
            }
        }
        return out.map { EcuHit(it.key, it.value) }
    }

    // ---------- Modules : UDS (ISO 14229) / KWP2000 ----------

    /** Octet de statut d'un défaut UDS, en clair. */
    fun udsStatusText(status: Int): String {
        val parts = mutableListOf<String>()
        if (status and 0x08 != 0) parts.add("confirmé")
        if (status and 0x04 != 0) parts.add("en attente")
        if (status and 0x01 != 0) parts.add("test échoué")
        if (status and 0x80 != 0) parts.add("voyant demandé")
        return if (parts.isEmpty()) "inactif (statut %02X)".format(status) else parts.joinToString(", ")
    }

    /** 0x59 0x02 mask (DTC×3 + statut)...  ou  0x58 count (DTC×2 + statut)... */
    fun parseDiagDtcs(messages: List<List<Int>>): List<String> {
        val out = mutableListOf<String>()
        for (m in messages) {
            if (m.size >= 3 && m[0] == 0x59) {
                m.drop(3).chunked(4).filter { it.size == 4 }.forEach {
                    if (it[0] == 0 && it[1] == 0 && it[2] == 0) return@forEach
                    out.add("${dtcFromBytes(it[0], it[1])}-%02X [${udsStatusText(it[3])}]".format(it[2]))
                }
            } else if (m.size >= 2 && m[0] == 0x58) {
                m.drop(2).chunked(3).filter { it.size == 3 }.forEach {
                    if (it[0] == 0 && it[1] == 0) return@forEach
                    out.add("${dtcFromBytes(it[0], it[1])} [statut %02X]".format(it[2]))
                }
            }
        }
        return out
    }

    /** Réponse négative 7F : (service demandé, code de refus), ou null. */
    fun negativeResponse(messages: List<List<Int>>): Pair<Int, Int>? {
        for (m in messages) if (m.size >= 3 && m[0] == 0x7F) return m[1] to m[2]
        return null
    }

    fun nrcName(code: Int): String = when (code) {
        0x10 -> "refus général"
        0x11 -> "service non supporté"
        0x12 -> "sous-fonction non supportée"
        0x13 -> "longueur de message incorrecte"
        0x22 -> "conditions non remplies (contact, moteur, vitesse...)"
        0x31 -> "valeur hors plage / identifiant inconnu"
        0x33 -> "accès sécurisé requis"
        0x35 -> "clé invalide"
        0x78 -> "réponse en attente"
        0x7E -> "sous-fonction non supportée dans cette session"
        0x7F -> "service non supporté dans cette session"
        else -> "code %02X".format(code)
    }

    /**
     * Identifie le module qui a répondu à un TesterPresent (3E 00) sur une ligne
     * ATH1 : "7E8 02 7E 00" (positif) ou "7E8 03 7F 3E 11" (refus = module présent).
     */
    fun moduleResponder(lines: List<String>): String? {
        for (hit in parseEcuHits(lines)) {
            val b = hexBytes(hit.payload) ?: continue
            if (b.size >= 3 && b[1] == 0x7E) return hit.id
            if (b.size >= 4 && b[1] == 0x7F && b[2] == 0x3E) return hit.id
        }
        return null
    }

    /** Valeur texte d'un identifiant de données (service 22) : "62 F1 90 ...". */
    fun parseDid(messages: List<List<Int>>, did: Int): String? {
        for (m in messages) {
            if (m.size >= 4 && m[0] == 0x62 && m[1] == (did shr 8) && m[2] == (did and 0xFF)) {
                val s = ascii(m.drop(3).filter { it != 0 }).trim()
                return s.ifEmpty { null }
            }
        }
        return null
    }
}
