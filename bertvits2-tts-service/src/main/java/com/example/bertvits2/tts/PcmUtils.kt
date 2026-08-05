package com.example.bertvits2.tts

/**
 * Author: Voine
 * Date: 2026/8/5
 * Description: BV2 输出的是 [-1, 1] 的 float 波形，而系统 TTS 的
 * SynthesisCallback 只接受整型 PCM，这里做 float -> 16bit PCM(小端) 转换。
 */
internal object PcmUtils {

    /**
     * @param wave   float 波形
     * @param length 只转换前 length 个采样点
     */
    fun floatToPcm16(wave: FloatArray, length: Int = wave.size): ByteArray {
        val count = length.coerceIn(0, wave.size)
        val out = ByteArray(count * 2)
        for (i in 0 until count) {
            val sample = (wave[i].coerceIn(-1f, 1f) * MAX_PCM16).toInt()
            out[i * 2] = (sample and 0xFF).toByte()
            out[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return out
    }

    private const val MAX_PCM16 = 32767f
}
