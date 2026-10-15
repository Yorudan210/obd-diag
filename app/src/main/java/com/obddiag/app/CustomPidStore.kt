package com.obddiag.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Sauvegarde locale (SharedPreferences) des PIDs personnalisés de l'utilisateur. */
class CustomPidStore(context: Context) {

    private val prefs = context.getSharedPreferences("obd_custom_pids", Context.MODE_PRIVATE)

    fun load(): List<CustomPid> {
        val txt = prefs.getString("pids", null) ?: return emptyList()
        return try {
            val arr = JSONArray(txt)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CustomPid(
                    name = o.getString("name"),
                    request = o.getString("request"),
                    equation = o.getString("equation"),
                    unit = o.optString("unit", ""),
                    header = o.optString("header", ""),
                    rxId = o.optString("rxId", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(list: List<CustomPid>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name).put("request", it.request)
                    .put("equation", it.equation).put("unit", it.unit)
                    .put("header", it.header).put("rxId", it.rxId)
            )
        }
        prefs.edit().putString("pids", arr.toString()).apply()
    }
}
