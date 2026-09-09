package com.obddiag.app

data class DtcInfo(val code: String, val description: String)

object DtcCodes {

    /**
     * Décode la réponse brute au mode 03 (ex: "43 01 33 00 00 00 00")
     * en une liste de codes DTC (ex: P0133).
     * Chaque code est encodé sur 2 octets après l'octet d'en-tête "43".
     */
    fun parseMode03(raw: String): List<String> {
        val bytes = raw
            .replace("43", "", ignoreCase = true)
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.matches(Regex("[0-9A-Fa-f]{2}")) }
            .map { it.toInt(16) }

        val codes = mutableListOf<String>()
        var i = 0
        while (i + 1 < bytes.size) {
            val b1 = bytes[i]
            val b2 = bytes[i + 1]
            i += 2

            if (b1 == 0 && b2 == 0) continue // padding, pas de code

            val letter = when ((b1 shr 6) and 0x03) {
                0 -> "P"
                1 -> "C"
                2 -> "B"
                else -> "U"
            }
            val digit1 = (b1 shr 4) and 0x03
            val digit2 = b1 and 0x0F
            val digit3 = (b2 shr 4) and 0x0F
            val digit4 = b2 and 0x0F

            codes.add("$letter$digit1${digit2.toString(16).uppercase()}${digit3.toString(16).uppercase()}${digit4.toString(16).uppercase()}")
        }
        return codes
    }

    /** Dictionnaire des codes DTC génériques les plus courants. Non exhaustif. */
    private val descriptions: Map<String, String> = mapOf(
        "P0100" to "Débit d'air massique (MAF) - circuit défaillant",
        "P0101" to "Débit d'air massique (MAF) - problème de plage/performance",
        "P0110" to "Capteur de température d'air d'admission - circuit défaillant",
        "P0115" to "Capteur de température du liquide de refroidissement - circuit défaillant",
        "P0120" to "Capteur de position papillon/pédale - circuit défaillant",
        "P0128" to "Thermostat - température de refroidissement sous le seuil",
        "P0130" to "Sonde à oxygène (Bank 1, capteur 1) - circuit défaillant",
        "P0171" to "Mélange trop pauvre (Bank 1)",
        "P0172" to "Mélange trop riche (Bank 1)",
        "P0174" to "Mélange trop pauvre (Bank 2)",
        "P0175" to "Mélange trop riche (Bank 2)",
        "P0200" to "Circuit injecteur - défaillant",
        "P0230" to "Circuit relais de pompe à carburant - défaillant",
        "P0300" to "Ratés d'allumage détectés - cylindres multiples/aléatoires",
        "P0301" to "Raté d'allumage détecté - cylindre 1",
        "P0302" to "Raté d'allumage détecté - cylindre 2",
        "P0303" to "Raté d'allumage détecté - cylindre 3",
        "P0304" to "Raté d'allumage détecté - cylindre 4",
        "P0335" to "Capteur de position vilebrequin (CKP) - circuit défaillant",
        "P0340" to "Capteur de position arbre à cames (CMP) - circuit défaillant",
        "P0400" to "Circuit de recirculation des gaz d'échappement (EGR) - anomalie",
        "P0401" to "Débit EGR insuffisant",
        "P0420" to "Efficacité du catalyseur sous le seuil (Bank 1)",
        "P0430" to "Efficacité du catalyseur sous le seuil (Bank 2)",
        "P0440" to "Circuit d'évaporation des vapeurs de carburant (EVAP) - anomalie",
        "P0442" to "Circuit EVAP - fuite de petite taille détectée",
        "P0455" to "Circuit EVAP - fuite de grande taille détectée",
        "P0500" to "Capteur de vitesse véhicule (VSS) - défaillant",
        "P0505" to "Système de régulation de ralenti - anomalie",
        "P0600" to "Circuit de communication série - défaillant",
        "P0601" to "Calculateur (ECM/PCM) - erreur mémoire interne",
        "P0700" to "Circuit de communication de la transmission - anomalie (voir codes boîte)",
        "P0715" to "Capteur de vitesse d'entrée/turbine - circuit défaillant",
        "P0740" to "Circuit d'embrayage du convertisseur de couple - anomalie"
    )

    fun describe(code: String): String =
        descriptions[code] ?: "Description non disponible dans le dictionnaire local — rechercher \"$code\" en ligne pour plus de détails."
}
