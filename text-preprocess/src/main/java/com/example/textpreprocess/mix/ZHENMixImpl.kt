package com.example.textpreprocess.mix

import android.util.Log
import com.example.textpreprocess.en.CmuLexiconTxt
import com.example.textpreprocess.en.EnglishG2P
import com.example.textpreprocess.preprocess.IBertVITS2ProcessInternal
import com.example.textpreprocess.preprocess.PreprocessResult
import com.example.textpreprocess.zh.BV2Preprocess
import com.example.textpreprocess.zh.WordPos
import com.example.textpreprocess.zh.intersperse
import com.example.textpreprocess.zh.normal_symbols
import com.example.textpreprocess.zh.num_ja_tones
import com.example.textpreprocess.zh.num_zh_tones
import com.example.textpreprocess.zh.zhSymbolsMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Author: Voine
 * Date: 2026/2/27
 * Description: 中英混合预处理（自动检测模式）
 */
private val UNK_ID = zhSymbolsMap["UNK"] ?: 0

// 语言到 tone 偏移的映射
private const val ZH_TONE_START = 0
private val EN_TONE_START = num_zh_tones + num_ja_tones

// 语言到 lang_id 的映射
private const val ZH_LANG_ID = 0
private const val EN_LANG_ID = 2

class ZHENMixImpl(
    val zhPreprocess: BV2Preprocess,
    val cmudictPath: String,
    val cacheDir: String? = null
): IBertVITS2ProcessInternal {

    @Volatile
    private var enLexicon: CmuLexiconTxt? = null

    @Volatile
    private var enG2P: EnglishG2P? = null

    /**
     * 字典是否已就绪，供上层判断是否走 mix 逻辑。
     */
    fun isReady(): Boolean = enG2P != null

    init {
        if (CmuLexiconTxt.hasBinaryCache(cacheDir)) {
            // 二进制缓存存在，同步加载（通常 1-2s 内完成）
            Log.i(TAG, "Binary cache found, loading synchronously...")
            val lexicon = CmuLexiconTxt.fromLocalFile(cmudictPath, cacheDir)
            enLexicon = lexicon
            enG2P = EnglishG2P(
                symbols = normal_symbols.toSet(),
                lexicon = lexicon
            )
        } else {
            // 无缓存，异步触发文本解析 + 缓存生成，不阻塞当前线程
            Log.i(TAG, "No binary cache, triggering async generation...")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val lexicon = CmuLexiconTxt.fromLocalFile(cmudictPath, cacheDir)
                    enLexicon = lexicon
                    enG2P = EnglishG2P(
                        symbols = normal_symbols.toSet(),
                        lexicon = lexicon
                    )
                    Log.i(TAG, "Async CMU dict loading complete, mix mode ready")
                } catch (e: Exception) {
                    Log.e(TAG, "Async CMU dict loading failed", e)
                }
            }
        }
    }

    override suspend fun preprocess(text: String): PreprocessResult? {
        // 兼容旧的 <ZH>/<EN> 标签格式
        val segments = segmentByJieba(text)


        if (segments.isEmpty()) {
            return PreprocessResult(
                errorMsg = "无法解析混合语言文本: $text"
            )
        }

        return processMixSegments(segments)
    }

    /**
     * 利用 jieba 分词 + 词性标注自动切分中英文段落。
     * jieba tag 中 "eng" 表示英文词。
     * 相邻同语言的词合并为一个 segment。
     * 标点符号归入前一个 segment（如果没有前一个则归入下一个）。
     */
    private fun segmentByJieba(text: String): List<Pair<String, String>> {
        val normalized = zhPreprocess.normalizeText(text)
        val wordTags: List<WordPos> = zhPreprocess.segmentWords(normalized)
        if (wordTags.isEmpty()) return emptyList()

        Log.i(TAG, "jieba tags: ${wordTags.joinToString { "${it.word}/${it.pos}" }}")

        // 将 jieba 结果按中/英归类，标点跟随前一个段
        data class LangWord(val word: String, val lang: String) // lang = "ZH" | "EN" | "PUNCT"

        val classified = wordTags.map { wp ->
            val w = wp.word.trim()
            if (w.isEmpty()) return@map null
            val lang = when {
                wp.pos == "eng" -> "EN"
                w.all { isPunctOrSpace(it) } -> "PUNCT"
                else -> "ZH"
            }
            LangWord(w, lang)
        }.filterNotNull()

        // 合并连续同语言的词，标点附加到前一个 segment
        val segments = mutableListOf<Pair<String, String>>() // (lang, text)
        val currentText = StringBuilder()
        var currentLang = ""

        for (lw in classified) {
            if (lw.lang == "PUNCT") {
                // 标点附加到当前 segment
                currentText.append(lw.word)
                continue
            }
            if (lw.lang == currentLang) {
                currentText.append(lw.word)
            } else {
                // 语言切换，保存前一段
                if (currentText.isNotEmpty() && currentLang.isNotEmpty()) {
                    segments.add(currentLang to currentText.toString())
                }
                currentText.clear()
                currentLang = lw.lang
                currentText.append(lw.word)
            }
        }
        // 保存最后一段
        if (currentText.isNotEmpty() && currentLang.isNotEmpty()) {
            segments.add(currentLang to currentText.toString())
        }

        Log.i(TAG, "auto segments: ${segments.joinToString { "<${it.first}>${it.second}" }}")
        return segments
    }

    /**
     * 处理已切分好的 [(lang, text), ...] 段落
     */
    private fun processMixSegments(segments: List<Pair<String, String>>): PreprocessResult? {
        val allPhones = mutableListOf<Int>()
        val allTones = mutableListOf<Int>()
        val allWord2ph = mutableListOf<Int>()
        val allLangIds = mutableListOf<Int>()

        for ((lang, segText) in segments) {
            when (lang) {
                "ZH" -> {
                    val zhResult = processZhSegment(segText)
                    if (zhResult == null) {
                        return PreprocessResult(errorMsg = "ZH segment processing failed: $segText")
                    }
                    allPhones.addAll(zhResult.phones)
                    allTones.addAll(zhResult.tones.map { it + ZH_TONE_START })
                    allWord2ph.addAll(zhResult.word2ph)
                    allLangIds.addAll(List(zhResult.phones.size) { ZH_LANG_ID })
                }
                "EN" -> {
                    val enResult = processEnSegment(segText)
                    if (enResult == null) {
                        return PreprocessResult(errorMsg = "EN segment processing failed: $segText")
                    }
                    allPhones.addAll(enResult.phones)
                    allTones.addAll(enResult.tones.map { it + EN_TONE_START })
                    allWord2ph.addAll(enResult.word2ph)
                    allLangIds.addAll(List(enResult.phones.size) { EN_LANG_ID })
                }
                else -> {
                    return PreprocessResult(errorMsg = "Unsupported language in mix: $lang")
                }
            }
        }

        if (allPhones.size != allTones.size) {
            return PreprocessResult(
                errorMsg = "phones/tones size mismatch: ${allPhones.size} vs ${allTones.size}"
            )
        }

        // 添加 blank（intersperse）
        val phones = intersperse(allPhones, 0)
        val tones = intersperse(allTones, 0)
        val langIds = intersperse(allLangIds, 0)
        val word2ph = allWord2ph.map { it * 2 }.toMutableList()
        word2ph[0] += 1

        val totalPhones = phones.size
        val word2phSum = word2ph.sum()
        if (totalPhones != word2phSum) {
            return PreprocessResult(
                errorMsg = "phones count ($totalPhones) != sum(word2ph) ($word2phSum)"
            )
        }

        // Mix 模式 BERT 特征全走零向量
        val bertSize = word2ph.size
        val bertIds = List(bertSize) { 0 }
        val attentionMask = List(bertSize) { 1 }

        Log.i(TAG, "mix result: phones=$phones\n tones=$tones\n langIds=$langIds\n word2ph=$word2ph\n bertSize=$bertSize")

        return PreprocessResult(
            input_seq = phones,
            input_t = tones,
            input_language = langIds,
            input_ids = bertIds,
            input_word2ph = word2ph,
            attention_mask = attentionMask,
        )
    }

    private fun processZhSegment(segText: String): SegmentResult? {
        return try {
            val normalized = zhPreprocess.normalizeText(segText)
            val g2pResult = zhPreprocess.preprocessWithNormalizedText(normalized)
            val phones = g2pResult.phones.map { zhSymbolsMap[it] ?: UNK_ID }
            SegmentResult(phones = phones, tones = g2pResult.tones, word2ph = g2pResult.word2ph)
        } catch (e: Exception) {
            Log.e(TAG, "ZH segment error: $segText", e)
            null
        }
    }

    private fun processEnSegment(segText: String): SegmentResult? {
        val g2p = enG2P ?: return null
        return try {
            val normalizedText = g2p.textNormalize(segText)
            val g2pResult = g2p.g2p(normalizedText)
            val phones = g2pResult.phones.map { zhSymbolsMap[it] ?: UNK_ID }
            SegmentResult(phones = phones, tones = g2pResult.tones, word2ph = g2pResult.word2ph)
        } catch (e: Exception) {
            Log.e(TAG, "EN segment error: $segText", e)
            null
        }
    }

    private data class SegmentResult(
        val phones: List<Int>,
        val tones: List<Int>,
        val word2ph: List<Int>
    )

    companion object {
        private const val TAG = "ZHENMixImpl"
    }
}

/** 判断字符是否是标点或空白 */
private fun isPunctOrSpace(ch: Char): Boolean {
    if (ch.isWhitespace()) return true
    val type = Character.getType(ch).toByte()
    return type == Character.DASH_PUNCTUATION
            || type == Character.START_PUNCTUATION
            || type == Character.END_PUNCTUATION
            || type == Character.CONNECTOR_PUNCTUATION
            || type == Character.OTHER_PUNCTUATION
            || type == Character.INITIAL_QUOTE_PUNCTUATION
            || type == Character.FINAL_QUOTE_PUNCTUATION
            || type == Character.MATH_SYMBOL
            || ch in "，。！？；：、…—～·「」\"''《》【】（）"
}