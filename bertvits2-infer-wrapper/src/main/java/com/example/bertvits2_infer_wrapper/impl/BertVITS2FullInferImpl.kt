package com.example.bertvits2_infer_wrapper.impl

import android.content.Context
import android.util.Log
import com.example.bertvits2.BertVITS2JNI
import com.example.bertvits2.IBertVITS2JNI
import com.example.bertvits2_infer_wrapper.interfaces.IBertVITS2FullInfer
import com.example.bertvits2_infer_wrapper.utils.copyAssets2Local
import com.example.bertvits2_infer_wrapper.utils.safeResume
import com.example.textpreprocess.preprocess.BertVITS2PreprocessFactoryImpl
import com.example.textpreprocess.preprocess.IBertVITS2Preprocess
import com.example.textpreprocess.preprocess.PreprocessResult
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Author: Voine
 * Date: 2025/12/9
 * Description:
 */
class BertVITS2FullInferImpl(val context: Context): IBertVITS2FullInfer,
    IBertVITS2Preprocess by BertVITS2PreprocessFactoryImpl(context),
    IBertVITS2JNI by BertVITS2JNI() {

    override suspend fun initPreprocessor(): Boolean {
        val preprocessResult = suspendCancellableCoroutine {
            context.copyAssets2Local(
                true,
                "preprocess",
                context.filesDir.absolutePath
            ) { isSuccess: Boolean, absPath: String ->
                Log.i("copyAssets2Local", "isSuccess: $isSuccess, absPath: $absPath")
                it.safeResume(absPath)
            }
        }
        if (preprocessResult.isEmpty()) {
            Log.e("BertVITS2FullInferImpl", "Failed to copy preprocess assets")
            return false
        }
        return true
    }

    override fun startAudioInfer(
        preprocessResult: PreprocessResult,
        spkid: Int
    ): FloatArray? {
        return startAudioInfer(
            preprocessResult.input_seq.toIntArray(),
            preprocessResult.input_t.toIntArray(),
            preprocessResult.input_language.toIntArray(),
            preprocessResult.input_ids.toIntArray(),
            preprocessResult.input_word2ph.toIntArray(),
            preprocessResult.attention_mask.toIntArray(),
            spkid
        )
    }
}