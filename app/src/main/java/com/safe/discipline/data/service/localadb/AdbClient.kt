package com.safe.discipline.data.service.localadb

import android.os.Build
import android.util.Log
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

private const val TAG = "LocalAdbClient"

class AdbClient(private val host: String, private val port: Int, private val key: AdbKey) : Closeable {

    private lateinit var socket: Socket
    private lateinit var plainInputStream: DataInputStream
    private lateinit var plainOutputStream: DataOutputStream

    private var useTls = false
    private lateinit var tlsSocket: SSLSocket
    private lateinit var tlsInputStream: DataInputStream
    private lateinit var tlsOutputStream: DataOutputStream

    private val inputStream get() = if (useTls) tlsInputStream else plainInputStream
    private val outputStream get() = if (useTls) tlsOutputStream else plainOutputStream

    fun connect() {
        socket = Socket(host, port)
        socket.tcpNoDelay = true
        plainInputStream = DataInputStream(socket.getInputStream())
        plainOutputStream = DataOutputStream(socket.getOutputStream())

        write(AdbProtocol.A_CNXN, AdbProtocol.A_VERSION, AdbProtocol.A_MAXDATA, "host::")

        var message = read()
        if (message.command == AdbProtocol.A_STLS) {
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "TLS ADB requires Android 10+"
            }
            write(AdbProtocol.A_STLS, AdbProtocol.A_STLS_VERSION, 0)

            tlsSocket = key.sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
            tlsSocket.startHandshake()
            tlsInputStream = DataInputStream(tlsSocket.inputStream)
            tlsOutputStream = DataOutputStream(tlsSocket.outputStream)
            useTls = true
            message = read()
        } else if (message.command == AdbProtocol.A_AUTH) {
            check(message.arg0 == AdbProtocol.ADB_AUTH_TOKEN) { "unexpected adb auth arg0=${message.arg0}" }
            write(AdbProtocol.A_AUTH, AdbProtocol.ADB_AUTH_SIGNATURE, 0, key.sign(message.data))
            message = read()
            if (message.command != AdbProtocol.A_CNXN) {
                write(AdbProtocol.A_AUTH, AdbProtocol.ADB_AUTH_RSAPUBLICKEY, 0, key.adbPublicKey)
                message = read()
            }
        }

        check(message.command == AdbProtocol.A_CNXN) { "adb connect failed: command=${message.command}" }
    }

    fun shellCommand(command: String, listener: ((ByteArray) -> Unit)? = null) {
        val localId = 1
        write(AdbProtocol.A_OPEN, localId, 0, "shell:$command")

        var message = read()
        when (message.command) {
            AdbProtocol.A_OKAY -> {
                while (true) {
                    message = read()
                    val remoteId = message.arg0
                    when (message.command) {
                        AdbProtocol.A_WRTE -> {
                            if (message.dataLength > 0 && message.data != null) {
                                listener?.invoke(message.data)
                            }
                            write(AdbProtocol.A_OKAY, localId, remoteId)
                        }
                        AdbProtocol.A_CLSE -> {
                            write(AdbProtocol.A_CLSE, localId, remoteId)
                            break
                        }
                        else -> error("unexpected adb shell command=${message.command}")
                    }
                }
            }
            AdbProtocol.A_CLSE -> {
                val remoteId = message.arg0
                write(AdbProtocol.A_CLSE, localId, remoteId)
            }
            else -> error("adb shell open failed: command=${message.command}")
        }
    }

    private fun write(command: Int, arg0: Int, arg1: Int, data: ByteArray? = null) {
        write(AdbMessage(command, arg0, arg1, data))
    }

    private fun write(command: Int, arg0: Int, arg1: Int, data: String) {
        write(AdbMessage(command, arg0, arg1, data))
    }

    private fun write(message: AdbMessage) {
        outputStream.write(message.toByteArray())
        outputStream.flush()
    }

    private fun read(): AdbMessage {
        val buffer = ByteBuffer.allocate(AdbMessage.HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)
        inputStream.readFully(buffer.array(), 0, AdbMessage.HEADER_LENGTH)

        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val dataLength = buffer.int
        val checksum = buffer.int
        val magic = buffer.int
        val data =
                if (dataLength > 0) {
                    ByteArray(dataLength).also { inputStream.readFully(it, 0, dataLength) }
                } else {
                    null
                }
        return AdbMessage(command, arg0, arg1, dataLength, checksum, magic, data).also {
            it.validateOrThrow()
        }
    }

    override fun close() {
        runCatching { plainInputStream.close() }
        runCatching { plainOutputStream.close() }
        runCatching { socket.close() }
        if (useTls) {
            runCatching { tlsInputStream.close() }
            runCatching { tlsOutputStream.close() }
            runCatching { tlsSocket.close() }
        }
        Log.d(TAG, "closed")
    }
}
