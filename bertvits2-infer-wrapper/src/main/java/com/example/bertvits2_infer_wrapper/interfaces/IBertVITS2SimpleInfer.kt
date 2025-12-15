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
     * @return  float arr, sample rate
     */
    suspend fun infer(
        text: String,
        spkName: String,
    ): Pair<FloatArray?, Int>?

    fun setAudioLengthScale(length_scale: Float)

    fun release()
}

