package com.toyosatomimi98.pagevoice.data

import java.io.File
import java.security.MessageDigest

object FileDigest {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(64 * 1024).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") {
            "%02x".format(it.toInt() and 0xFF)
        }
    }
}
