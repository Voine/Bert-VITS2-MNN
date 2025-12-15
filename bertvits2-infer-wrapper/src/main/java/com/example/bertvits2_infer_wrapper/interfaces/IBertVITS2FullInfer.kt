package com.example.bertvits2_infer_wrapper.interfaces

import com.example.bertvits2.IBertVITS2JNI
import com.example.textpreprocess.preprocess.IBertVITS2Preprocess
import com.example.textpreprocess.preprocess.PreprocessResult

/**
 * Author: Voine
 * Date: 2025/12/9
 * Description: full version of IBertVITS2Infer for complete control
 * work flow: init preprocess && init loader -> set model path -> preprocess -> infer -> destroy loader
 */
interface IBertVITS2FullInfer: IBertVITS2Preprocess, IBertVITS2JNI {

    suspend fun initPreprocessor(): Boolean

    fun startAudioInfer(
        preprocessResult: PreprocessResult,
        spkid: Int
    ): FloatArray?
}