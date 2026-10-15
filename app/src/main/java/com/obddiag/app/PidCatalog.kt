package com.obddiag.app

data class Reading(val label: String, val value: Double, val unit: String)

enum class PidCategory(val title: String) {
    ENGINE("Moteur"),
    FUEL("Injection / Carburant"),
    TURBO("Turbo / Admission"),
    EMISSIONS("Antipollution (FAP / EGR / échappement)"),
    TEMPS("Températures"),
    COUNTERS("Compteurs / Divers")
}

/** Un PID standard SAE J1979 (mode 01) et sa formule de décodage. */
class LivePid(
    val pid: Int,
    val name: String,
    val category: PidCategory,
    val minBytes: Int,
    val decode: (List<Int>) -> List<Reading>
) {
    val hex: String get() = "%02X".format(pid)
}

object PidCatalog {

    private fun u16(b: List<Int>, i: Int) = b[i] * 256 + b[i + 1]
    private fun s16(b: List<Int>, i: Int): Int {
        val v = u16(b, i)
        return if (v >= 0x8000) v - 0x10000 else v
    }

    private fun single(
        pid: Int, name: String, unit: String, cat: PidCategory, bytes: Int,
        f: (List<Int>) -> Double
    ) = LivePid(pid, name, cat, bytes) { listOf(Reading(name, f(it), unit)) }

