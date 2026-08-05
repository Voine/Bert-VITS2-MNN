package com.example.bertvits2.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Author: Voine
 * Date: 2026/8/5
 * Description: 设置页试听用的极简播放器，直接吃 BV2 输出的 float 波形。
 * 正式朗读走系统 TTS 框架，不经过这里。
 */
internal class PreviewPlayer {

    private var track: AudioTrack? = null
    private var trackSampleRate = 0

    /** 阻塞写完整段波形，调用方需在后台线程执行 */
    fun play(wave: FloatArray, sampleRate: Int) {
        runCatching {
            val target = ensureTrack(sampleRate)
            target.write(wave, 0, wave.size, AudioTrack.WRITE_BLOCKING)
        }.onFailure { Log.e(TAG, "play failed", it) }
    }

    fun release() {
        runCatching {
            track?.stop()
            track?.release()
        }.onFailure { Log.e(TAG, "release failed", it) }
        track = null
        trackSampleRate = 0
    }

    private fun ensureTrack(sampleRate: Int): AudioTrack {
        val existing = track
        if (existing != null && trackSampleRate == sampleRate) return existing
        release()
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (bufferSize <= 0) throw IllegalStateException("AudioTrack unavailable, sampleRate=$sampleRate")
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setSampleRate(sampleRate)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            .build()
        created.play()
        track = created
        trackSampleRate = sampleRate
        return created
    }

    companion object {
        private const val TAG = "Bv2PreviewPlayer"
    }
}
