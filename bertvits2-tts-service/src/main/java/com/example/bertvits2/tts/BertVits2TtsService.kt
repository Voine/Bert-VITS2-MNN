package com.example.bertvits2.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import com.example.bertvits2_infer_wrapper.interfaces.SpeakerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Author: Voine
 * Date: 2026/8/5
 * Description: 把 Bert-VITS2 端侧推理接到 Android 系统 TTS 框架上。
 *
 * 声明了 android.intent.action.TTS_SERVICE 之后，本引擎会出现在
 * 「设置 → 语言和输入 → 文字转语音输出」里，可被 TalkBack / 系统朗读 /
 * 任意使用 TextToSpeech API 的 App 调用。
 *
 * 每个角色（speaker）会被暴露成一个 [Voice]，名字形如 `bv2-甘雨_ZH`，
 * 调用方可以用 TextToSpeech.setVoice 精确指定；不指定时按语言取设置页里的默认角色。
 */
class BertVits2TtsService : TextToSpeechService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** onStop 与合成跑在不同线程，用 volatile 打断合成循环 */
    @Volatile
    private var stopRequested = false

    private var currentLang: TtsPrefs.Lang = TtsPrefs.Lang.ZH

    override fun onCreate() {
        // 注意：super.onCreate() 内部会启动合成线程并回调 onLoadLanguage，
        // 所以 acquire 必须在 super 之前完成，否则 onLoadLanguage 里拿不到实例。
        Bv2InferManager.acquire(this)
        super.onCreate()
        currentLang = TtsPrefs.getLastLang(this)
        // 首次初始化要拷 asset + 解析 config，耗时较长，放后台跑，不阻塞 bind
        serviceScope.launch { Bv2InferManager.ensureInit() }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Bv2InferManager.release()
    }

    // ---------------------------------------------------------------- 语言

    /**
     * 语言支持与模型是否加载完无关（角色是内置的），所以这里只按静态语言表判断，
     * 避免初始化未完成时被系统判定为「不支持」。
     */
    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val target = TtsPrefs.Lang.fromLanguageCode(lang) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        return if (country.isNullOrEmpty() || country.equals(target.country, ignoreCase = true)) {
            TextToSpeech.LANG_COUNTRY_AVAILABLE
        } else {
            // 语言支持但国家/地区不完全匹配（如 zh-TW），仍然用同语言的角色朗读
            TextToSpeech.LANG_AVAILABLE
        }
    }

    override fun onGetLanguage(): Array<String> =
        arrayOf(currentLang.iso3, currentLang.country, "")

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val availability = onIsLanguageAvailable(lang, country, variant)
        if (availability != TextToSpeech.LANG_NOT_SUPPORTED) {
            TtsPrefs.Lang.fromLanguageCode(lang)?.let {
                currentLang = it
                TtsPrefs.setLastLang(this, it)
            }
        }
        return availability
    }

    // ---------------------------------------------------------------- 音色

    override fun onGetVoices(): MutableList<Voice> {
        val speakers = speakersBlocking()
        return speakers.map { info ->
            Voice(
                voiceNameOf(info),
                java.util.Locale.forLanguageTag(info.languageTag),
                Voice.QUALITY_HIGH,
                // 端侧神经网络合成，首字延迟在秒级
                Voice.LATENCY_VERY_HIGH,
                false,
                emptySet()
            )
        }.toMutableList()
    }

    override fun onIsValidVoiceName(voiceName: String?): Int =
        if (findSpeakerByVoiceName(voiceName) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onLoadVoice(voiceName: String?): Int {
        val info = findSpeakerByVoiceName(voiceName) ?: return TextToSpeech.ERROR
        TtsPrefs.Lang.fromTag(info.languageTag)?.let { currentLang = it }
        return TextToSpeech.SUCCESS
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        val target = TtsPrefs.Lang.fromLanguageCode(lang) ?: return null
        return resolveSpeaker(null, target)?.let { voiceNameOf(it) }
    }

    // ---------------------------------------------------------------- 合成

    override fun onStop() {
        Log.i(TAG, "onStop")
        stopRequested = true
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopRequested = false
        val text = request.charSequenceText?.toString().orEmpty()
        val lang = TtsPrefs.Lang.fromLanguageCode(request.language) ?: currentLang
        Log.i(TAG, "onSynthesizeText lang=$lang voice=${request.voiceName} rate=${request.speechRate} len=${text.length}")

        if (!runBlocking { Bv2InferManager.ensureInit() }) {
            Log.e(TAG, "engine init failed")
            callback.error(TextToSpeech.ERROR_SERVICE)
            return
        }

        val speaker = resolveSpeaker(request.voiceName, lang)
        if (speaker == null || speaker.sampleRate <= 0) {
            Log.e(TAG, "no usable speaker for lang=$lang voice=${request.voiceName}")
            callback.error(TextToSpeech.ERROR_INVALID_REQUEST)
            return
        }

        if (callback.start(speaker.sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) {
            Log.e(TAG, "callback.start rejected")
            return
        }

        // 分句合成：首字延迟只取决于第一句，而不是整段文本
        val chunks = TtsTextSplitter.split(text)
        if (chunks.isEmpty()) {
            Log.i(TAG, "nothing speakable in request, finish directly")
            callback.done()
            return
        }
        // 系统语速是百分比（100 为正常），越大越快；lengthScale 越大越慢，故成反比
        val speechRate = request.speechRate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        val lengthScale = TtsPrefs.getBaseLengthScale(this) * 100f / speechRate

        var successCount = 0
        for (chunk in chunks) {
            if (stopRequested) {
                Log.i(TAG, "stopped before chunk")
                return
            }
            val cost = System.currentTimeMillis()
            val result = runCatching {
                runBlocking { Bv2InferManager.synthesize(chunk, speaker.name, lengthScale) }
            }.onFailure {
                Log.e(TAG, "synthesize threw", it)
            }.getOrNull()

            if (result == null) {
                // 单句失败不中断整段朗读，跳过继续下一句
                Log.e(TAG, "synthesize failed, skip chunk: $chunk")
                continue
            }
            val (wave, sampleRate) = result
            Log.d(TAG, "chunk done, samples=${wave.size} cost=${System.currentTimeMillis() - cost} ms")
            if (sampleRate != speaker.sampleRate) {
                // start() 已经把采样率报给框架了，中途变了只能记日志，正常不会发生
                Log.e(TAG, "sample rate mismatch: declared=${speaker.sampleRate} actual=$sampleRate")
            }
            if (!writeWave(callback, wave)) return
            successCount++
        }
        if (stopRequested) return
        if (successCount == 0) {
            // 一句都没合出来，报错给调用方，而不是静默返回一段空音频
            Log.e(TAG, "all ${chunks.size} chunks failed")
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
            return
        }
        callback.done()
    }

    /** 按 [SynthesisCallback.getMaxBufferSize] 分片写出，返回 false 表示需要中断本次合成 */
    private fun writeWave(callback: SynthesisCallback, wave: FloatArray): Boolean {
        // 必须是偶数字节，否则会把一个 16bit 采样点截断
        val maxBytes = (callback.maxBufferSize.coerceAtLeast(2)) and 1.inv()
        val samplesPerWrite = maxBytes / 2
        var offset = 0
        while (offset < wave.size) {
            if (stopRequested) {
                Log.i(TAG, "stopped while writing audio")
                return false
            }
            val count = minOf(samplesPerWrite, wave.size - offset)
            val pcm = PcmUtils.floatToPcm16(wave.copyOfRange(offset, offset + count))
            if (callback.audioAvailable(pcm, 0, pcm.size) != TextToSpeech.SUCCESS) {
                Log.e(TAG, "audioAvailable rejected, abort")
                return false
            }
            offset += count
        }
        return true
    }

    // ---------------------------------------------------------------- 角色解析

    /**
     * 优先级：调用方显式指定的 voice > 设置页里该语言的默认角色 > 该语言第一个角色 > 任意角色
     */
    private fun resolveSpeaker(voiceName: String?, lang: TtsPrefs.Lang): SpeakerInfo? {
        val speakers = speakersBlocking()
        if (speakers.isEmpty()) return null
        findSpeakerByVoiceName(voiceName, speakers)?.let { return it }
        TtsPrefs.getDefaultSpeaker(this, lang)?.let { saved ->
            speakers.firstOrNull { it.name == saved }?.let { return it }
        }
        return speakers.firstOrNull { it.languageTag == lang.tag } ?: speakers.firstOrNull()
    }

    private fun findSpeakerByVoiceName(
        voiceName: String?,
        speakers: List<SpeakerInfo> = speakersBlocking(),
    ): SpeakerInfo? {
        if (voiceName.isNullOrEmpty()) return null
        // 兼容直接传角色名（不带 bv2- 前缀）的调用方
        return speakers.firstOrNull { voiceNameOf(it) == voiceName || it.name == voiceName }
    }

    /**
     * 角色列表需要 init 完成才有内容。这里给一个短超时的等待：
     * onGetVoices / onIsValidVoiceName 走的是 binder 线程，不能无限期阻塞调用方。
     */
    private fun speakersBlocking(): List<SpeakerInfo> {
        if (Bv2InferManager.isInitialized) return Bv2InferManager.speakers()
        val ready = runBlocking {
            withTimeoutOrNull(WAIT_INIT_TIMEOUT_MS) { Bv2InferManager.ensureInit() }
        }
        if (ready != true) {
            Log.w(TAG, "speaker list requested before init done")
            return emptyList()
        }
        return Bv2InferManager.speakers()
    }

    companion object {
        private const val TAG = "BertVits2TtsService"

        /** onGetVoices 这类查询接口等待初始化的上限 */
        private const val WAIT_INIT_TIMEOUT_MS = 3_000L

        private const val MIN_SPEECH_RATE = 25
        private const val MAX_SPEECH_RATE = 400

        const val VOICE_NAME_PREFIX = "bv2-"

        fun voiceNameOf(info: SpeakerInfo): String = VOICE_NAME_PREFIX + info.name
    }
}