    val all: List<LivePid> = listOf(
        // ---- Moteur ----
        single(0x0C, "Régime moteur", "tr/min", PidCategory.ENGINE, 2) { u16(it, 0) / 4.0 },
        single(0x0D, "Vitesse véhicule", "km/h", PidCategory.ENGINE, 1) { it[0].toDouble() },
        single(0x04, "Charge moteur calculée", "%", PidCategory.ENGINE, 1) { it[0] * 100.0 / 255.0 },
        single(0x43, "Charge moteur absolue", "%", PidCategory.ENGINE, 2) { u16(it, 0) * 100.0 / 255.0 },
        single(0x11, "Position papillon", "%", PidCategory.ENGINE, 1) { it[0] * 100.0 / 255.0 },
        single(0x49, "Pédale d'accélérateur D", "%", PidCategory.ENGINE, 1) { it[0] * 100.0 / 255.0 },
        single(0x4A, "Pédale d'accélérateur E", "%", PidCategory.ENGINE, 1) { it[0] * 100.0 / 255.0 },
        single(0x4C, "Commande papillon", "%", PidCategory.ENGINE, 1) { it[0] * 100.0 / 255.0 },
        single(0x61, "Couple demandé", "%", PidCategory.ENGINE, 1) { it[0] - 125.0 },
        single(0x62, "Couple réel", "%", PidCategory.ENGINE, 1) { it[0] - 125.0 },
        single(0x63, "Couple de référence", "Nm", PidCategory.ENGINE, 2) { u16(it, 0).toDouble() },
        single(0x42, "Tension calculateur", "V", PidCategory.ENGINE, 2) { u16(it, 0) / 1000.0 },

        // ---- Injection / carburant ----
        single(0x23, "Pression rampe commune", "bar", PidCategory.FUEL, 2) { u16(it, 0) / 10.0 },
        single(0x59, "Pression rampe (absolue)", "bar", PidCategory.FUEL, 2) { u16(it, 0) / 10.0 },
        single(0x22, "Pression rampe (relative collecteur)", "kPa", PidCategory.FUEL, 2) { u16(it, 0) * 0.079 },
        single(0x5D, "Calage de l'injection", "°", PidCategory.FUEL, 2) { u16(it, 0) / 128.0 - 210.0 },
        single(0x5E, "Consommation instantanée", "L/h", PidCategory.FUEL, 2) { u16(it, 0) / 20.0 },
        single(0x2F, "Niveau de carburant", "%", PidCategory.FUEL, 1) { it[0] * 100.0 / 255.0 },

        // ---- Turbo / admission ----
        single(0x0B, "Pression collecteur d'admission (MAP)", "kPa", PidCategory.TURBO, 1) { it[0].toDouble() },
        single(0x10, "Débit d'air (MAF)", "g/s", PidCategory.TURBO, 2) { u16(it, 0) / 100.0 },
        single(0x33, "Pression barométrique", "kPa", PidCategory.TURBO, 1) { it[0].toDouble() },
        LivePid(0x70, "Pression de suralimentation", PidCategory.TURBO, 5) { b ->
            if ((b[0] and 1) == 0) emptyList() else listOf(
                Reading("Pression turbo - consigne", u16(b, 1) / 32.0, "kPa"),
                Reading("Pression turbo - réelle", u16(b, 3) / 32.0, "kPa")
            )
        },
        LivePid(0x71, "Géométrie variable (VGT)", PidCategory.TURBO, 3) { b ->
            listOf(
                Reading("Géométrie variable - consigne", b[1] * 100.0 / 255.0, "%"),
                Reading("Géométrie variable - réelle", b[2] * 100.0 / 255.0, "%")
            )
        },

        // ---- Antipollution ----
        single(0x2C, "Commande vanne EGR", "%", PidCategory.EMISSIONS, 1) { it[0] * 100.0 / 255.0 },
        single(0x2D, "Erreur EGR", "%", PidCategory.EMISSIONS, 1) { it[0] * 100.0 / 128.0 - 100.0 },
        single(0x24, "Richesse (lambda) sonde 1", "λ", PidCategory.EMISSIONS, 2) { u16(it, 0) * 2.0 / 65536.0 },
        LivePid(0x78, "Température échappement (EGT) banc 1", PidCategory.EMISSIONS, 3) { b ->
            val r = mutableListOf<Reading>()
            if ((b[0] and 1) != 0) r.add(Reading("EGT banc 1 capteur 1", u16(b, 1) / 10.0 - 40.0, "°C"))
            if ((b[0] and 2) != 0 && b.size >= 5) r.add(Reading("EGT banc 1 capteur 2", u16(b, 3) / 10.0 - 40.0, "°C"))
            r
        },
        LivePid(0x7A, "Filtre à particules - pressions (FAP)", PidCategory.EMISSIONS, 3) { b ->
            val r = mutableListOf<Reading>()
            if ((b[0] and 1) != 0) r.add(Reading("FAP pression différentielle", s16(b, 1) / 100.0, "kPa"))
            if ((b[0] and 2) != 0 && b.size >= 5) r.add(Reading("FAP pression entrée", u16(b, 3) / 100.0, "kPa"))
            if ((b[0] and 4) != 0 && b.size >= 7) r.add(Reading("FAP pression sortie", u16(b, 5) / 100.0, "kPa"))
            r
        },
        LivePid(0x7C, "Filtre à particules - températures (FAP)", PidCategory.EMISSIONS, 3) { b ->
            val r = mutableListOf<Reading>()
            if ((b[0] and 1) != 0) r.add(Reading("FAP température entrée", u16(b, 1) / 10.0 - 40.0, "°C"))
            if ((b[0] and 2) != 0 && b.size >= 5) r.add(Reading("FAP température sortie", u16(b, 3) / 10.0 - 40.0, "°C"))
            r
        },

        // ---- Températures ----
        single(0x05, "Température liquide de refroidissement", "°C", PidCategory.TEMPS, 1) { it[0] - 40.0 },
        single(0x0F, "Température air d'admission", "°C", PidCategory.TEMPS, 1) { it[0] - 40.0 },
        single(0x46, "Température ambiante", "°C", PidCategory.TEMPS, 1) { it[0] - 40.0 },
        single(0x5C, "Température huile moteur", "°C", PidCategory.TEMPS, 1) { it[0] - 40.0 },

        // ---- Compteurs ----
        single(0x1F, "Durée depuis le démarrage", "s", PidCategory.COUNTERS, 2) { u16(it, 0).toDouble() },
        single(0x21, "Distance avec voyant MIL allumé", "km", PidCategory.COUNTERS, 2) { u16(it, 0).toDouble() },
        single(0x31, "Distance depuis effacement des défauts", "km", PidCategory.COUNTERS, 2) { u16(it, 0).toDouble() },
        single(0x30, "Cycles de chauffe depuis effacement", "", PidCategory.COUNTERS, 1) { it[0].toDouble() },
        single(0x4D, "Durée MIL allumé", "min", PidCategory.COUNTERS, 2) { u16(it, 0).toDouble() },
        single(0x4E, "Durée depuis effacement des défauts", "min", PidCategory.COUNTERS, 2) { u16(it, 0).toDouble() }
    )

    val byPid: Map<Int, LivePid> = all.associateBy { it.pid }

    /** PIDs cochés par défaut au premier affichage. */
    val defaultSelection: Set<Int> = setOf(0x0C, 0x0D, 0x05, 0x04, 0x0B, 0x10, 0x23, 0x0F)

    /** PIDs pertinents pour une image figée (mode 02). */
    val freezeFramePids: List<LivePid> = all.filter {
        it.category != PidCategory.COUNTERS && it.pid <= 0x5F
    }

    fun fuelType(code: Int): String = when (code) {
        1 -> "Essence"; 2 -> "Méthanol"; 3 -> "Éthanol"; 4 -> "Diesel"; 5 -> "GPL"
        6 -> "GNC"; 7 -> "Propane"; 8 -> "Électrique"; 9, 10, 11 -> "Bicarburation"
        else -> "Inconnu ($code)"
    }
}
