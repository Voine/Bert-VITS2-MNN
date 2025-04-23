package com.example.bertvits2mnn.utils

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Author: Voine
 * Date: 2025/4/3
 * Description:
 */

val g_sampleRate = 44100

fun floatToShortArray(floatArray: FloatArray): ShortArray {
    return ShortArray(floatArray.size) { i ->
        (floatArray[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
    }
}

fun saveWavFile(
    context: Context,
    filePath: String,
    audioFloatArray: FloatArray,
    audioName: String = "output.wav",
) {
    val output = File(filePath, audioName)
    writeWavFile(output, floatToShortArray(audioFloatArray), g_sampleRate)

}

private fun writeWavFile(
    file: File,
    audioData: ShortArray,
    sampleRate: Int = g_sampleRate,
    numChannels: Int = 1,
    bitsPerSample: Int = 16
) {
    val byteRate = sampleRate * numChannels * bitsPerSample / 8
    val totalDataLen = 36 + audioData.size * 2  // 36 是 header 除 data 外部分
    val totalAudioLen = audioData.size * 2      // short 是2字节

    file.outputStream().use { out ->
        // WAV header
        out.write("RIFF".toByteArray())
        out.write(intToLittleEndian(totalDataLen))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intToLittleEndian(16)) // Subchunk1Size
        out.write(shortToLittleEndian(1)) // AudioFormat: PCM = 1
        out.write(shortToLittleEndian(numChannels.toShort()))
        out.write(intToLittleEndian(sampleRate))
        out.write(intToLittleEndian(byteRate))
        out.write(shortToLittleEndian((numChannels * bitsPerSample / 8).toShort())) // BlockAlign
        out.write(shortToLittleEndian(bitsPerSample.toShort()))
        out.write("data".toByteArray())
        out.write(intToLittleEndian(totalAudioLen))

        // 音频数据
        val byteBuffer = ByteBuffer.allocate(audioData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in audioData) {
            byteBuffer.putShort(s)
        }
        out.write(byteBuffer.array())
    }
}

private fun intToLittleEndian(value: Int): ByteArray =
    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

private fun shortToLittleEndian(value: Short): ByteArray =
    ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
