package com.example.textpreprocess.jp

import android.content.Context
import android.util.Log
import com.example.textpreprocess.preprocess.IBertVITS2ProcessInternal
import com.example.textpreprocess.preprocess.PreprocessResult
import com.example.textpreprocess.zh.intersperse
import com.example.textpreprocess.zh.num_zh_tones
import com.example.textpreprocess.zh.zhSymbolsMap

/**
 * Author: Voine
 * Date: 2025/12/9
 * Description:
 */
class JPBV2Impl(val context: Context): IBertVITS2ProcessInternal {

    private val jpTextProcessor: JapaneseTextPreprocessor by lazy {
        JapaneseTextPreprocessor().apply {
            initOpenJTalk("open_jtalk_dic_utf_8-1.11", context.assets)
        }
    }

    private val bertEncoder by lazy { JapaneseCharTokenizer(
        "${context.filesDir.absolutePath}/bert/jp/vocab.txt"
    ) }

    override suspend fun preprocess(text: String): PreprocessResult? {
        val (_phones, _tones, _word2ph) = jpTextProcessor.g2p(text)
        var phones = _phones.mapNotNull { zhSymbolsMap[it] }
        var tones = _tones.map { it + num_zh_tones }
        var langIds = List(phones.size) { 1 }
        if (phones.size != _tones.size) {
            return PreprocessResult(
                errorMsg = "phones size error: ${phones.size}, tones size: ${_tones.size}"
            )
        }
        //add blank
        phones = intersperse(phones, 0)
        tones = intersperse(tones, 0)
        langIds = intersperse(langIds, 0)
        val word2ph = _word2ph.map { it * 2 }.toMutableList()
        word2ph[0] += 1

        Log.i("JPBV2Impl", "start bert encoding")
        val (bertIds, mask) = bertEncoder.encode(text)

        Log.i("JPBV2Impl", "all result is: input_seq: ${phones} input_t: ${tones} input_language: ${langIds} input_ids: ${bertIds} input_word2ph: ${word2ph} attention_mask: ${mask}")
        return PreprocessResult(
            input_seq = phones,
            input_t = tones,
            input_language = langIds,
            input_ids = bertIds,
            input_word2ph = word2ph,
            attention_mask = mask,
        )
    }
}