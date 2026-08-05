package com.example.bertvits2_infer_wrapper.interfaces

/**
 * Author: Voine
 * Date: 2025/12/9
 * Description: simple version of IBertVITS2Infer for quick infer, use internal models
 */
interface IBertVITS2SimpleInfer {

    suspend fun init(): Boolean

    fun getSpkNameList(): List<String>

    /**
     * 角色的完整信息，[init] 成功后才有内容。
     * 相比 [getSpkNameList] 额外给出语言与采样率，供系统 TTS 引擎这类
     * 「合成前就必须知道采样率」的场景使用。
     */
    fun getSpeakerInfoList(): List<SpeakerInfo>

    /**
     * @return  float arr, sample rate
     */
    suspend fun infer(
        text: String,
        spkName: String,
    ): Pair<FloatArray?, Int>?

    fun setAudioLengthScale(length_scale: Float)

    fun release()
}

/**
 * @param name        角色名，即 [IBertVITS2SimpleInfer.getSpkNameList] 中的元素
 * @param languageTag BCP-47 语言标签，如 zh-CN / en-US / ja-JP
 * @param sampleRate  该角色模型的输出采样率
 */
data class SpeakerInfo(
    val name: String,
    val languageTag: String,
    val sampleRate: Int,
)

