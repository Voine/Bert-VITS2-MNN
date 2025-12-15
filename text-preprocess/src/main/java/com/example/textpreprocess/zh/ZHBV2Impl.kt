package com.example.textpreprocess.zh

import android.util.Log
import com.example.cpptokenizer.CppTokenizerJNI
import com.example.textpreprocess.preprocess.IBertVITS2ProcessInternal
import com.example.textpreprocess.preprocess.PreprocessResult


/**
 * Author: Voine
 * Date: 2025/12/9
 * Description: 纯中文预处理，Bert tokenizer 编码 + G2P
 */
class ZHBV2Impl(
    private val preprocessor: BV2Preprocess,
    private val bertTokenizer: CppTokenizerJNI,
): IBertVITS2ProcessInternal  {

    val logTag: String
        get() = "ZHBV2Impl"

    override suspend fun preprocess(text: String): PreprocessResult? {
        val normalized = preprocessor.normalizeText(text)
        val bertResult = processTextWithLetter(normalized)
        val g2pResult = preprocessor.preprocessWithNormalizedText(normalized)
        var phones = g2pResult.phones.mapNotNull { zhSymbolsMap[it] }
        var tones = g2pResult.tones
        var langIds = List(phones.size) { 0 }
        if (phones.size != tones.size) {
            return PreprocessResult(
                errorMsg = "phones size error: ${phones.size}, tones size: ${tones.size}"
            )
        }
        //add blank
        phones = intersperse(phones, 0)
        tones = intersperse(tones, 0)
        langIds = intersperse(langIds, 0)
        val word2ph = g2pResult.word2ph.map { it * 2 }.toMutableList()
        word2ph[0] += 1

        //    assert len(word2ph) == len(text) + 2
        if (normalized.length + 2 != word2ph.size) {
            return PreprocessResult(
                errorMsg = "word2ph size error: ${word2ph.size}, text length: ${normalized.length}"
            )
        }
        if (bertResult.size != word2ph.size) {
            return PreprocessResult(
                errorMsg = "bertResult size error: ${bertResult.size}, word2ph size: ${word2ph.size}"
            )
        }

        return PreprocessResult(
            input_seq = phones,
            input_t = tones,
            input_language = langIds,
            input_ids = bertResult.toList(),
            input_word2ph = word2ph,
            attention_mask = List(bertResult.size) { 1 },
        )
    }

    /**
     * 处理含有英文字母的文本
     * 英文字母部分全部填 0，其他部分用 Bert tokenizer 编码
     */
    private fun processTextWithLetter(text: String): IntArray {
        val englishIndices = mutableListOf<Int>()
        val nonEnglishBuilder = StringBuilder()
        // 1. 记录英文字母索引，构建去除英文字母的新字符串
        text.forEachIndexed { idx, c ->
            if (c.isLetter() && ( c in 'A'..'Z' ||  c in 'a'..'z' )) {
                englishIndices.add(idx)
            } else {
                nonEnglishBuilder.append(c)
            }
        }
        val nonEnglishText = nonEnglishBuilder.toString()
        // 2. 用 Bert tokenizer 编码
        Log.d(logTag, "nonEnglishText: $nonEnglishText")
        val nonEnglishIds = bertTokenizer.encodeText(nonEnglishText)
        // 3. 合成最终 input id list
        val result = mutableListOf<Int>()
        result.add(nonEnglishIds[0]) // addSpecialTokens=true 会在头尾增加特殊 id
        var nonEnIdx = 1
        for (i in text.indices) {
            if (englishIndices.contains(i)) {
                result.add(0) // 英文字母部分全部填 0
            } else {
                result.add(nonEnglishIds[nonEnIdx++])
            }
        }
        result.add(nonEnglishIds.last())
        return result.toIntArray()
    }
}