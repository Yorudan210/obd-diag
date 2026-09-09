package com.obddiag.app

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ObdViewModel : ViewModel() {

    private val connection = ObdConnection()

    var isConnected = mutableStateOf(false)
    var statusMessage = mutableStateOf("Non connecté")
    var dtcList = mutableStateOf<List<DtcInfo>>(emptyList())
    var liveValues = mutableStateOf<Map<String, Pair<Double, String>>>(emptyMap())
    var isLoading = mutableStateOf(false)

    private var liveDataJob: Job? = null

    fun connect(device: BluetoothDevice) {
        isLoading.value = true
        statusMessage.value = "Connexion en cours..."
        viewModelScope.launch {
            val result = connection.connect(device)
            isLoading.value = false
            if (result.isSuccess) {
                isConnected.value = true
                statusMessage.value = "Connecté à ${device.name ?: device.address}"
            } else {
                isConnected.value = false
                statusMessage.value = "Échec de connexion : ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun disconnect() {
        stopLiveData()
        connection.disconnect()
        isConnected.value = false
        statusMessage.value = "Déconnecté"
    }

    /** Lit les codes défauts stockés (Mode 03) */
    fun readDtcs() {
        if (!connection.isConnected) return
        isLoading.value = true
        viewModelScope.launch {
            try {
                val raw = connection.sendRaw("03")
                val codes = DtcCodes.parseMode03(raw)
                dtcList.value = codes.map { DtcInfo(it, DtcCodes.describe(it)) }
                statusMessage.value = if (codes.isEmpty()) "Aucun code défaut stocké" else "${codes.size} code(s) trouvé(s)"
            } catch (e: Exception) {
                statusMessage.value = "Erreur lecture DTC : ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    /** Efface les codes défauts stockés (Mode 04) */
    fun clearDtcs() {
        if (!connection.isConnected) return
        isLoading.value = true
        viewModelScope.launch {
            try {
                connection.sendRaw("04")
                dtcList.value = emptyList()
                statusMessage.value = "Codes défauts effacés"
            } catch (e: Exception) {
                statusMessage.value = "Erreur effacement : ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    /** Démarre le rafraîchissement en boucle des données temps réel */
    fun startLiveData() {
        if (!connection.isConnected) return
        stopLiveData()
        liveDataJob = viewModelScope.launch {
            while (true) {
                val updated = mutableMapOf<String, Pair<Double, String>>()
                for (pidDef in PidParser.supportedPids) {
                    try {
                        val raw = connection.sendRaw("01${pidDef.pid}")
                        val dataBytes = PidParser.extractDataBytes(raw, pidDef.pid)
                        if (dataBytes != null && dataBytes.isNotEmpty()) {
                            val value = pidDef.convert(dataBytes)
                            updated[pidDef.name] = value to pidDef.unit
                        }
                    } catch (_: Exception) {
                        // PID non supporté par ce véhicule, on ignore
                    }
                }
                liveValues.value = updated
                delay(500)
            }
        }
    }

    fun stopLiveData() {
        liveDataJob?.cancel()
        liveDataJob = null
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return connection.getPairedDevices(adapter)
    }

    override fun onCleared() {
        super.onCleared()
        connection.disconnect()
    }
}
