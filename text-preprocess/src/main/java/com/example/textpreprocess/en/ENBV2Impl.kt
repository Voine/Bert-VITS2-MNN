package com.example.textpreprocess.en

import android.content.Context
import android.util.Log
import com.example.cpptokenizer.CppTokenizerJNI
import com.example.textpreprocess.preprocess.IBertVITS2ProcessInternal
import com.example.textpreprocess.preprocess.PreprocessResult
import com.example.textpreprocess.zh.intersperse
import com.example.textpreprocess.zh.num_ja_tones
import com.example.textpreprocess.zh.num_zh_tones
import com.example.textpreprocess.zh.zhSymbolsMap

/**
 * Author: Voine
 * Date: 2025/12/9
 * Description:
 */

private val UNK_ID = zhSymbolsMap["UNK"] ?: 0

class ENBV2Impl(val context: Context): IBertVITS2ProcessInternal {
    private val lexicon by lazy {
        CmuLexiconTxt.fromLocalFile(filePath = "${context.filesDir.absolutePath}/preprocess/en/cmudict.rep",
            context.filesDir.absolutePath)
    }
    private val g2p by lazy {
        EnglishG2P(symbols = com.example.textpreprocess.zh.normal_symbols.toSet(), lexicon = lexicon)
    }
    private val bertTokenizer: CppTokenizerJNI by lazy {
        CppTokenizerJNI().apply {
            initTokenizerFromBlobSentencePiece("${context.filesDir.absolutePath}/bert/en/spm.model")
        }
    }
    override suspend fun preprocess(text: String): PreprocessResult? {
        // 1. 文本归一化（数字展开 + 标点替换 + 标点后加空格），与 Python text_normalize 对齐
        val normalizedText = g2p.textNormalize(text)

        // 2. 用与 BERT 相同的 SentencePiece tokenizer 把归一化文本切成 wordpiece，
        //    传给 g2p 以保证 word2ph 是按 wordpiece 个数分发的。
        //    这样最终 word2ph 长度 == tokens.size + 2，与
        //    [CLS] + encode(normalizedText) + [SEP] 长度对齐，
        //    后续 BERT 特征 expand 到 phone 维度时才不会错位。
        Log.i("ENBV2Impl", "normalized text: $normalizedText")
        val tokens = bertTokenizer.tokenizeText(normalizedText).toList()
        Log.i("ENBV2Impl", "bert tokens(${tokens.size}): $tokens")

        val result = g2p.g2p(normalizedText, tokens)

        // 3. Phone 映射：未知 phone 映射到 UNK，不能 mapNotNull 静默丢弃
        var phones = result.phones.map { zhSymbolsMap[it] ?: UNK_ID }
        var tones = result.tones.map { it + num_ja_tones + num_zh_tones }
        var langIds = List(phones.size) { 2 }
        if (phones.size != tones.size) {
            return PreprocessResult(
                errorMsg = "phones size error: ${phones.size}, tones size: ${tones.size}"
            )
        }
        //add blank
        phones = intersperse(phones, 0)
        tones = intersperse(tones, 0)
        langIds = intersperse(langIds, 0)
        val word2ph = result.word2ph.map { it * 2 }.toMutableList()
        word2ph[0] += 1

        // 4. BERT 编码：使用与 g2p 完全一致的 normalizedText
        Log.i("ENBV2Impl", "start bert encoding")
        val bertResult = bertTokenizer.encodeText(normalizedText).toMutableList()
        bertResult.add(0, 1) // add cls
        bertResult.add(2) // add sep
        Log.i(
            "ENBV2Impl",
            "all result is: input_seq: ${phones}\n input_t: ${tones} \n input_language: ${langIds} \n input_ids: ${bertResult.toList()} \n input_word2ph: ${word2ph} \n attention_mask: ${List(bertResult.size) { 1 }}"
        )
        if (bertResult.size != word2ph.size) {
            return PreprocessResult(
                errorMsg = "bertResult size error: ${bertResult.size}, word2ph size: ${word2ph.size}, tokens size: ${tokens.size}"
            )
        }
        return PreprocessResult(
            input_seq = phones,
            input_t = tones,
            input_language = langIds,
            input_ids = bertResult,
            input_word2ph = word2ph,
            attention_mask = List(bertResult.size) { 1 },
        )
    }
}