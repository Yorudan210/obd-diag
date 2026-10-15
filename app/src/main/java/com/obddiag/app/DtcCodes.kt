package com.obddiag.app

data class DtcInfo(val code: String, val description: String)

object DtcCodes {

    /** Dictionnaire des codes génériques SAE courants (essence + diesel). Non exhaustif. */
    private val descriptions: Map<String, String> = mapOf(
        // Alimentation / air
        "P0016" to "Corrélation vilebrequin / arbre à cames (banc 1, capteur A)",
        "P0045" to "Électrovanne de commande de suralimentation A - circuit ouvert",
        "P0046" to "Électrovanne de commande de suralimentation A - plage/performance",
        "P0047" to "Électrovanne de commande de suralimentation A - signal bas",
        "P0048" to "Électrovanne de commande de suralimentation A - signal haut",
        "P0087" to "Pression de rampe / système carburant trop basse",
        "P0088" to "Pression de rampe / système carburant trop haute",
        "P0089" to "Régulateur de pression carburant 1 - performance",
        "P0090" to "Régulateur de pression carburant 1 - circuit",
        "P0093" to "Fuite détectée dans le système carburant (grosse fuite)",
        "P0100" to "Débitmètre d'air (MAF) - circuit défaillant",
        "P0101" to "Débitmètre d'air (MAF) - plage/performance",
        "P0102" to "Débitmètre d'air (MAF) - signal bas",
        "P0103" to "Débitmètre d'air (MAF) - signal haut",
        "P0105" to "Capteur de pression d'admission (MAP) - circuit défaillant",
        "P0106" to "Capteur de pression d'admission (MAP) - plage/performance",
        "P0107" to "Capteur de pression d'admission (MAP) - signal bas",
        "P0108" to "Capteur de pression d'admission (MAP) - signal haut",
        "P0110" to "Capteur de température d'air d'admission - circuit défaillant",
        "P0112" to "Capteur de température d'air d'admission - signal bas",
        "P0113" to "Capteur de température d'air d'admission - signal haut",
        "P0115" to "Capteur de température du liquide de refroidissement - circuit défaillant",
        "P0117" to "Capteur de température du liquide de refroidissement - signal bas",
        "P0118" to "Capteur de température du liquide de refroidissement - signal haut",
        "P0120" to "Capteur de position papillon/pédale A - circuit défaillant",
        "P0121" to "Capteur de position papillon/pédale A - plage/performance",
        "P0122" to "Capteur de position papillon/pédale A - signal bas",
        "P0123" to "Capteur de position papillon/pédale A - signal haut",
        "P0128" to "Thermostat - température de refroidissement sous le seuil",
        "P0130" to "Sonde à oxygène (banc 1, capteur 1) - circuit défaillant",
        "P0171" to "Mélange trop pauvre (banc 1)",
        "P0172" to "Mélange trop riche (banc 1)",
        "P0174" to "Mélange trop pauvre (banc 2)",
        "P0175" to "Mélange trop riche (banc 2)",
        "P0190" to "Capteur de pression de rampe - circuit défaillant",
        "P0191" to "Capteur de pression de rampe - plage/performance",
        "P0192" to "Capteur de pression de rampe - signal bas",
        "P0193" to "Capteur de pression de rampe - signal haut",
        "P0194" to "Capteur de pression de rampe - signal intermittent",
        // Injecteurs
        "P0200" to "Circuit injecteur - défaillant",
        "P0201" to "Injecteur cylindre 1 - circuit défaillant",
        "P0202" to "Injecteur cylindre 2 - circuit défaillant",
        "P0203" to "Injecteur cylindre 3 - circuit défaillant",
        "P0204" to "Injecteur cylindre 4 - circuit défaillant",
        "P0230" to "Relais de pompe à carburant - circuit défaillant",
        // Turbo
        "P0234" to "Turbocompresseur - surpression (overboost)",
        "P0235" to "Capteur de pression de suralimentation A - circuit défaillant",
        "P0236" to "Capteur de pression de suralimentation A - plage/performance",
        "P0237" to "Capteur de pression de suralimentation A - signal bas",
        "P0238" to "Capteur de pression de suralimentation A - signal haut",
        "P0243" to "Électrovanne de wastegate/géométrie variable A - circuit défaillant",
        "P0245" to "Électrovanne de wastegate/géométrie variable A - signal bas",
        "P0246" to "Électrovanne de wastegate/géométrie variable A - signal haut",
        "P0299" to "Turbocompresseur - sous-pression (underboost)",
        // Ratés / allumage / capteurs régime
        "P0300" to "Ratés de combustion détectés - cylindres multiples/aléatoires",
        "P0301" to "Raté de combustion détecté - cylindre 1",
        "P0302" to "Raté de combustion détecté - cylindre 2",
        "P0303" to "Raté de combustion détecté - cylindre 3",
        "P0304" to "Raté de combustion détecté - cylindre 4",
        "P0335" to "Capteur de position vilebrequin (CKP) A - circuit défaillant",
        "P0336" to "Capteur de position vilebrequin (CKP) A - plage/performance",
        "P0340" to "Capteur de position arbre à cames (CMP) A - circuit défaillant",
        "P0341" to "Capteur de position arbre à cames (CMP) A - plage/performance",
        "P0380" to "Circuit de bougies de préchauffage A - défaillant",
        "P0381" to "Voyant de préchauffage - circuit défaillant",
        // EGR / antipollution
        "P0400" to "Recirculation des gaz d'échappement (EGR) - débit anormal",
        "P0401" to "Débit EGR insuffisant",
        "P0402" to "Débit EGR excessif",
        "P0403" to "Vanne EGR - circuit défaillant",
        "P0404" to "Vanne EGR - plage/performance",
        "P0405" to "Capteur de position EGR A - signal bas",
        "P0406" to "Capteur de position EGR A - signal haut",
        "P0420" to "Efficacité du catalyseur sous le seuil (banc 1)",
        "P0430" to "Efficacité du catalyseur sous le seuil (banc 2)",
        "P0440" to "Circuit d'évaporation des vapeurs de carburant (EVAP) - anomalie",
        "P0442" to "Circuit EVAP - petite fuite détectée",
        "P0455" to "Circuit EVAP - grosse fuite détectée",
        "P0470" to "Capteur de pression d'échappement - circuit défaillant",
        "P0471" to "Capteur de pression d'échappement - plage/performance",
        "P0472" to "Capteur de pression d'échappement - signal bas",
        "P0473" to "Capteur de pression d'échappement - signal haut",
        // Divers
        "P0500" to "Capteur de vitesse véhicule (VSS) - défaillant",
        "P0505" to "Régulation du ralenti - anomalie",
        "P0560" to "Tension du système - anomalie",
        "P0562" to "Tension du système - trop basse",
        "P0563" to "Tension du système - trop haute",
        "P0600" to "Liaison de communication série - défaillante",
        "P0601" to "Calculateur (ECM/PCM) - erreur de mémoire interne",
        "P0606" to "Calculateur (ECM/PCM) - défaut du processeur",
        "P0611" to "Module de commande des injecteurs - défaillant",
        "P0670" to "Module de commande des bougies de préchauffage - circuit défaillant",
        "P0671" to "Bougie de préchauffage cylindre 1 - circuit défaillant",
        "P0672" to "Bougie de préchauffage cylindre 2 - circuit défaillant",
        "P0673" to "Bougie de préchauffage cylindre 3 - circuit défaillant",
        "P0674" to "Bougie de préchauffage cylindre 4 - circuit défaillant",
        "P0700" to "Communication de la transmission - anomalie (voir codes boîte)",
        "P0715" to "Capteur de vitesse d'entrée/turbine - circuit défaillant",
        "P0740" to "Embrayage du convertisseur de couple - circuit défaillant",
        // FAP
        "P242F" to "Filtre à particules - colmatage par accumulation de cendres",
        "P2002" to "Filtre à particules - efficacité sous le seuil (banc 1)",
        "P2452" to "Capteur de pression différentielle du FAP A - circuit défaillant",
        "P2453" to "Capteur de pression différentielle du FAP A - plage/performance",
        "P2454" to "Capteur de pression différentielle du FAP A - signal bas",
        "P2455" to "Capteur de pression différentielle du FAP A - signal haut",
        "P2458" to "Filtre à particules - durée de régénération anormale",
        "P2459" to "Filtre à particules - fréquence de régénération trop élevée",
        "P2463" to "Filtre à particules - colmatage par accumulation de suie",
        // Communication réseau
        "U0100" to "Communication perdue avec le calculateur moteur (ECM/PCM)",
        "U0101" to "Communication perdue avec le calculateur de boîte (TCM)",
        "U0121" to "Communication perdue avec le calculateur ABS",
        "U0140" to "Communication perdue avec le calculateur carrosserie (BCM/BSI)",
        "U0155" to "Communication perdue avec le combiné d'instruments"
    )

    fun describe(code: String): String {
        descriptions[code]?.let { return it }
        val second = code.getOrNull(1)
        return when {
            code.startsWith("P") && (second == '1' || second == '3') ->
                "Code spécifique constructeur ($code) - voir la documentation du constructeur."
            code.startsWith("B") || code.startsWith("C") ->
                "Code carrosserie/châssis ($code) - généralement spécifique constructeur."
            else -> "Description absente du dictionnaire local - rechercher \"$code\" avec la référence du véhicule."
        }
    }
}
