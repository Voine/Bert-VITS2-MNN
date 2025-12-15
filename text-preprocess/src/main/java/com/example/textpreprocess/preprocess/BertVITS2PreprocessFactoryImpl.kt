package com.example.textpreprocess.preprocess

import android.content.Context
import android.util.Log
import com.example.cpptokenizer.CppTokenizerJNI
import com.example.textpreprocess.en.ENBV2Impl
import com.example.textpreprocess.jp.JPBV2Impl
import com.example.textpreprocess.mix.ZHENMixImpl
import com.example.textpreprocess.zh.BV2Preprocess
import com.example.textpreprocess.zh.ZHBV2Impl
import java.io.File

/**
 * Author: Voine
 * Date: 2025/12/9
 * Description:
 */

class BertVITS2PreprocessFactoryImpl(val context: Context) : IBertVITS2Preprocess {
    private val bV2Preprocess by lazy { BV2Preprocess(
        jieba_dict =  "${context.filesDir.absolutePath}/preprocess/zh/dict/jieba.dict.utf8",
        jieba_user = "${context.filesDir.absolutePath}/preprocess/zh/dict/user.dict.utf8",
        jieba_hmm = "${context.filesDir.absolutePath}/preprocess/zh/dict/hmm_model.utf8",
        jieba_idf = "${context.filesDir.absolutePath}/preprocess/zh/dict/idf.utf8",
        jieba_stop = "${context.filesDir.absolutePath}/preprocess/zh/dict/stop_words.utf8",
        opencpop_strict_path = "${context.filesDir.absolutePath}/preprocess/zh/opencpop-strict.txt"
        )
    }
    private val tokenizer by lazy { CppTokenizerJNI().apply {
        initTokenizerFromBlobJson("${context.filesDir.absolutePath}/bert/zh/tokenizer.json")
    }}
    private val zhPreprocess: IBertVITS2ProcessInternal by lazy {
        ZHBV2Impl(bV2Preprocess, tokenizer)
    }
    private val enPreprocess: IBertVITS2ProcessInternal by lazy {
        ENBV2Impl(context)
    }
    private val jpPreprocess: IBertVITS2ProcessInternal by lazy {
        JPBV2Impl(context)
    }
    private val zhEnMixPreprocess: IBertVITS2ProcessInternal by lazy {
        ZHENMixImpl(bV2Preprocess,
            cmudictPath = "${context.filesDir.absolutePath}/preprocess/en/cmudict.rep",
            cacheDir = context.filesDir.absolutePath)
    }
    override suspend fun preprocess(
        text: String,
        language: Int
    ): PreprocessResult? {
        Log.i("BertVITS2PreprocessImplFactory", "start preprocess language $language")
        return when (language) {
            LANGUAGE_ZH -> {
                zhPreprocess.preprocess(text)
            }
            LANGUAGE_EN -> {
                enPreprocess.preprocess(text)
            }
            LANGUAGE_JP -> {
                jpPreprocess.preprocess(text)
            }
            LANGUAGE_MIX_ZH_EN -> {
                zhEnMixPreprocess.preprocess(text)
            }
            else -> {
                Log.e("BertVITS2PreprocessImplFactory", "Unsupported language type: $language")
                return null
            }
        }
    }
}