package com.safe.discipline.data.service.localadb

import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbMessage(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val dataChecksum: Int,
        val magic: Int,
        val data: ByteArray?
) {

    constructor(command: Int, arg0: Int, arg1: Int, data: String) :
            this(command, arg0, arg1, "$data\u0000".toByteArray())

    constructor(command: Int, arg0: Int, arg1: Int, data: ByteArray?) :
            this(
                    command,
                    arg0,
                    arg1,
                    data?.size ?: 0,
                    checksum(data),
                    (command.toLong() xor 0xFFFFFFFF).toInt(),
                    data
            )

    fun validateOrThrow() {
        check(command == (magic xor -0x1)) { "bad adb magic" }
        if (dataLength != 0) {
            check(checksum(data) == dataChecksum) { "bad adb checksum" }
        }
    }

    fun toByteArray(): ByteArray {
        val length = HEADER_LENGTH + (data?.size ?: 0)
        return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(command)
            putInt(arg0)
            putInt(arg1)
            putInt(dataLength)
            putInt(dataChecksum)
            putInt(magic)
            if (data != null) put(data)
        }.array()
    }

    companion object {
        const val HEADER_LENGTH = 24

        private fun checksum(data: ByteArray?): Int {
            if (data == null) return 0
            var sum = 0
            for (b in data) {
                val value = b.toInt()
                sum += if (value >= 0) value else value + 256
            }
            return sum
        }
    }
}
