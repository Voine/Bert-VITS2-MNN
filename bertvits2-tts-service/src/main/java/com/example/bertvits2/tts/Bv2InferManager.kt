package com.example.bertvits2.tts

import android.content.Context
import android.util.Log
import com.example.bertvits2_infer_wrapper.impl.BertVITS2SimpleInferImpl
import com.example.bertvits2_infer_wrapper.interfaces.IBertVITS2SimpleInfer
import com.example.bertvits2_infer_wrapper.interfaces.SpeakerInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Author: Voine
 * Date: 2026/8/5
 * Description: 进程内唯一的 BV2 推理实例。
 *
 * 系统 TTS 引擎（[BertVits2TtsService]）与 demo UI 会同时用到推理能力，而底层
 * loader / 模型路径 / lengthScale 都是全局状态，所以这里做两件事：
 * 1. 引用计数持有：谁用谁 [acquire]，[release] 到 0 才真正销毁 loader；
 * 2. 串行化推理：所有 [synthesize] 排队执行，避免并发改模型路径导致串音。
 */
object Bv2InferManager {

    private const val TAG = "Bv2InferManager"

    private val initMutex = Mutex()
    private val inferMutex = Mutex()

    private var infer: IBertVITS2SimpleInfer? = null
    private var initialized = false
    private var refCount = 0

    /** 当前生效的 lengthScale，避免重复调用 native setter */
    private var currentLengthScale = -1f

    /**
     * 声明一次使用，需与 [release] 配对。
     * @param context 用 applicationContext，避免持有 Activity
     */
    @Synchronized
    fun acquire(context: Context) {
        refCount++
        if (infer == null) {
            infer = BertVITS2SimpleInferImpl(context.applicationContext)
        }
        Log.d(TAG, "acquire, refCount=$refCount")
    }

    @Synchronized
    fun release() {
        refCount--
        Log.d(TAG, "release, refCount=$refCount")
        if (refCount <= 0) {
            refCount = 0
            runCatching { infer?.release() }
                .onFailure { Log.e(TAG, "release infer failed", it) }
            infer = null
            initialized = false
            currentLengthScale = -1f
        }
    }

    /** 幂等初始化（asset 拷贝 + config 解析 + preprocessor），首次调用耗时较长 */
    suspend fun ensureInit(): Boolean = initMutex.withLock {
        if (initialized) return@withLock true
        val target = synchronized(this) { infer } ?: run {
            Log.e(TAG, "ensureInit called without acquire()")
            return@withLock false
        }
        val cost = System.currentTimeMillis()
        val success = runCatching { target.init() }
            .onFailure { Log.e(TAG, "init failed", it) }
            .getOrDefault(false)
        Log.i(TAG, "init success=$success, cost=${System.currentTimeMillis() - cost} ms")
        initialized = success
        success
    }

    val isInitialized: Boolean
        @Synchronized get() = initialized

    /** [ensureInit] 成功后才有内容 */
    fun speakers(): List<SpeakerInfo> =
        synchronized(this) { infer }?.takeIf { isInitialized }?.getSpeakerInfoList() ?: emptyList()

    fun speakerNames(): List<String> = speakers().map { it.name }

    /**
     * 串行执行一次推理。调用前需保证 [ensureInit] 已成功。
     * @return float 波形 + 采样率，失败返回 null
     */
    suspend fun synthesize(
        text: String,
        spkName: String,
        lengthScale: Float,
    ): Pair<FloatArray, Int>? = inferMutex.withLock {
        val target = synchronized(this) { infer }
        if (target == null || !isInitialized) {
            Log.e(TAG, "synthesize before init done")
            return@withLock null
        }
        if (lengthScale > 0f && lengthScale != currentLengthScale) {
            target.setAudioLengthScale(lengthScale)
            currentLengthScale = lengthScale
        }
        val result = runCatching { target.infer(text, spkName) }
            .onFailure { Log.e(TAG, "infer failed, text=$text spk=$spkName", it) }
            .getOrNull()
        val wave = result?.first ?: return@withLock null
        val sampleRate = result.second
        if (wave.isEmpty() || sampleRate <= 0) {
            Log.e(TAG, "infer got empty result, sampleRate=$sampleRate")
            return@withLock null
        }
        wave to sampleRate
    }
}
