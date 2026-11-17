package com.toyosatomimi98.pagevoice.export

import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

data class WavInfo(
    val audioFormat: Int,
    val channels: Int,
    val sampleRate: Int,
    val byteRate: Int,
    val blockAlign: Int,
    val bitsPerSample: Int,
    val dataOffset: Long,
    val dataLength: Long,
) {
    val durationMillis: Long
        get() = if (byteRate <= 0) 0L else dataLength * 1000L / byteRate
}

object WavMerger {
    fun inspect(file: File): WavInfo {
        RandomAccessFile(file, "r").use { input ->
            require(input.length() >= 44L) { "语音引擎生成了不完整的 WAV 文件。" }
            require(input.readAscii(4) == "RIFF") { "不支持的音频格式：缺少 RIFF 头。" }
            input.skipFully(4)
            require(input.readAscii(4) == "WAVE") { "不支持的音频格式：不是 WAVE 文件。" }

            var format: WavInfo? = null
            var dataOffset = -1L
            var dataLength = -1L
            while (input.filePointer + 8 <= input.length()) {
                val chunkId = input.readAscii(4)
                val chunkSize = input.readUnsignedIntLe()
                val chunkStart = input.filePointer
                when (chunkId) {
                    "fmt " -> {
                        require(chunkSize >= 16) { "WAV fmt 区块损坏。" }
                        val audioFormat = input.readUnsignedShortLe()
                        val channels = input.readUnsignedShortLe()
                        val sampleRate = input.readIntLe()
                        val byteRate = input.readIntLe()
                        val blockAlign = input.readUnsignedShortLe()
                        val bits = input.readUnsignedShortLe()
                        format = WavInfo(
                            audioFormat = audioFormat,
                            channels = channels,
                            sampleRate = sampleRate,
                            byteRate = byteRate,
                            blockAlign = blockAlign,
                            bitsPerSample = bits,
                            dataOffset = -1L,
                            dataLength = -1L,
                        )
                    }
                    "data" -> {
                        dataOffset = chunkStart
                        dataLength = chunkSize.coerceAtMost(input.length() - chunkStart)
                    }
                }
                input.seek(chunkStart + chunkSize + (chunkSize and 1L))
            }

            val parsed = requireNotNull(format) { "WAV 文件缺少 fmt 区块。" }
            require(dataOffset >= 0 && dataLength >= 0) { "WAV 文件缺少 data 区块。" }
            return parsed.copy(dataOffset = dataOffset, dataLength = dataLength)
        }
    }

    fun merge(inputs: List<File>, output: OutputStream): WavInfo {
        require(inputs.isNotEmpty()) { "没有可合并的音频片段。" }
        val infos = inputs.map(::inspect)
        val first = infos.first()
        require(first.audioFormat == 1) {
            "当前系统 TTS 输出的 WAV 不是常见 PCM 格式，暂时无法合并。"
        }
        require(infos.all {
            it.audioFormat == first.audioFormat &&
                it.channels == first.channels &&
                it.sampleRate == first.sampleRate &&
                it.bitsPerSample == first.bitsPerSample
        }) {
            "系统语音引擎在不同片段间切换了音频格式，请尝试安装统一语音包后重试。"
        }

        val totalData = infos.sumOf { it.dataLength }
        require(totalData <= 0xFFFFFFFFL - 36L) { "导出的音频超过 WAV 单文件上限（约 4 GB）。" }
        val buffered = BufferedOutputStream(output, 64 * 1024)

        writeAscii(buffered, "RIFF")
        writeIntLe(buffered, (totalData + 36L).toInt())
        writeAscii(buffered, "WAVE")
        writeAscii(buffered, "fmt ")
        writeIntLe(buffered, 16)
        writeShortLe(buffered, first.audioFormat)
        writeShortLe(buffered, first.channels)
        writeIntLe(buffered, first.sampleRate)
        writeIntLe(buffered, first.byteRate)
        writeShortLe(buffered, first.blockAlign)
        writeShortLe(buffered, first.bitsPerSample)
        writeAscii(buffered, "data")
        writeIntLe(buffered, totalData.toInt())

        inputs.forEachIndexed { index, file ->
            val info = infos[index]
            RandomAccessFile(file, "r").use { input ->
                input.seek(info.dataOffset)
                val buffer = ByteArray(64 * 1024)
                var remaining = info.dataLength
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    buffered.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
        buffered.flush()

        return first.copy(dataOffset = 44L, dataLength = totalData)
    }

    private fun RandomAccessFile.readAscii(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readUnsignedShortLe(): Int =
        readUnsignedByte() or (readUnsignedByte() shl 8)

    private fun RandomAccessFile.readIntLe(): Int {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        val b2 = readUnsignedByte()
        val b3 = readUnsignedByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun RandomAccessFile.readUnsignedIntLe(): Long =
        readIntLe().toLong() and 0xFFFFFFFFL

    private fun RandomAccessFile.skipFully(bytes: Long) {
        seek(filePointer + bytes)
    }

    private fun writeAscii(output: OutputStream, value: String) {
        output.write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun writeShortLe(output: OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
    }

    private fun writeIntLe(output: OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 24) and 0xFF)
    }
}
