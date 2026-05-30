package com.example.bertvits2mnn

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bertvits2_infer_wrapper.impl.BertVITS2SimpleInferImpl
import com.example.bertvits2_infer_wrapper.interfaces.IBertVITS2SimpleInfer
import com.example.bertvits2mnn.utils.saveWavFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Author: Voine
 * Date: 2025/4/1
 * Description: main view model
 */
class VoiceViewModel : ViewModel() {
    private val _uiState: MutableSharedFlow<UIState> =
        MutableSharedFlow(replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val uiState = _uiState.asSharedFlow()
    private val soundHandler: SoundPlayHandler by lazy {
        SoundPlayHandler()
    }

    @Volatile
    private var currentSpkName: String = ""

    private lateinit var speakers: List<String>
    private val bv2SimpleInferImpl: IBertVITS2SimpleInfer by lazy {
        BertVITS2SimpleInferImpl(BV2Application.context)
    }
    private var firstInferDone: Boolean = false

    fun init() {
        setLoading(true, "正在初始化...")
        viewModelScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            setLoading(true, "正在初始化 VITS...")
            bv2SimpleInferImpl.init()
            initCharacters()
            setDefaultState()
            val endTime = System.currentTimeMillis()
            Log.d("init", "init time: ${endTime - startTime} ms")
            updateLogcat("初始化耗时: ${endTime - startTime} ms")
            setLoading(false)
        }
    }

    fun updateInputText(string: String) {
        Log.d("updateInputText", "string: $string")
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        _uiState.tryEmit(sendState.copy(inputText = string))
    }

    fun startAudioInference(text: String) {
        viewModelScope.launch(Dispatchers.Default) {
            updateEnableSavedBtnState(false)
            Log.d("runVits", "cleanedText start infer: $text")
            if (!firstInferDone) {
                firstInferDone = true
                setLoading(true, "开始启动推理...首次推理时间较长")
            } else {
                setLoading(true, "开始启动推理...")
            }
            val startTime = System.currentTimeMillis()
            val inferResult = runCatching {
                bv2SimpleInferImpl.infer(text, currentSpkName)
            }.onFailure {
                Log.e("runVits", "infer threw exception", it)
                updateLogcat("推理异常: ${it.message}")
            }.getOrNull()

            // 不管成功失败，都先把 loading 状态清掉，避免 UI 卡在“生成中”
            val endTime = System.currentTimeMillis()
            setLoading(false)

            if (inferResult == null) {
                updateLogcat("推理失败（耗时 ${endTime - startTime} ms），请查看日志")
                return@launch
            }
            val (result, sampleRate) = inferResult
            updateLogcat("推理耗时: ${endTime - startTime} ms")
            Log.d("runVits", "result: ${result?.joinToString(",", limit = 10)}")
            Log.d("runVits", "infer time: ${endTime - startTime} ms")
            result ?: run {
                updateLogcat("推理结果为空")
                return@launch
            }
            soundHandler.sendSound(result, sampleRate)
            val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
            _uiState.tryEmit(sendState.copy(sampleRate = sampleRate))
            updateEnableSavedBtnState(true, result.toList())
            // Auto Save the result to a WAV file if in debug mode
            if (BuildConfig.DEBUG) {
                launch(Dispatchers.IO) {
                    runCatching {
                        saveWavFile(
                            BV2Application.context,
                            BV2Application.context.filesDir.absolutePath,
                            result,
                            sampleRate = sampleRate,
                            "output_${System.currentTimeMillis()}.wav"
                        )
                    }.onFailure {
                        Log.e("runVits", "saveWavFile error: ${it.message}")
                    }
                }
            }
        }
    }

    fun selectCharacter(string: String) {
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        Log.d("selectCharacter", "string: $string")
        updateLogcat("选择角色: $string")
        val currentCharacter = sendState.selectedCharacter
        if (getDefaultTextFromSpeaker(currentCharacter) == sendState.inputText) {
            // If the input text is the default text of the previous character, update it to the new character's default text
            val newDefaultText = getDefaultTextFromSpeaker(string)
            _uiState.tryEmit(sendState.copy(selectedCharacter = string, inputText = newDefaultText))
            currentSpkName = string
            return
        }
        _uiState.tryEmit(sendState.copy(selectedCharacter = string))
        currentSpkName = string
    }


