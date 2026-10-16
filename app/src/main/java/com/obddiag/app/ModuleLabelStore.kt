package com.obddiag.app

import android.content.Context
import org.json.JSONObject

/** Noms donnés par l'utilisateur aux modules trouvés (adresse de requête -> nom). */
class ModuleLabelStore(context: Context) {

    private val prefs = context.getSharedPreferences("obd_module_labels", Context.MODE_PRIVATE)

    fun load(): Map<String, String> {
        val txt = prefs.getString("labels", null) ?: return emptyMap()
        return try {
            val o = JSONObject(txt)
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun save(labels: Map<String, String>) {
        val o = JSONObject()
        labels.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("labels", o.toString()).apply()
    }
}
