package com.example.bertvits2mnn

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import com.example.bertvits2mnn.utils.g_sampleRate

/**
 * Description: SoundPlayHandler
 * Author: Voine
 * Date: 2023/2/23
 */
class SoundPlayHandler {
    private val handler: Handler
    private var audioTrack: AudioTrack? = null
    private var sampleRate = g_sampleRate // default sample rate
    private var channels = AudioFormat.CHANNEL_OUT_MONO
    private var audioFormat = AudioFormat.ENCODING_PCM_FLOAT

    init {
        val handlerThread = HandlerThread("SoundPlayHandler")
        handlerThread.start()
        handler = object : Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                onHandleMessage(msg)
            }
        }
        ensureAudioTrack()
    }

    /**
     * 设置采样率，如果和当前不同会重建 AudioTrack
     */
    fun setSampleRate(sr: Int) {
        if (sr <= 0) throw IllegalArgumentException("不支持的采样率: $sr")
        if (sr != sampleRate) {
            sampleRate = sr
            ensureAudioTrack(forceRecreate = true)
        }
    }

    /**
     * 设置通道和采样率，如果有变化会重建 AudioTrack
     */
    fun setTrackData(sr: Int, ch: Int) {
        val newChannels = when (ch) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> throw IllegalArgumentException("不支持的通道数: $ch")
        }
        if (sr <= 0) throw IllegalArgumentException("不支持的采样率: $sr")
        val changed = sr != sampleRate || newChannels != channels
        sampleRate = sr
        channels = newChannels
        Log.d(TAG, "AudioTrack: sampling rate:$sr channels:$ch")
        if (changed) {
            ensureAudioTrack(forceRecreate = true)
        }
    }

    /**
     * 发送音频数据播放，可指定采样率。
     * 如果传入的 sampleRate 与当前不同，会自动重建 AudioTrack。
     */
    fun sendSound(floatArray: FloatArray, sampleRate: Int = this.sampleRate) {
        if (sampleRate > 0 && sampleRate != this.sampleRate) {
            setSampleRate(sampleRate)
        }
        handler.sendMessage(Message.obtain(handler, 0, floatArray))
    }

    private fun onHandleMessage(msg: Message) {
        val sound = msg.obj as FloatArray
        try {
            Log.d(TAG, "try to write arr....")
            audioTrack?.write(sound, 0, sound.size, AudioTrack.WRITE_BLOCKING)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    /**
     * 确保 AudioTrack 已创建且参数匹配。
     * @param forceRecreate 为 true 时强制销毁重建
     */
    private fun ensureAudioTrack(forceRecreate: Boolean = false) {
        if (forceRecreate) {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        }
        if (audioTrack != null) return

        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channels, audioFormat)
        if (bufferSize <= 0) throw IllegalStateException("AudioTrack不可用！sampleRate=$sampleRate")
        Log.d(TAG, "Creating AudioTrack: sampleRate=$sampleRate channels=$channels bufferSize=$bufferSize")
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setChannelMask(channels)
                    .setSampleRate(sampleRate).build()
            )
            .setBufferSizeInBytes(bufferSize).build()
        audioTrack?.play()
    }

    companion object {
        private const val TAG = "SoundPlayHandler"
    }
}