    fun updateLengthScale(lengthScale: Float) {
        Log.d("updateLengthScale", "lengthScale: $lengthScale")
        updateLogcat("语音缩放系数: $lengthScale")
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        _uiState.tryEmit(sendState.copy(currentLengthScale = lengthScale))
        bv2SimpleInferImpl.setAudioLengthScale(lengthScale)
    }

    fun saveLocal(savedResult: FloatArray?, sampleRate: Int?) {
        val path = BV2Application.context.getExternalFilesDir(null)?.absolutePath
        if (savedResult == null || path == null) {
            updateLogcat("保存失败 ${if (savedResult == null) "音频数据为空" else "路径为空"}")
            return
        }
        val fileName = "output_${System.currentTimeMillis()}.wav"
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                saveWavFile(
                    BV2Application.context,
                    path,
                    savedResult,
                    sampleRate = sampleRate,
                    fileName
                )
            }.onFailure {
                Log.e("runVits", "saveWavFile error", it)
            }.onSuccess {
                Log.d("runVits", "saveWavFile success")
                updateLogcat("保存成功, 路径为：${path.replace("/storage/emulated/0", "文件管理")} $fileName")
            }
        }
    }

    private fun updateEnableSavedBtnState(enable: Boolean, result: List<Float>? = null) {
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        Log.d("updateEnableBtnState", "enable: $enable")
        _uiState.tryEmit(sendState.copy(saveBtnEnabled = enable, savedResult = result))
    }

    private fun setDefaultState() {
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        Log.d("setDefaultState", "sendState: $sendState")
        currentSpkName = speakers[0]
        _uiState.tryEmit(
            sendState.copy
                (
                inputText = getDefaultTextFromSpeaker(speakers[0]),
                selectedCharacter = speakers[0]
            )
        )
    }

    private fun getDefaultTextFromSpeaker(speaker: String): String {
        return when (speaker) {
            "陈_ZH" -> "博士，欢迎来到龙门。"
            "珐露珊_ZH" -> "旅行者，好久不见。"
            "甘雨_ZH" -> "工作还没有做完，又要开始搬砖了。"
            "APPLe_EN" -> "Greetings, madam. I am here. Clouds help predict the weather."
            "Sonetto_EN" -> "Timekeeper, at your service. The stars shine bright tonight."
            "Vertin_EN" -> "The storm is coming. We must prepare ourselves."
            "八重神子_JP"-> "たびびと、きょうはどんなおもしろいほんをもってきてくれたの？もしないようがつまらなかったら、わたし、へんしゅうぶに『しげき』にいこうかな～？"
            "宵宫_JP" -> "こんにちは、皆さん。今日は素晴らしい一日ですね。"
            "椿_JP" -> "あなたといると、なぜか落ち着くの。"
            "野兽先辈_JP" -> "にじゅうよんさいはがくせいです"
            "22娘_MIX" -> "RTX 5090 将于明年发布，敬请期待！"
            else -> "你好，欢迎使用语音合成系统。"
        }
    }

    private fun initCharacters() {
        this.speakers = bv2SimpleInferImpl.getSpkNameList()
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        _uiState.tryEmit(sendState.copy(characters = speakers))
    }

    private fun setLoading(loading: Boolean, hint: String = "") {
        Log.d("setLoading", "loading: $loading, hint: $hint")
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        _uiState.tryEmit(sendState.copy(isLoading = loading, loadingHint = hint))
    }

    private fun updateLogcat(logcat: String) {
        Log.d("updateLogcat", "logcat: $logcat")
        val sendState = _uiState.replayCache.firstOrNull() ?: UIState()
        val currentLogcat = sendState.logcat
        _uiState.tryEmit(sendState.copy(logcat = "$currentLogcat\n$logcat"))
    }

    override fun onCleared() {
        bv2SimpleInferImpl.release()
        super.onCleared()
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
    val saveBtnEnabled: Boolean = false,
    val savedResult: List<Float>? = null,
    val sampleRate: Int? = null,
)