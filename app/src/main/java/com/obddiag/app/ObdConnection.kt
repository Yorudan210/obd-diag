package com.obddiag.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Réponse d'une commande ELM327. */
class ElmReply(val command: String, val raw: String) {
    val lines: List<String> = ObdParser.cleanLines(raw)
    val error: String? = ObdParser.errorOf(lines)
    val messages: List<List<Int>> by lazy { ObdParser.parseMessages(lines) }
    val ok: Boolean get() = error == null
}

/**
 * Connexion Bluetooth Classic (SPP) vers un boîtier ELM327.
 * Toutes les commandes passent par un verrou : l'interface et la boucle de
 * données temps réel peuvent donc l'utiliser sans entrelacer les requêtes.
 */
class ObdConnection {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private val lock = Mutex()

    val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(adapter: BluetoothAdapter): List<BluetoothDevice> =
        adapter.bondedDevices?.toList() ?: emptyList()

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.IO) {
        disconnect()
        try {
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            var sock = device.createRfcommSocketToServiceRecord(sppUuid)
            try {
                sock.connect()
            } catch (e: IOException) {
                // Repli utilisé par beaucoup de clones ELM327 : RFCOMM canal 1 par réflexion
                try { sock.close() } catch (_: IOException) {}
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                sock = m.invoke(device, 1) as BluetoothSocket
                sock.connect()
            }
            socket = sock
            input = sock.inputStream
            output = sock.outputStream
            Result.success(Unit)
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    /**
     * Envoie une commande (AT ou requête OBD en hexa) et attend l'invite '>'.
     * @param timeoutMs délai maximal ; la première requête OBD (recherche de protocole) peut être longue.
     */
    suspend fun send(command: String, timeoutMs: Long = 4000): ElmReply = lock.withLock {
        withContext(Dispatchers.IO) {
            val out = output ?: throw IOException("Non connecté")
            val inp = input ?: throw IOException("Non connecté")

            // Purge les octets résiduels d'une réponse précédente
            val junk = ByteArray(256)
            while (inp.available() > 0) inp.read(junk)

            out.write((command + "\r").toByteArray())
            out.flush()

            val buf = StringBuilder()
            val chunk = ByteArray(1024)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (inp.available() > 0) {
                    val n = inp.read(chunk)
                    if (n > 0) buf.append(String(chunk, 0, n, Charsets.ISO_8859_1))
                    if (buf.contains('>')) break
                } else {
                    delay(8)
                }
            }
            ElmReply(command, buf.toString().replace(">", ""))
        }
    }

    fun disconnect() {
        try { input?.close() } catch (_: IOException) {}
        try { output?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        input = null
        output = null
        socket = null
    }
}
