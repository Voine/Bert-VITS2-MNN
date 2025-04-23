package com.example.bertvits2mnn

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
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
    private var channels = AudioFormat.CHANNEL_OUT_MONO // default channels
    private var audioFormat = AudioFormat.ENCODING_PCM_FLOAT
    private var bufferSize = 0

    init {
        val handlerThread = HandlerThread("SoundPlayHandler")
        handlerThread.start()
        handler = object : Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                onHandleMessage(msg)
            }
        }
        bufferSize = AudioTrack.getMinBufferSize(sampleRate, channels, audioFormat)
        if (bufferSize <= 0) throw Exception("AudioTrack不可用！")
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

    fun setTrackData(sr: Int, ch: Int) {
        if (ch == 2) channels = AudioFormat.CHANNEL_OUT_STEREO
        if (ch > 2 || ch < 0) throw Exception("不支持的通道数$ch！")
        if (sampleRate <= 0) throw Exception("不支持的采样率$sr！")
        sampleRate = sr
//        Timber.tag(TAG).i("AudioTrack", "sampling rate:$sr channels:$ch")
        Log.d(TAG, "AudioTrack: sampling rate:$sr channels:$ch")
    }

    fun sendSound(floatArray: FloatArray) {
        handler.sendMessage(Message.obtain(handler, 0, floatArray))
    }

    fun onHandleMessage(msg: Message) {
        val sound = msg.obj as FloatArray
        try {
//            Timber.tag(TAG).d( "try to write arr....")
            Log.d(TAG, "try to write arr....")
            audioTrack?.write(sound, 0, sound.size, AudioTrack.WRITE_BLOCKING)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        audioTrack?.release()
    }

    fun playFloatAudio(floatArray: FloatArray) {
        val sampleRate = g_sampleRate
        val numSamples = floatArray.size

        val shortData = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val f = Math.max(-1f, Math.min(1f, floatArray[i]))
            shortData[i] = (f * 32767).toInt().toShort()
        }

        val audioTrack =  AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,  // 或 CHANNEL_OUT_STEREO
            AudioFormat.ENCODING_PCM_16BIT,
            shortData.size * 2,          // 每个 short 占 2 字节
            AudioTrack.MODE_STATIC         // 预加载一次性播放
        );

        // Step 3: 播放
        audioTrack.write(shortData, 0, shortData.size);
        audioTrack.play();
    }

    companion object {
        private const val TAG = "SoundPlayHandler"
    }
}