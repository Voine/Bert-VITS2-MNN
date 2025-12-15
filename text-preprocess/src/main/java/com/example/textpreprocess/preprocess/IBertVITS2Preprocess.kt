package com.example.textpreprocess.preprocess

import androidx.annotation.IntDef
import androidx.annotation.Keep

/**
 * Author: Voine
 * Date: 2025/12/9
 * Description:
 */
interface IBertVITS2Preprocess {
    suspend fun preprocess(
        text: String,
        @LanguageType language: Int,
    ): PreprocessResult?
}

internal interface IBertVITS2ProcessInternal {
    suspend fun preprocess(
        text: String,
    ): PreprocessResult?
}

@Keep
data class PreprocessResult(
    val input_seq: List<Int> = emptyList(),
    val input_t: List<Int> = emptyList(),
    val input_language: List<Int> = emptyList(),
    val input_ids: List<Int> = emptyList(),
    val input_word2ph: List<Int> = emptyList(),
    val attention_mask: List<Int> = emptyList(),
    val errorMsg: String? = null,
)

const val LANGUAGE_ZH = 0
const val LANGUAGE_EN = 1
const val LANGUAGE_JP = 2
const val LANGUAGE_MIX_ZH_EN = 3 //中英混
@IntDef(
    LANGUAGE_ZH,
    LANGUAGE_EN,
    LANGUAGE_JP,
    LANGUAGE_MIX_ZH_EN
)
@Retention(AnnotationRetention.SOURCE)
annotation class LanguageType