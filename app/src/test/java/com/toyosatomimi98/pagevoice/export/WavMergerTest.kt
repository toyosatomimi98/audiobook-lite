package com.toyosatomimi98.pagevoice.export

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class WavMergerTest {
    @Test
    fun `pcm wav files merge into one valid data payload`() {
        val directory = Files.createTempDirectory("pagevoice-wav-test").toFile()
        try {
            val first = File(directory, "a.wav").apply { writeBytes(testWav(byteArrayOf(1, 2, 3, 4))) }
            val second = File(directory, "b.wav").apply { writeBytes(testWav(byteArrayOf(5, 6))) }
            val output = ByteArrayOutputStream()

            val result = WavMerger.merge(listOf(first, second), output)
            val merged = File(directory, "merged.wav").apply { writeBytes(output.toByteArray()) }
            val inspected = WavMerger.inspect(merged)

            assertEquals(6L, result.dataLength)
            assertEquals(6L, inspected.dataLength)
            assertEquals(8000, inspected.sampleRate)
            assertEquals(1, inspected.channels)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun testWav(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("RIFF".toByteArray())
        output.writeIntLe(data.size + 36)
        output.write("WAVE".toByteArray())
        output.write("fmt ".toByteArray())
        output.writeIntLe(16)
        output.writeShortLe(1)
        output.writeShortLe(1)
        output.writeIntLe(8000)
        output.writeIntLe(16000)
        output.writeShortLe(2)
        output.writeShortLe(16)
        output.write("data".toByteArray())
        output.writeIntLe(data.size)
        output.write(data)
        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeIntLe(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeShortLe(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }
}
