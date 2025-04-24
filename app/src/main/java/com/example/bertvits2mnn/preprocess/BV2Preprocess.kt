package com.example.bertvits2mnn.preprocess

import android.content.Context
import com.example.cppjieba.CppJiebaJNI
import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum
import com.github.houbb.pinyin.util.PinyinHelper
import kotlin.collections.listOf

/**
 * Author: Voine
 * Date: 2025/4/17
 * Description: BertVits2 text preprocess
 */
class BV2Preprocess(context: Context,
                    jieba_dict: String,
                    jieba_hmm: String,
                    jieba_user: String,
                    jieba_idf: String,
                    jieba_stop: String,
                    val opencpop_strict_path: String,
) {

    private val pinyinSymbolMap: Map<String, List<String>>
    private val punctuation: String
    private val jiebaNativeLib: CppJiebaJNI
    private val toneSandhi: ToneSandhi
    private val normalizer: Normalizer

    init {
        // trigger pinyin lib init
        PinyinHelper.toPinyin("你好", PinyinStyleEnum.NUM_LAST)
        pinyinSymbolMap = loadPinyinToSymbolMap(context)
        punctuation = listOf("!", "?", "…", ",", ".", "'", "-").joinToString(separator = "")
        jiebaNativeLib = CppJiebaJNI()
        jiebaNativeLib.initJieba(
            jieba_dict,
            jieba_hmm,
            jieba_user,
            jieba_idf,
            jieba_stop
        )
        toneSandhi = ToneSandhi(jiebaNativeLib)
        normalizer = Normalizer()
    }

    fun normalizeText(text: String): String {
        return normalizer.normalizeText(text, punctuation)
    }

    fun preprocess(text: String): G2PResult {
        val normalized = normalizeText(text)
        return g2p(normalized)
    }

    fun preprocessWithNormalizedText(normalizedText: String): G2PResult {
        return g2p(normalizedText)
    }


    //Grapheme to Phoneme
    private fun g2p(sentences: String): G2PResult {
        val phones = mutableListOf<String>()
        val tones = mutableListOf<Int>()
        val word2ph = mutableListOf<Int>()

        val sentences = splitSentences(sentences)
        for (sentence in sentences) {
            val cleanSentence = sentence.replace(Regex("[a-zA-Z]+"), "")
            val words = segmentWords(cleanSentence)
            val preMergedWords = toneSandhi.preMergeForModify(words)

            for (wordPos in preMergedWords) {
                val word = wordPos.word
                val initials: List<String>
                val finals: List<String>
                if (word in punctuation) {
                    initials = listOf(word)
                    finals = listOf(word)
                } else {
                    val pinyins = PinyinHelper.toPinyin(word, PinyinStyleEnum.NUM_LAST).replace('ü', 'v').split(" ")
                    initials = pinyins.map { it.first().toString() }.toList()
                    finals = pinyins.map { it.drop(1) }.toList()
                }

                val modifiedFinals = toneSandhi.modifiedTone(word, wordPos.pos, finals)

                for ((c, v) in initials.zip(modifiedFinals)) {
                    val raw = c + v
                    if (c == v) {
                        if (c in punctuation) {
                            phones.add(c)
                            tones.add(0)
                            word2ph.add(1)
                            continue
                        }
                    }
                    val tone = extractTone(v)
                    val (symbols, t) = mapPinyinToPhoneme(raw, tone, pinyinSymbolMap)
                    phones.addAll(symbols)
                    tones.addAll(List(symbols.size) { t })
                    word2ph.add(symbols.size)
                }
            }
        }

        return G2PResult(
            phones = listOf("_") + phones + listOf("_"),
            tones = listOf(0) + tones + listOf(0),
            word2ph = listOf(1) + word2ph + listOf(1)
        )
    }

    private fun loadPinyinToSymbolMap(context: Context): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        context.assets.open(opencpop_strict_path).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.trim().split("	")
                if (parts.size == 2) {
                    val key = parts[0]
                    val value = parts[1].split(" ")
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun splitSentences(text: String): List<String> {
        val regex = Regex("(?<=[$punctuation])\\s*")
        return text.split(regex).filter { it.isNotBlank() }
    }

    private fun segmentWords(text: String): List<WordPos> {
        return callJiebaSegmentation(text)
    }

    private fun callJiebaSegmentation(text: String): List<WordPos> {
        // JNI or AIDL 调用 C++ 分词服务
        return jiebaNativeLib.tag(text).map {
            WordPos(it.word, it.tag)
        }
    }

    // 步骤 3：拼音提取 + 音节拆解 + 拼音映射

    private fun extractTone(pinyin: String): Int {
        val toneChar = pinyin.lastOrNull()
        return if (toneChar != null && toneChar in '1'..'5') toneChar.digitToInt() else 5
    }

    private fun stripTone(pinyin: String): String {
        return if (pinyin.lastOrNull()?.isDigit() == true) pinyin.dropLast(1) else pinyin
    }

    private fun mapPinyinToPhoneme(
        pinyin: String,
        tone: Int,
        pinyinToSymbolMap: Map<String, List<String>>
    ): Pair<List<String>, Int> {
        val normalized = normalizeSyllable(stripTone(pinyin))
        val symbolList = pinyinToSymbolMap[normalized] ?: listOf("sil") // fallback
        return Pair(symbolList, tone)
    }

    private fun normalizeSyllable(pinyin: String): String {
        // 对应 Python 中拼音拼写修正部分
        val replacements = mapOf(
            "uei" to "ui", "iou" to "iu", "uen" to "un",
            "ing" to "ying", "i" to "yi", "in" to "yin", "u" to "wu",
            "v" to "yu", "e" to "e", "u:an" to "yuan"
        )
        return replacements.entries.fold(pinyin) { acc, (k, v) ->
            if (acc == k) v else acc
        }
    }
}

data class G2PResult(
    val phones: List<String>,
    val tones: List<Int>,
    val word2ph: List<Int>
)

data class WordPos(val word: String, val pos: String)