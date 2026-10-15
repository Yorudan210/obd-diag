package com.obddiag.app

/**
 * PID défini par l'utilisateur (requête brute + équation), pour lire des données
 * constructeur (ex. FAP / injecteurs PSA) dont les identifiants ne sont pas
 * standardisés. Les valeurs à utiliser viennent de listes communautaires ou de
 * la documentation du calculateur : l'application ne les devine pas.
 *
 * @param request  requête hexa sans espaces, ex. "2101" ou "22F190"
 * @param header   en-tête CAN de la requête (ATSH), ex. "7E0" ; vide = défaut
 * @param rxId     filtre de réception CAN (ATCRA), ex. "7E8" ; vide = automatique
 * @param equation formule avec A,B,C... = octets après l'écho de la requête
 */
data class CustomPid(
    val name: String,
    val request: String,
    val equation: String,
    val unit: String = "",
    val header: String = "",
    val rxId: String = ""
) {
    /** Nombre d'octets d'écho de la réponse (mode + identifiant). */
    val echoBytes: Int get() = request.length / 2

    fun isValid(): Boolean =
        name.isNotBlank() && request.length >= 2 && request.length % 2 == 0 &&
            request.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' } &&
            equation.isNotBlank()

    /** Décode les octets de la réponse complète (incluant l'écho). */
    fun decode(message: List<Int>): Double? {
        val req = ObdParser.hexBytes(request) ?: return null
        if (message.size <= req.size || message[0] != req[0] + 0x40) return null
        return Formula.eval(equation, message.drop(req.size))
    }

    companion object {
        /**
         * Import d'un CSV au format Torque :
         * Name,ShortName,ModeAndPID,Equation,Min Value,Max Value,Unit,Header
         */
        fun parseTorqueCsv(text: String): List<CustomPid> {
            val out = mutableListOf<CustomPid>()
            for (line in text.lines()) {
                val l = line.trim()
                if (l.isEmpty()) continue
                val f = splitCsv(l)
                if (f.size < 4) continue
                if (f[0].equals("Name", ignoreCase = true)) continue
                val pid = CustomPid(
                    name = f[0].trim(),
                    request = f[2].trim().replace(" ", "").uppercase(),
                    equation = f[3].trim(),
                    unit = f.getOrElse(6) { "" }.trim(),
                    header = f.getOrElse(7) { "" }.trim().uppercase()
                )
                if (pid.isValid()) out.add(pid)
            }
            return out
        }

        fun splitCsv(line: String): List<String> {
            val res = mutableListOf<String>()
            val cur = StringBuilder()
            var q = false
            for (ch in line) {
                when {
                    ch == '"' -> q = !q
                    ch == ',' && !q -> { res.add(cur.toString()); cur.setLength(0) }
                    else -> cur.append(ch)
                }
            }
            res.add(cur.toString())
            return res
        }
    }
}
