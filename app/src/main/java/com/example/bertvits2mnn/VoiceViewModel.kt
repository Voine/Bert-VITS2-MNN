package com.example.bertvits2mnn

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bertvits2.BertVITS2JNI
import com.example.bertvits2mnn.preprocess.BV2Preprocess
import com.example.bertvits2mnn.preprocess.intersperse
import com.example.bertvits2mnn.preprocess.zhSymbolsMap
import com.example.bertvits2mnn.utils.copyAssets2Local
import com.example.bertvits2mnn.utils.saveWavFile
import com.example.cpptokenizer.CppTokenizerJNI
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.collections.toIntArray
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Author: Voine
 * Date: 2025/4/1
 * Description:
 */
class VoiceViewModel : ViewModel() {
    private val _uiState: MutableSharedFlow<UIState> = MutableSharedFlow(replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val uiState = _uiState.asSharedFlow()
    private val soundHandler: SoundPlayHandler by lazy {
        SoundPlayHandler()
    }
    private val vitsInferChannel by lazy {
        Channel<BV2InferBean>(capacity = Int.MAX_VALUE, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    @Volatile
    private var currentSpkId = 0

    private lateinit var speakers: List<String>

    private lateinit var bertVITS2: BertVITS2JNI

    private lateinit var bV2Preprocess: BV2Preprocess

    private lateinit var bertTokenizer: CppTokenizerJNI

    fun init(context: Context) {
        setLoading(true, "正在初始化...")
        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            setLoading(true, "正在移动模型...")
            var absPath = suspendCancellableCoroutine {
                context.copyAssets2Local(true, "assets", context.filesDir.absolutePath) { isSuccess: Boolean, absPath: String ->
                    Log.d("copyAssets2Local", "isSuccess: $isSuccess, absPath: $absPath")
                    it.safeResume(absPath)
                }
            }

            setLoading(true, "正在初始化 VITS...")
            initVits(true, absPath)
            val configJson = File("${context.filesDir.absolutePath}/assets/bv2_model/config.json")

            val gson = Gson();

            // 先将整体转为 JsonObject
            val root = gson.fromJson(configJson.readText(), JsonObject::class.java);

            // 提取 spk2id 部分
            val spk2idJson = root
                .getAsJsonObject("data")
                .getAsJsonObject("spk2id");

            // 转为 Map<String, Integer>
            val type = object : TypeToken<Map<String, Int>>() {}.type
            val spk2idMap: Map<String, Int> = gson.fromJson(spk2idJson, type)
            speakers = spk2idMap.keys.toList()
            initCharacters(speakers)
            setLoading(true, "正在初始化中文分词引擎...")

            bV2Preprocess = BV2Preprocess(context,
                jieba_dict = "${context.filesDir.absolutePath}/assets/preprocess/dict/jieba.dict.utf8",
                jieba_hmm = "${context.filesDir.absolutePath}/assets/preprocess/dict/hmm_model.utf8",
                jieba_user = "${context.filesDir.absolutePath}/assets/preprocess/dict/user.dict.utf8",
                jieba_idf = "${context.filesDir.absolutePath}/assets/preprocess/dict/idf.utf8",
                jieba_stop = "${context.filesDir.absolutePath}/assets/preprocess/dict/stop_words.utf8",
                opencpop_strict_path = "assets/preprocess/opencpop-strict.txt"
            )

            bertTokenizer = CppTokenizerJNI()
            bertTokenizer.initTokenizer("${context.filesDir.absolutePath}/assets/bert/tokenizer.json")

            setDefaultState()
            val endTime = System.currentTimeMillis()
            Log.d("init", "init time: ${endTime - startTime} ms")
            updateLogcat("初始化耗时: ${endTime - startTime} ms")
            launch (Dispatchers.Main.immediate) {
                while (true) {
                    val bv2Infer = vitsInferChannel.receive()
                    Log.d("runVits", "cleanedText start infer: $bv2Infer")
                    setLoading(true, "开始启动推理...")
                    val startTime = System.currentTimeMillis()

                    val result: FloatArray? = withContext(Dispatchers.IO) {
                        bertVITS2.startAudioInfer(
                            input_seq = bv2Infer.input_seq,
                            input_t = bv2Infer.input_t,
                            input_language = bv2Infer.input_language,
                            input_ids = bv2Infer.input_ids,
                            input_word2ph = bv2Infer.word2ph,
                            attention_mask = bv2Infer.attention_mask,
                            spkid = currentSpkId
                        )
                    }
                    val endTime = System.currentTimeMillis()
                    updateLogcat("推理耗时: ${endTime - startTime} ms")
                    Log.d("runVits", "result: ${result?.joinToString(",", limit = 10)}")
                    Log.d("runVits", "infer time: ${endTime - startTime} ms")
                    setLoading(false)
                    result ?: continue
                    soundHandler.sendSound(result)
                    launch(Dispatchers.IO) {
                        runCatching {
                            saveWavFile(
                                context,
                                context.filesDir.absolutePath,
                                result,
                                "output_${System.currentTimeMillis()}.wav"
                            )
                        }.onFailure {
                            Log.e("runVits", "saveWavFile error: ${it.message}")
                        }
                    }
                }
            }
            setLoading(false)
        }
    }

    fun setDefaultState() {
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        Log.d("setDefaultState", "sendState: $sendState")
        _uiState.tryEmit(sendState.copy
            (inputText = "旅行者，好久不见",
            selectedCharacter = speakers[0])
        )
    }


    fun initVits(isSuccess: Boolean, absPath: String) {
        val encPath = File(absPath,"assets/bv2_model/bert_vits23_enc_genshin_arknights_fp16.mnn").absolutePath
        val decPath = File(absPath,"assets/bv2_model/bert_vits23_dec_genshin_arknights_fp16.mnn").absolutePath
        val flowPath = File(absPath,"assets/bv2_model/bert_vits23_flow_genshin_arknights_fp16.mnn").absolutePath
        val embPath = File(absPath,"assets/bv2_model/bert_vits23_emb_genshin_arknights_fp16.mnn").absolutePath
        val dpPath = File(absPath,"assets/bv2_model/bert_vits23_dp_genshin_arknights_fp16.mnn").absolutePath
        val sdpPath = File(absPath,"assets/bv2_model/bert_vits23_sdp_genshin_arknights_fp16.mnn").absolutePath
        val bertPath = File(absPath,"assets/bert/chinese-roberta-wwm-ext-large-distilled-fp16.mnn").absolutePath
        bertVITS2 = BertVITS2JNI()
        bertVITS2.initBertVITS2Loader()
        bertVITS2.setBertVITS2ModelPath(
            enc_model_path = encPath,
            dec_model_path = decPath,
            sdp_model_path = sdpPath,
            dp_model_path = dpPath,
            emb_model_path = embPath,
            flow_model_path = flowPath,
            bert_model_path = bertPath
        )
    }

    fun runVits(showText: String = "你好") {
        setLoading(true, "开始转义文本...")
        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val normalized  = bV2Preprocess.normalizeText(showText)
            val bertResult = bertTokenizer.encodeText(normalized)
            val g2pResult = bV2Preprocess.preprocessWithNormalizedText(normalized)
            val endTime = System.currentTimeMillis()
            Log.d("runVits", "cleanedText time: ${endTime - startTime} ms")
            updateLogcat("文本预处理: ${endTime - startTime} ms")
            var phones = g2pResult.phones.mapNotNull { zhSymbolsMap[it] }
            var tones = g2pResult.tones
            var langIds = List(phones.size) { 0 }

            if (phones.size != tones.size) {
                Log.e("runVits", "phones size error: ${phones.size}, tones size: ${tones.size}")
                updateLogcat("处理失败：phones size error: ${phones.size}\ntones size: ${tones.size}\ntext: $showText")
                return@launch
            }

            //add blank
            phones = intersperse(phones, 0)
            tones = intersperse(tones, 0)
            langIds = intersperse(langIds, 0)
            val word2ph = g2pResult.word2ph.map { it * 2 }.toMutableList()
            word2ph[0] += 1

            //    assert len(word2ph) == len(text) + 2
            if (normalized.length + 2 != word2ph.size) {
                Log.e("runVits", "word2ph size error: ${word2ph.size}, text length: ${normalized.length}")
                return@launch
            }
            vitsInferChannel.trySend(
                BV2InferBean(
                    input_seq = phones.toIntArray(),
                    input_t = tones.toIntArray(),
                    input_language = langIds.toIntArray(),
                    input_ids = bertResult,
                    word2ph = word2ph.toIntArray(),
                    attention_mask = IntArray(bertResult.size) { 1 },
                )
            )
        }
    }

    fun initCharacters(speakers: List<String>?) {
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        _uiState.tryEmit(sendState.copy(characters = speakers ?: emptyList()))
    }

    fun updateInputText(string: String) {
        Log.d("updateInputText", "string: $string")
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        _uiState.tryEmit(sendState.copy(inputText = string))
    }

    fun startAudioInference(text: String) {
        runVits(text.trim())
    }

    fun selectCharacter(string: String) {
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        Log.d("selectCharacter", "string: $string")
        updateLogcat("选择角色: $string")
        _uiState.tryEmit(sendState.copy(selectedCharacter = string))
        currentSpkId = speakers.indexOf(string)
    }

    fun setLoading(loading: Boolean, hint: String = "") {
        Log.d("setLoading", "loading: $loading, hint: $hint")
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        _uiState.tryEmit(sendState.copy(isLoading = loading, loadingHint = hint))
    }

    fun updateLengthScale(lengthScale: Float) {
        Log.d("updateLengthScale", "lengthScale: $lengthScale")
        updateLogcat("语音缩放系数: $lengthScale")
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        _uiState.tryEmit(sendState.copy(currentLengthScale = lengthScale))
        bertVITS2.setAudioLengthScale(lengthScale)
    }

    fun updateLogcat(logcat: String) {
        Log.d("updateLogcat", "logcat: $logcat")
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        val currentLogcat = sendState.logcat
        _uiState.tryEmit(sendState.copy(logcat = "$currentLogcat\n$logcat"))
    }
}

data class UIState(
    val inputText: String = "",
    val selectedCharacter: String = "",
    val isLoading: Boolean = false,
    val loadingHint: String = "",
    val currentLengthScale: Float = 1.0f,
    val characters: List<String> = emptyList(),
    val logcat: String = "",
)

fun <T> CancellableContinuation<T>.safeResume(value: T) {
    if (this.isActive) {
        (this as? Continuation<T>)?.resume(value)
    }
}
data class BV2InferBean(
    val input_seq: IntArray,
    val input_t: IntArray,
    val input_language: IntArray,
    //for bert use
    val input_ids: IntArray,
    val word2ph: IntArray,
    val attention_mask: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BV2InferBean

        if (!input_seq.contentEquals(other.input_seq)) return false
        if (!input_t.contentEquals(other.input_t)) return false
        if (!input_language.contentEquals(other.input_language)) return false
        if (!input_ids.contentEquals(other.input_ids)) return false
        if (!word2ph.contentEquals(other.word2ph)) return false
        if (!attention_mask.contentEquals(other.attention_mask)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = input_seq.contentHashCode()
        result = 31 * result + input_t.contentHashCode()
        result = 31 * result + input_language.contentHashCode()
        result = 31 * result + input_ids.contentHashCode()
        result = 31 * result + word2ph.contentHashCode()
        result = 31 * result + attention_mask.contentHashCode()
        return result
    }
}