package com.obddiag.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Gère la connexion Bluetooth Classic (SPP) vers un boîtier ELM327
 * et l'envoi/réception des commandes AT + OBD-II (Mode 01/03/04).
 */
class ObdConnection {

    // UUID standard du profil SPP (Serial Port Profile)
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    val isConnected: Boolean
        get() = socket?.isConnected == true

    /** Récupère les périphériques Bluetooth déjà appairés (l'ELM327 doit être appairé avant, via les réglages Android). */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(adapter: BluetoothAdapter): List<BluetoothDevice> {
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            sock.connect()
            socket = sock
            input = sock.inputStream
            output = sock.outputStream

            // Séquence d'initialisation standard de l'ELM327
            initializeElm()
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private suspend fun initializeElm() {
        sendRaw("ATZ")   // reset
        sendRaw("ATE0")  // désactive l'écho
        sendRaw("ATL0")  // désactive les retours à la ligne supplémentaires
        sendRaw("ATS0")  // désactive les espaces dans les réponses
        sendRaw("ATSP0") // sélection auto du protocole OBD-II
    }

    /** Envoie une commande brute (AT ou requête OBD en hexa) et retourne la réponse texte brute. */
    suspend fun sendRaw(command: String): String = withContext(Dispatchers.IO) {
        val out = output ?: throw IOException("Non connecté")
        val inp = input ?: throw IOException("Non connecté")

        out.write((command + "\r").toByteArray())
        out.flush()

        // Lecture jusqu'au caractère '>' qui marque la fin de réponse de l'ELM327
        val buffer = StringBuilder()
        val readBuf = ByteArray(1024)
        var attempts = 0
        while (attempts < 200) {
            if (inp.available() > 0) {
                val n = inp.read(readBuf)
                buffer.append(String(readBuf, 0, n))
                if (buffer.contains('>')) break
            } else {
                kotlinx.coroutines.delay(20)
            }
            attempts++
        }
        buffer.toString()
            .replace("\r", " ")
            .replace(">", "")
            .trim()
    }

    fun disconnect() {
        try {
            input?.close()
            output?.close()
            socket?.close()
        } catch (_: IOException) {
        } finally {
            input = null
            output = null
            socket = null
        }
    }
}
