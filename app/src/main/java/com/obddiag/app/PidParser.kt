package com.obddiag.app

/**
 * Définition des PIDs standards (Mode 01) les plus utiles pour un affichage temps réel,
 * avec leur formule de conversion (formules SAE J1979 standard).
 */
data class LivePid(
    val name: String,
    val pid: String,     // ex "0C" pour le régime moteur
    val unit: String,
    val convert: (List<Int>) -> Double // reçoit les octets de données (après 41 XX)
)

object PidParser {

    val supportedPids = listOf(
        LivePid("Régime moteur", "0C", "tr/min") { b -> ((b[0] * 256) + b[1]) / 4.0 },
        LivePid("Vitesse véhicule", "0D", "km/h") { b -> b[0].toDouble() },
        LivePid("Température liquide de refroidissement", "05", "°C") { b -> b[0] - 40.0 },
        LivePid("Position papillon", "11", "%") { b -> b[0] * 100.0 / 255.0 },
        LivePid("Température air admission", "0F", "°C") { b -> b[0] - 40.0 },
        LivePid("Charge moteur calculée", "04", "%") { b -> b[0] * 100.0 / 255.0 },
        LivePid("Niveau de carburant", "2F", "%") { b -> b[0] * 100.0 / 255.0 },
        LivePid("Tension batterie module", "42", "V") { b -> ((b[0] * 256) + b[1]) / 1000.0 }
    )

    /**
     * Extrait les octets de données utiles d'une réponse brute ELM327 du type
     * "41 0C 1A F8" -> [0x1A, 0xF8]
     */
    fun extractDataBytes(raw: String, pid: String): List<Int>? {
        val tokens = raw.trim().split(Regex("\\s+"))
            .filter { it.matches(Regex("[0-9A-Fa-f]{2}")) }

        val pidIndex = tokens.indexOfFirst {
            it.equals("41", ignoreCase = true)
        }
        if (pidIndex == -1 || pidIndex + 1 >= tokens.size) return null
        if (!tokens[pidIndex + 1].equals(pid, ignoreCase = true)) return null

        return tokens.drop(pidIndex + 2).map { it.toInt(16) }
    }
}
