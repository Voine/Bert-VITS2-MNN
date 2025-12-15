package com.example.textpreprocess.zh

import android.content.Context
import androidx.annotation.Keep
import com.example.cppjieba.CppJiebaJNI
import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum
import com.github.houbb.pinyin.util.PinyinHelper
import java.io.File
import kotlin.collections.listOf

/**
 * Author: Voine
 * Date: 2025/4/17
 * Description: BertVits2 text preprocess
 */
class BV2Preprocess(jieba_dict: String,
                    jieba_hmm: String,
                    jieba_user: String,
                    jieba_idf: String,
                    jieba_stop: String,
                    val opencpop_strict_path: String,
) {

    companion object {
        fun initPinyinHelper(context: Context) {
            // trigger pinyin lib init
//            PinyinHelper.initHelper(context)
            PinyinHelper.toPinyin("你好", PinyinStyleEnum.NUM_LAST)
        }
    }

    private val pinyinSymbolMap: Map<String, List<String>>
    private val punctuation: String
    private val jiebaNativeLib: CppJiebaJNI
    private val toneSandhi: ToneSandhi
    private val normalizer: Normalizer

    //字母伪读音
    private val letter2chs = mapOf(
        "A" to listOf("ei", "i"),
        "B" to listOf("b", "i"),
        "C" to listOf("s", "ei"),
        "D" to listOf("d", "i"),
        "E" to listOf("y", "i"),
        "F" to listOf("E", "f"),
        "G" to listOf("j", "i"),
        "H" to listOf("ei", "i", "ch"),
        "I" to listOf("AA", "ai"),
        "J" to listOf("zh", "ei"),
        "K" to listOf("k", "ei"),
        "L" to listOf("EE", "l", "e"),
        "M" to listOf("EE", "m", "u"),
        "N" to listOf("EE", "en"),
        "O" to listOf("OO", "ou"),
        "P" to listOf("p", "i"),
        "Q" to listOf("k", "i", "u"),
        "R" to listOf("EE", "er"),
        "S" to listOf("EE", "s"),
        "T" to listOf("t", "i"),
        "U" to listOf("y", "ou"),
        "V" to listOf("w", "i"),
        "W" to listOf("d", "a", "b", "iu"),
        "X" to listOf("e", "ai", "k", "s"),
        "Y" to listOf("w", "ai"),
        "Z" to listOf("z", "i"),
    )

    //字母声调
    private val letterTone: Map<String, List<Int>> = mapOf(
        "A" to listOf(4, 5),
        "B" to listOf(4, 5),
        "C" to listOf(4, 5),
        "D" to listOf(4, 5),
        "E" to listOf(4, 5),
        "F" to listOf(1, 5),
        "G" to listOf(4, 5),
        "H" to listOf(4, 5, 5),
        "I" to listOf(4, 5),
        "J" to listOf(4, 5),
        "K" to listOf(4, 5),
        "L" to listOf(4, 5, 5),
        "M" to listOf(4, 5, 5),
        "N" to listOf(1, 5),
        "O" to listOf(1, 5),
        "P" to listOf(4, 5),
        "Q" to listOf(4, 5, 5),
        "R" to listOf(2, 5),
        "S" to listOf(4, 5),
        "T" to listOf(1, 5),
        "U" to listOf(1, 1),
        "V" to listOf(1, 1),
        "W" to listOf(1, 1, 5, 5),
        "X" to listOf(4, 5, 5, 5),
        "Y" to listOf(4, 5),
        "Z" to listOf(4, 5),
    )

    init {
        pinyinSymbolMap = loadPinyinToSymbolMap()
        punctuation = com.example.textpreprocess.zh.punctuation.joinToString(separator = "")
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

    //test func
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
            val words = segmentWords(sentence)
            val preMergedWords = toneSandhi.preMergeForModify(words)

            for (wordPos in preMergedWords) {
                val word = wordPos.word
                val initials: List<String>
                val finals: List<String>
                if (word in punctuation) {
                    initials = listOf(word)
                    finals = listOf(word)
                } else if (word.firstOrNull() in ('A'..'Z') || word.firstOrNull() in ('a'..'z')) {
                    initials = listOf(word.uppercase())
                    finals = listOf(word.uppercase())
                } else {
                    val pinyins = PinyinHelper.toPinyin(word, PinyinStyleEnum.NUM_LAST)
                        .replace('ü', 'v')
                        .split(" ")
                    initials = pinyins.map { it.first().toString() }.toList()
                    if (word == "哔哩哔哩" || word == "哔哩") {
                        // 特例处理, 拼音库一般会把哔哩转成 bi4 li1, 但我们希望它们都是一声
                        finals = pinyins.map { it.drop(1).replace("4", "1") }.toList()
                    } else {
                        finals = pinyins.map { it.drop(1) }.toList()
                    }
                }

                val modifiedFinals = if(word.firstOrNull() in ('A'..'Z') || word.firstOrNull() in ('a'..'z')) {
                    finals
                } else toneSandhi.modifiedTone(word, wordPos.pos, finals)

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
                    if (c.firstOrNull() in ('A'..'Z')) {
                        for (letter in c) {
                            val letterChs = letter2chs[letter.uppercase()] ?: listOf(",")
                            phones.addAll(letterChs)
                            tones.addAll(letterTone[letter.toString()] ?: List(letterChs.size) {1})
                            word2ph.add(letterChs.size)
                        }
                        continue
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

    private fun loadPinyinToSymbolMap(): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        File(opencpop_strict_path).inputStream().bufferedReader().useLines { lines ->
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

    /**
     * 使用 jieba 对文本分词并返回词性标注结果，供外部（如 MIX 处理）使用。
     * tag 为 "eng" 的词表示英文。
     */
    fun segmentWords(text: String): List<WordPos> {
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
        val symbolList = pinyinToSymbolMap[normalized] ?: listOf(",") // fallback
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
@Keep
data class G2PResult(
    val phones: List<String>,
    val tones: List<Int>,
    val word2ph: List<Int>
)

@Keep
data class WordPos(val word: String, val pos: String)