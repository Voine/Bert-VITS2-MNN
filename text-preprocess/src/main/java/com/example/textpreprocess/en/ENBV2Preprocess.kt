package com.example.textpreprocess.en

/**
 * Author: Voine
 * Date: 2025/12/8
 * Description:
 */
import android.util.Log
import java.io.File
import java.util.Locale
import kotlin.collections.iterator
import kotlin.text.iterator
import  com.example.textpreprocess.zh.normalizepunctuationMap

/* ============================== Public API =============================== */

data class G2PResult(
    val phones: List<String>, // 包含首尾 "_"，例如 ["_", "hh","ah","l","ow",".","w","er","l","d","_"]
    val tones: List<Int>,    // 与 phones 对齐，标点=0；其他为 1/2/3（对应 ARPAbet 0/1/2 + 1）
    val word2ph: List<Int>    // 与 BERT 分词对齐；sum(word2ph) == phones.size
)

/**
 * 入口：文本 -> (phones, tones, word2ph)
 * - tokenizer：可传入你现有的 SentencePiece/DeBERTa 分词器；不传则用 fallback（空白+标点）。
 * - symbols：你项目训练期的符号集（确保与训练端一致）；未知音素会被映射到 "UNK"。
 */
class EnglishG2P(
    private val symbols: Set<String>,
    private val punctuation: List<String> =  com.example.textpreprocess.zh.punctuation,
    private val lexicon: CmuLexicon
) {

    /**
     * 文本归一化：数字展开 + 标点替换 + 标点后加空格
     * 对应 Python 的 text_normalize()
     */
    fun textNormalize(text: String): String {
        var t = normalizeNumbers(text)
        t = replacePunctuation(t)
        t = t.replace(Regex("([,;.?!])([\\w])"), "$1 $2")
        return t
    }

    /**
     * 入口：文本 -> (phones, tones, word2ph)
     *
     * @param text 已经经过 [textNormalize] 的文本（与 BERT 输入保持一致）。
     * @param tokens 可选，BERT/SentencePiece tokenizer 对 [text] 切分得到的 wordpiece 列表
     *               （例如 CppTokenizerJNI.tokenizeText 的结果）。
     *               传入时 word2ph 会按 wordpiece 个数分发，从而满足
     *               `len(word2ph) == len(tokens) + 2`，与
     *               `CLS + encode(text) + SEP` 长度对齐。
     *               不传则退化为基于空白+标点的 fallback 分组（仅在不需要 BERT 对齐时使用，
     *               例如 mix 模式下 BERT 走零向量）。
     */
    fun g2p(
        text: String,
        tokens: List<String>? = null,
    ): G2PResult {
        val normalized = replacePunctuation(text)
        val wordsGrouped = if (tokens != null) {
            textToWordsByTokens(tokens)
        } else {
            textToWordsFallback(normalized)
        }

        val phones = mutableListOf<String>()
        val tones = mutableListOf<Int>()
        val phoneLenPerGroup = mutableListOf<Int>()

        for (group in wordsGrouped) {
            var word = group.toMutableList()
            val tempPhones = mutableListOf<String>()
            val tempTones = mutableListOf<Int>()

            // 若组内有 "'" 则合并（模仿 Python 逻辑：don't -> "don't"）
            if (word.size > 1 && word.contains("'")) {
                word = mutableListOf(word.joinToString(""))
            }

            // 【重要】先检查拼接后的完整单词是否需要特殊处理
            // 因为 tokenizer 可能会把 VITS 分成 ['V', 'ITS']，但我们需要把它当作完整单词处理
            val fullWord = word.joinToString("")

            if (isHybridWord(fullWord)) {
                // 混合词：如 macOS, iPhone
                val (phns, tns) = g2pHybridWord(fullWord)
                tempPhones += phns
                tempTones += tns
            } else if (isInWordWhitelist(fullWord)) {
                // 白名单词：如 VITS, BERT - 按正常单词读
                val (phns, tns) = g2pSingleWord(fullWord)
                tempPhones += phns
                tempTones += tns
            } else if (shouldSpellOut(fullWord)) {
                // 需要按字母读的词：如 GPU, 或其他全大写词
                val (phns, tns) = spellOutWord(fullWord)
                tempPhones += phns.map(::postReplacePh)
                tempTones += tns
            } else {
                // 正常处理每个 token
                for (w in word) {
                    if (w in punctuation) {
                        tempPhones += w
                        tempTones += 0
                        continue
                    }
                    // 最优先检查是否是混合拼读词（如 macOS, iPhone）
                    if (isHybridWord(w)) {
                        val (phns, tns) = g2pHybridWord(w)
                        tempPhones += phns
                        tempTones += tns
                    }
                    // 使用综合判断：白名单优先，全大写词默认按字母读
                    else if (shouldSpellOut(w)) {
                        val (phns, tns) = spellOutWord(w)
                        tempPhones += phns.map(::postReplacePh)
                        tempTones += tns
                    }
                    // 词典查询 + 规则兜底
                    else {
                        val pron = lexiconFirst(w)
                        val (pp, tt) = refinePron(pron)
                        tempPhones += pp.map(::postReplacePh)
                        tempTones += tt
                    }
                }
            }
            phones += tempPhones
            tones += tempTones
            phoneLenPerGroup += tempPhones.size
        }

        // word2ph：把每个"词组"的 phones 均分给该组内的 wordpiece 数
        val word2ph = mutableListOf<Int>()
        for ((idx, group) in wordsGrouped.withIndex()) {
            val pl = phoneLenPerGroup[idx]
            val wl = group.size
            word2ph += distributePhone(pl, wl)
        }

        // 首尾占位，与 Python 对齐
        val finalPhones = listOf("_") + phones + listOf("_")
        val finalTones = listOf(0) + tones + listOf(0)
        val finalW2Ph = listOf(1) + word2ph + listOf(1)

        // 对齐检查（遇到问题时抛出异常，便于测试）
        check(finalPhones.size == finalTones.size) { "phones != tones" }
        check(finalPhones.size == finalW2Ph.sum()) { "phones != sum(word2ph)" }

        return G2PResult(finalPhones, finalTones, finalW2Ph)
    }

    /* ===================== Acronym / Hybrid / Spell-out ==================== */

    /**
     * 处理单个单词的 G2P 转换（内部辅助函数）
     * 对应 Python g2p_single_word
     */
    private fun g2pSingleWord(w: String): Pair<List<String>, List<Int>> {
        val pron = lexiconFirst(w)
        val (pp, tt) = refinePron(pron)
        return pp.map(::postReplacePh) to tt
    }

    /**
     * 处理混合拼读词的 G2P 转换
     * 对应 Python g2p_hybrid_word
     */
    private fun g2pHybridWord(word: String): Pair<List<String>, List<Int>> {
        val segments = HYBRID_WORDS[word.uppercase(Locale.US)]
            ?: return emptyList<String>() to emptyList()
        val phonemes = mutableListOf<String>()
        val tns = mutableListOf<Int>()
        for ((segment, segType) in segments) {
            if (segType == "spell") {
                val (phns, letterTones) = spellOutWord(segment)
                phonemes += phns.map(::postReplacePh)
                tns += letterTones
            } else { // "word"
                val (phns, wordTones) = g2pSingleWord(segment)
                phonemes += phns
                tns += wordTones
            }
        }
        return phonemes to tns
    }

    /**
     * 将缩写词拆分成字母并返回对应的音素和音调
     * 对应 Python spell_out_word，使用 LETTER_TO_PHONEMES
     */
    private fun spellOutWord(word: String): Pair<List<String>, List<Int>> {
        val phonemes = mutableListOf<String>()
        val tones = mutableListOf<Int>()
        for (letter in word.uppercase(Locale.US)) {
            val key = letter.toString()
            val entry = LETTER_TO_PHONEMES[key] ?: continue
            phonemes += entry.first
            tones += entry.second
        }
        return phonemes to tones
    }

    /** 检查一个词是否在"按单词读"的白名单中 */
    private fun isInWordWhitelist(word: String): Boolean =
        word.uppercase(Locale.US) in READ_AS_WORD_WHITELIST

    /** 检查一个词是否是混合拼读词 */
    private fun isHybridWord(word: String): Boolean =
        word.uppercase(Locale.US) in HYBRID_WORDS

    /**
     * 判断一个词是否应该按字母读（综合判断）
     * 策略：
     * 1. 如果在"按单词读"白名单中 -> 不按字母读
     * 2. 如果在"按字母读"集合中 -> 按字母读
     * 3. 如果全大写（且不在白名单中）且 >= 2 字母 -> 按字母读（兜底）
     * 4. 其他情况 -> 不按字母读
     */
    private fun shouldSpellOut(word: String): Boolean {
        val upper = word.uppercase(Locale.US)
        if (upper in READ_AS_WORD_WHITELIST) return false
        if (upper in SPELL_OUT_ACRONYMS) return true
        val letters = word.filter { it.isLetter() }
        if (letters.length >= 2 && letters.all { it.isUpperCase() }) return true
        return false
    }

    /* ============================ Core Steps ============================= */

    private fun lexiconFirst(raw: String): Pron {
        // 规范化候选：去所有格、去首尾引号、连字符分裂
        val candidates = buildList {
            addAll(normalizeForLookup(raw))
        }

        // 先整体查 + 词形回退
        for (c in candidates) {
            lexicon.lookup(c)?.firstOrNull()?.let { return it }
            for (lemma in tryLemmaForms(c)) {
                lexicon.lookup(lemma)?.firstOrNull()?.let { return it }
            }
        }

        // 连字符切分查（若所有子词都能命中则拼接）
        val hy = raw.replace("’", "'")
        if (hy.contains("-")) {
            val parts = hy.split("-").filter { it.isNotBlank() }
            if (parts.isNotEmpty()) {
                val prons = parts.mapNotNull { part ->
                    val cands = normalizeForLookup(part) + tryLemmaForms(part)
                    var p: Pron? = null
                    for (cc in cands) {
                        p = lexicon.lookup(cc)?.firstOrNull()
                        if (p != null) break
                    }
                    p
                }
                if (prons.size == parts.size) {
                    return Pron(
                        arpabet = prons.flatMap { it.arpabet },
                        stress = prons.flatMap { it.stress }
                    )
                }
            }
        }

        // Acronym（全大写 2..6）
        acronymPron(raw)?.let { return it }

        // 规则兜底 / 或小模型
        return fallbackG2P(raw)
    }

    private fun refinePron(pr: Pron): Pair<List<String>, List<Int>> {
        val outPhones = mutableListOf<String>()
        val outTones = mutableListOf<Int>()
        for (ph in pr.arpabet) {
            val (p, t) = refinePh(ph)
            outPhones += p
            outTones += t
        }
        return outPhones to outTones
    }

    /* ============================ Tokenization =========================== */

    /**
     * 基于 BERT/SentencePiece tokenizer 的 wordpiece 列表把文本切成 word group，
     * 对应 Python `text_to_words(text)`：
     *   - "▁X" 表示一个新词的起点，去掉前缀 "▁" 作为该词的第一个 token
     *   - 标点视上下文决定是粘到前一个词还是单独成组
     *   - 其它 subword 续到上一个词中
     *
     * 这样得到的分组满足 `sum(len(group)) == tokens.size`，使最终 word2ph 的
     * 长度（首尾各加 1 占位）等于 `tokens.size + 2`，与
     * `CLS + encode(text) + SEP` 的长度对齐。
     */
    private fun textToWordsByTokens(tokens: List<String>): List<List<String>> {
        val words = mutableListOf<MutableList<String>>()
        for ((idx, t) in tokens.withIndex()) {
            when {
                t.startsWith("▁") -> {
                    words.add(mutableListOf(t.substring(1)))
                }
                t in punctuation -> {
                    if (idx == tokens.size - 1) {
                        words.add(mutableListOf(t))
                    } else {
                        val next = tokens[idx + 1]
                        if (!next.startsWith("▁") && next !in punctuation) {
                            if (words.isEmpty()) words.add(mutableListOf())
                            words.last().add(t)
                        } else {
                            words.add(mutableListOf(t))
                        }
                    }
                }
                else -> {
                    if (words.isEmpty()) words.add(mutableListOf())
                    words.last().add(t)
                }
            }
        }
        return words
    }

    /**
     * Fallback：纯空白+标点切分（仅在没有 BERT 对齐需求时使用）
     */
    private fun textToWordsFallback(text: String): List<List<String>> {
        val toks = REGEX_SPLIT.split(text)
            .filter { it.isNotBlank() }
            .flatMap { token ->
                if (token.length == 1 && token in punctuation) listOf(token)
                else splitByPunct(token, punctuation)
            }
        // 简单策略：遇到标点就收一个组；其余按空白词组
        val groups = mutableListOf<MutableList<String>>()
        var cur = mutableListOf<String>()
        for (tk in toks) {
            if (tk in punctuation) {
                if (cur.isNotEmpty()) groups += cur
                groups += mutableListOf(tk)
                cur = mutableListOf()
            } else {
                cur.add(tk)
            }
        }
        if (cur.isNotEmpty()) groups += cur
        return groups
    }

    /* =============================== Utils =============================== */

    private fun distributePhone(nPhone: Int, nWord: Int): List<Int> {
        if (nWord <= 0) return emptyList()
        val arr = IntArray(nWord)
        repeat(nPhone) {
            var minIdx = 0
            var minVal = arr[0]
            for (i in 1 until nWord) {
                if (arr[i] < minVal) {
                    minVal = arr[i]; minIdx = i
                }
            }
            arr[minIdx]++
        }
        return arr.toList()
    }

    private fun replacePunctuation(s: String): String {
        var t = s
        for ((k, v) in normalizepunctuationMap) {
            t = t.replace(k, v)
        }
        // 在句读后补空格：与 Python 对齐
        return t.replace(Regex("([,;.?!])([\\w])"), "$1 $2")
    }

    private fun postReplacePh(ph: String): String {
        // "v" 在 en_symbols 中是大写 "V"
        val mapped = if (ph == "v") "V" else ph
        return if (mapped in symbols) mapped else "UNK"
    }

    private fun refinePh(phRaw: String): Pair<String, Int> {
        // ARPAbet 末尾可能有 0/1/2 重音数字
        val m = REGEX_STRESS.find(phRaw)
        val tone = if (m != null) m.groupValues[1].toInt() + 1 else 3 // 0/1/2 -> 1/2/3
        val base = phRaw.trim().trimEnd('0', '1', '2').lowercase(Locale.US)
        return base to tone
    }

    private fun normalizeForLookup(raw: String): List<String> {
        val w = raw.trim()
        val base = w.trim('\'', '’', '"', '“', '”')
            .lowercase(Locale.US)
            .removeSuffix("'s")
            .removeSuffix("’s")
            .removeSuffix("'")
            .removeSuffix("’")
        return buildList {
            add(base)
            if (base.contains('-')) addAll(base.split('-').filter { it.isNotBlank() })
        }
    }

    private fun tryLemmaForms(w: String): List<String> {
        val out = mutableSetOf<String>()
        val s = w.lowercase(Locale.US)
        // 复数
        if (s.endsWith("ies") && s.length > 3) out += s.dropLast(3) + "y"
        if (s.endsWith("es") && s.length > 2) out += s.dropLast(2)
        if (s.endsWith("s") && s.length > 1) out += s.dropLast(1)
        // 过去式/分词/进行时
        if (s.endsWith("ied")) out += s.dropLast(3) + "y"
        if (s.endsWith("ed")) out += s.dropLast(2)
        if (s.endsWith("ing")) {
            out += s.dropLast(3)
            if (s.endsWith("ying")) out += s.dropLast(4) + "ie"
        }
        // 比较级/最高级
        if (s.endsWith("ier")) out += s.dropLast(3) + "y"
        if (s.endsWith("iest")) out += s.dropLast(4) + "y"
        if (s.endsWith("er")) out += s.dropLast(2)
        if (s.endsWith("est")) out += s.dropLast(3)
        return out.toList()
    }

    /* ============================ Fallback G2P ============================ */

    data class Pron(val arpabet: List<String>, val stress: List<Int>) // stress 暂不使用，可全 3

    private fun fallbackG2P(wRaw: String): Pron {
        // 先试 acronym
        acronymPron(wRaw)?.let { return it }
        // 规则法
        return ruleG2P(wRaw)
    }

    private fun acronymPron(wRaw: String): Pron? {
        val w = wRaw.trim()
        if (w.length in 2..6 && w.all { it.isUpperCase() }) {
            // 特例优先（根据你业务需要扩充）
            ACRONYM_EXCEPTIONS[w]?.let { return it }
            val arp = mutableListOf<String>()
            for (c in w) {
                val m = ACRONYM_LETTERS[c] ?: return null
                arp += m
            }
            return Pron(arpabet = arp, stress = List(arp.size) { 3 })
        }
        return null
    }

    // 极简规则兜底（示意：可逐步补全）
    private fun ruleG2P(wRaw: String): Pron {
        val s = wRaw.lowercase(Locale.US)
        val arp = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val rest = s.substring(i)
            when {
                rest.startsWith("tion") -> {
                    arp += listOf("SH", "AH", "N"); i += 4
                }

                rest.startsWith("sion") -> {
                    arp += listOf("ZH", "AH", "N"); i += 4
                }

                rest.startsWith("tial") -> {
                    arp += listOf("SH", "AH", "L"); i += 4
                }

                rest.startsWith("cial") -> {
                    arp += listOf("SH", "AH", "L"); i += 4
                }

                rest.startsWith("ph") -> {
                    arp += "F"; i += 2
                }

                rest.startsWith("qu") -> {
                    arp += listOf("K", "W"); i += 2
                }

                rest.startsWith("ch") -> {
                    arp += "CH"; i += 2
                }

                rest.startsWith("sh") -> {
                    arp += "SH"; i += 2
                }

                rest.startsWith("th") -> {
                    arp += "TH"; i += 2
                } // 不分清浊，简化
                rest.startsWith("ck") -> {
                    arp += "K"; i += 2
                }

                else -> {
                    // 简化元音/辅音映射（可按开闭音节优化）
                    when (val c = s[i]) {
                        'a' -> arp += "AE"
                        'e' -> arp += "EH"
                        'i' -> arp += "IH"
                        'o' -> arp += "AA"
                        'u' -> arp += "AH"
                        'y' -> arp += "IY"
                        'b' -> arp += "B"
                        'c' -> arp += "K"
                        'd' -> arp += "D"
                        'f' -> arp += "F"
                        'g' -> arp += "G"
                        'h' -> arp += "HH"
                        'j' -> arp += "JH"
                        'k' -> arp += "K"
                        'l' -> arp += "L"
                        'm' -> arp += "M"
                        'n' -> arp += "N"
                        'p' -> arp += "P"
                        'q' -> arp += "K"
                        'r' -> arp += "R"
                        's' -> arp += "S"
                        't' -> arp += "T"
                        'v' -> arp += "V"
                        'w' -> arp += "W"
                        'x' -> arp += listOf("K", "S")
                        'z' -> arp += "Z"
                        else -> { /* skip unknown */
                        }
                    }
                    i++
                }
            }
        }
        return Pron(arpabet = arp, stress = List(arp.size) { 3 })
    }
}

/* ============================== Lexicon =============================== */

interface CmuLexicon {
    fun lookup(wordLower: String): List<EnglishG2P.Pron>?
}

/**
 * CMU 发音词典的高性能加载实现。
 *
 * 支持两类文本源：
 * 1) 标准 cmudict：  WORD  W ER D
 * 2) cmudict.rep：   WORD  W ER D - AH N （音节用 " - " 分隔）
 *
 * 优化策略——**二进制缓存**：
 *   首次从文本解析后，将结果序列化为紧凑的二进制文件（.bin）；
 *   后续启动直接读取二进制缓存，跳过逐行文本解析，速度提升 5-10 倍。
 *   当源文本文件发生变化（lastModified / length 变动）时，自动重建缓存。
 */
class CmuLexiconTxt(
    private val map: Map<String, List<EnglishG2P.Pron>>
) : CmuLexicon {
    override fun lookup(wordLower: String): List<EnglishG2P.Pron>? {
        return map[wordLower.lowercase(Locale.US)]
    }

    companion object {
        private const val TAG = "CmuLexiconTxt"

        /** 二进制缓存魔数，用于校验文件合法性 */
        private const val CACHE_MAGIC = 0x434D5542 // "CMUB"

        /** 缓存格式版本号，格式变更时递增即可 */
        private const val CACHE_VERSION = 1

        /** 预编译的空白分割正则，避免每行重复编译 */
        private val WHITESPACE_RE = Regex("\\s+")

        /* ======================== Public entry ========================= */

        /**
         * 检查二进制缓存文件是否存在（轻量级，仅检查文件存在性，不做 IO 读取）。
         * 用于上层判断能否走同步快速加载路径。
         */
        fun hasBinaryCache(cacheDir: String?): Boolean {
            if (cacheDir == null) return false
            return File(cacheDir, "cmudict_cache_v${CACHE_VERSION}.bin").exists()
        }

        /**
         * 加载 CMU 词典，优先使用二进制缓存。
         * @param filePath 文本词典路径（cmudict.txt / cmudict.rep）
         * @param cacheDir 缓存目录；为 null 时不启用缓存，退化为纯文本解析。
         *                 建议传 context.filesDir 或 context.cacheDir。
         */
        fun fromLocalFile(filePath: String, cacheDir: String? = null): CmuLexiconTxt {
            val srcFile = File(filePath)
            val cacheFile = cacheDir?.let {
                File(it, "cmudict_cache_v${CACHE_VERSION}.bin")
            }

            // 1. 尝试从二进制缓存加载
            if (cacheFile != null && cacheFile.exists()) {
                try {
                    val startMs = System.currentTimeMillis()
                    val result = loadFromBinaryCache(cacheFile, srcFile)
                    if (result != null) {
                        val elapsed = System.currentTimeMillis() - startMs
                        Log.i(TAG, "Loaded ${result.map.size} entries from binary cache in ${elapsed}ms")
                        return result
                    }
                    // result == null 表示缓存过期或校验失败，继续走文本解析
                    Log.i(TAG, "Binary cache invalid/stale, rebuilding...")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load binary cache, falling back to text parse", e)
                }
            }
            // 2. 文本解析
            val startMs = System.currentTimeMillis()
            val map = parseTextFile(srcFile)
            val elapsed = System.currentTimeMillis() - startMs
            Log.i(TAG, "Parsed ${map.size} entries from text file in ${elapsed}ms")

            // 3. 写入二进制缓存（异步，不阻塞返回）
            if (cacheFile != null) {
                try {
                    val writeStart = System.currentTimeMillis()
                    writeBinaryCache(cacheFile, map, srcFile)
                    val writeElapsed = System.currentTimeMillis() - writeStart
                    Log.i(TAG, "Binary cache written in ${writeElapsed}ms")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write binary cache", e)
                }
            }

            return CmuLexiconTxt(map)
        }

        /* ==================== Text parsing (optimized) =================== */

        private fun parseTextFile(srcFile: File): HashMap<String, MutableList<EnglishG2P.Pron>> {
            val map = HashMap<String, MutableList<EnglishG2P.Pron>>(131072)
            srcFile.inputStream().bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val ln = line.trim()
                    if (ln.isEmpty()) continue
                    // 注释行以 "##" 开头（双#），注意 #SHARP-SIGN / ;SEMI-COLON 等是合法条目
                    if (ln.startsWith("##")) continue

                    val idx = ln.indexOf("  ")
                    if (idx <= 0) continue
                    var word = ln.substring(0, idx).trim()
                    val phonesArea = ln.substring(idx + 2).trim()

                    // 去掉括号序号 WORD(1)
                    val parenIdx = word.indexOf('(')
                    if (parenIdx > 0 && word.endsWith(")")) {
                        word = word.substring(0, parenIdx)
                    }
                    val lower = word.lowercase(Locale.US)

                    val pronList: List<String> = if (phonesArea.contains(" - ")) {
                        phonesArea.split(" - ")
                            .flatMap { seg -> seg.trim().split(WHITESPACE_RE) }
                    } else {
                        phonesArea.split(WHITESPACE_RE)
                    }
                    if (pronList.isEmpty()) continue

                    val pron = EnglishG2P.Pron(
                        arpabet = pronList,
                        stress = List(pronList.size) { 3 }
                    )
                    map.getOrPut(lower) { mutableListOf() }.add(pron)
                }
            }
            return map
        }

        /* =============== Binary cache: write (DataOutputStream) ============ */

        /**
         * 二进制格式（小端 DataOutputStream 默认大端，此处用 Big-Endian）：
         *
         * [Header]
         *   magic:       Int (4B)       — CACHE_MAGIC
         *   version:     Int (4B)       — CACHE_VERSION
         *   srcModified: Long (8B)      — 源文件 lastModified
         *   srcLength:   Long (8B)      — 源文件 length
         *
         * [String Pool]  — 所有不重复的音素字符串，集中存储
         *   poolSize:    Int (4B)
         *   for each:    UTF (2B len + bytes)
         *
         * [Entries]
         *   entryCount:  Int (4B)
         *   for each entry:
         *     word:      UTF
         *     pronCount: Short (2B)
         *     for each pron:
         *       phonemeCount: Short (2B)
         *       for each phoneme:
         *         poolIndex: Short (2B)  — 在 string pool 中的索引
         */
        private fun writeBinaryCache(
            cacheFile: File,
            map: Map<String, List<EnglishG2P.Pron>>,
            srcFile: File
        ) {
            // 1. 构建 string pool
            val poolSet = LinkedHashSet<String>(256)
            for ((_, prons) in map) {
                for (pron in prons) {
                    poolSet.addAll(pron.arpabet)
                }
            }
            val poolList = poolSet.toList()
            val poolIndex = HashMap<String, Int>(poolList.size * 2)
            poolList.forEachIndexed { i, s -> poolIndex[s] = i }

            // 2. 写文件
            cacheFile.parentFile?.mkdirs()
            java.io.DataOutputStream(cacheFile.outputStream().buffered(1 shl 16)).use { dos ->
                // header
                dos.writeInt(CACHE_MAGIC)
                dos.writeInt(CACHE_VERSION)
                dos.writeLong(srcFile.lastModified())
                dos.writeLong(srcFile.length())

                // string pool
                dos.writeInt(poolList.size)
                for (s in poolList) {
                    dos.writeUTF(s)
                }

                // entries
                dos.writeInt(map.size)
                for ((word, prons) in map) {
                    dos.writeUTF(word)
                    dos.writeShort(prons.size)
                    for (pron in prons) {
                        dos.writeShort(pron.arpabet.size)
                        for (ph in pron.arpabet) {
                            dos.writeShort(poolIndex[ph]!!)
                        }
                    }
                }
            }
        }

        /* =============== Binary cache: read (DataInputStream) ============ */

        /**
         * 从二进制缓存恢复 map。
         * 如果 magic/version 不匹配或源文件指纹变化，返回 null。
         */
        private fun loadFromBinaryCache(
            cacheFile: File,
            srcFile: File
        ): CmuLexiconTxt? {
            java.io.DataInputStream(cacheFile.inputStream().buffered(1 shl 16)).use { dis ->
                // header check
                if (dis.readInt() != CACHE_MAGIC) return null
                if (dis.readInt() != CACHE_VERSION) return null
                val cachedModified = dis.readLong()
                val cachedLength = dis.readLong()
                if (srcFile.exists() && (cachedModified != srcFile.lastModified() || cachedLength != srcFile.length())) {
                    return null // 源文件已变更，缓存失效
                }

                // string pool
                val poolSize = dis.readInt()
                val pool = Array(poolSize) { dis.readUTF() }

                // entries
                val entryCount = dis.readInt()
                val map = HashMap<String, MutableList<EnglishG2P.Pron>>(entryCount * 2)
                repeat(entryCount) {
                    val word = dis.readUTF()
                    val pronCount = dis.readShort().toInt()
                    val prons = ArrayList<EnglishG2P.Pron>(pronCount)
                    repeat(pronCount) {
                        val phCount = dis.readShort().toInt()
                        val arpabet = ArrayList<String>(phCount)
                        repeat(phCount) {
                            arpabet.add(pool[dis.readShort().toInt()])
                        }
                        prons.add(EnglishG2P.Pron(
                            arpabet = arpabet,
                            stress = List(phCount) { 3 }
                        ))
                    }
                    map[word] = prons
                }
                return CmuLexiconTxt(map)
            }
        }
    }
}

/* ============================= Constants ============================== */

private val REGEX_SPLIT = Regex("\\s+")
private val REGEX_STRESS = Regex(".*?(\\d)$")

/* ========================= Number Normalization ========================= */

private val COMMA_NUMBER_RE = Regex("([0-9][0-9,]+[0-9])")
private val DECIMAL_NUMBER_RE = Regex("([0-9]+\\.[0-9]+)")
private val POUNDS_RE = Regex("£([0-9,]*[0-9]+)")
private val DOLLARS_RE = Regex("\\$([0-9.,]*[0-9]+)")
private val ORDINAL_RE = Regex("[0-9]+(st|nd|rd|th)")
private val NUMBER_RE = Regex("[0-9]+")

private val ONES = arrayOf(
    "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
    "seventeen", "eighteen", "nineteen"
)
private val TENS = arrayOf(
    "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
)

private fun numberToWords(num: Int): String {
    if (num == 0) return "zero"
    if (num < 0) return "minus ${numberToWords(-num)}"
    val parts = mutableListOf<String>()
    if (num >= 1_000_000_000) {
        parts += "${numberToWords(num / 1_000_000_000)} billion"
        val rem = num % 1_000_000_000
        if (rem > 0) parts += numberToWords(rem)
    } else if (num >= 1_000_000) {
        parts += "${numberToWords(num / 1_000_000)} million"
        val rem = num % 1_000_000
        if (rem > 0) parts += numberToWords(rem)
    } else if (num >= 1000) {
        parts += "${numberToWords(num / 1000)} thousand"
        val rem = num % 1000
        if (rem > 0) parts += numberToWords(rem)
    } else if (num >= 100) {
        parts += "${ONES[num / 100]} hundred"
        val rem = num % 100
        if (rem > 0) parts += numberToWords(rem)
    } else if (num >= 20) {
        val t = TENS[num / 10]
        val o = num % 10
        parts += if (o > 0) "$t ${ONES[o]}" else t
    } else {
        parts += ONES[num]
    }
    return parts.joinToString(" ")
}

private fun expandNumber(num: Int): String {
    return if (num in 1001..2999) {
        when {
            num == 2000 -> "two thousand"
            num in 2001..2009 -> "two thousand ${numberToWords(num % 100)}"
            num % 100 == 0 -> "${numberToWords(num / 100)} hundred"
            else -> {
                val high = numberToWords(num / 100)
                val low = num % 100
                val lowStr = if (low < 10) "oh ${numberToWords(low)}" else numberToWords(low)
                "$high $lowStr"
            }
        }
    } else {
        numberToWords(num)
    }
}

private fun expandOrdinal(num: Int): String {
    return numberToWords(num) // simplified: just reads as cardinal
}

private fun expandDollars(match: MatchResult): String {
    val value = match.groupValues[1]
    val parts = value.split(".")
    if (parts.size > 2) return "$value dollars"
    val dollars = parts[0].replace(",", "").toIntOrNull() ?: 0
    val cents = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toIntOrNull() ?: 0 else 0
    return when {
        dollars > 0 && cents > 0 -> {
            val du = if (dollars == 1) "dollar" else "dollars"
            val cu = if (cents == 1) "cent" else "cents"
            "${numberToWords(dollars)} $du, ${numberToWords(cents)} $cu"
        }
        dollars > 0 -> {
            val du = if (dollars == 1) "dollar" else "dollars"
            "${numberToWords(dollars)} $du"
        }
        cents > 0 -> {
            val cu = if (cents == 1) "cent" else "cents"
            "${numberToWords(cents)} $cu"
        }
        else -> "zero dollars"
    }
}

private fun normalizeNumbers(text: String): String {
    var t = COMMA_NUMBER_RE.replace(text) { it.groupValues[1].replace(",", "") }
    t = POUNDS_RE.replace(t) { "${it.groupValues[1]} pounds" }
    t = DOLLARS_RE.replace(t) { expandDollars(it) }
    t = DECIMAL_NUMBER_RE.replace(t) { it.groupValues[1].replace(".", " point ") }
    t = ORDINAL_RE.replace(t) { expandOrdinal(it.value.dropLast(2).toIntOrNull() ?: 0) }
    t = NUMBER_RE.replace(t) { expandNumber(it.value.toIntOrNull() ?: 0) }
    return t
}

// 字母名（A->EY, B->B IY ...）
private val ACRONYM_LETTERS: Map<Char, List<String>> = mapOf(
    'A' to listOf("EY"),
    'B' to listOf("B", "IY"),
    'C' to listOf("S", "IY"),
    'D' to listOf("D", "IY"),
    'E' to listOf("IY"),
    'F' to listOf("EH", "F"),
    'G' to listOf("JH", "IY"),
    'H' to listOf("EY", "CH"),
    'I' to listOf("AY"),
    'J' to listOf("JH", "EY"),
    'K' to listOf("K", "EY"),
    'L' to listOf("EH", "L"),
    'M' to listOf("EH", "M"),
    'N' to listOf("EH", "N"),
    'O' to listOf("OW"),
    'P' to listOf("P", "IY"),
    'Q' to listOf("K", "Y", "UW"),
    'R' to listOf("AA", "R"),
    'S' to listOf("EH", "S"),
    'T' to listOf("T", "IY"),
    'U' to listOf("Y", "UW"),
    'V' to listOf("V", "IY"),
    'W' to listOf("D", "AH", "B", "Y", "AH", "L", "Y", "UW"),
    'X' to listOf("EH", "K", "S"),
    'Y' to listOf("W", "AY"),
    'Z' to listOf("Z", "IY")
)

// 业务常见特例（可持续扩充）
private val ACRONYM_EXCEPTIONS: Map<String, EnglishG2P.Pron> = mapOf(
    "RTX" to EnglishG2P.Pron(listOf("AA", "R", "T", "IY", "EH", "K", "S"), List(7) { 3 }),
    "DLSS" to EnglishG2P.Pron(listOf("D", "IY", "EH", "L", "EH", "S", "EH", "S"), List(8) { 3 }),
    "FSR" to EnglishG2P.Pron(listOf("EH", "F", "EH", "S", "AA", "R"), List(6) { 3 })
)

/* ============================================================================
 * 缩写词处理策略（对应 Python english.py）
 *
 * 全大写词的处理优先级：
 *   1) 先检查 HYBRID_WORDS（混合拼读词），如 macOS -> mac(word) + OS(spell)
 *   2) 再检查 READ_AS_WORD_WHITELIST（按单词读白名单），如 VITS -> 按单词读
 *   3) 再检查 SPELL_OUT_ACRONYMS（明确需要按字母读），如 GPU -> G-P-U
 *   4) 最后，其他全大写词默认按字母读（兜底策略）
 * 混合大小写词：使用正常的 G2P 流程
 * ============================================================================ */

/** 需要按【单词】读的全大写词白名单 */
private val READ_AS_WORD_WHITELIST: Set<String> = setOf(
    // 技术术语（读作单词）
    "VITS", "BERT", "CLIP", "CUDA", "UNIX", "LINUX", "GIT", "VIM",
    "BASH", "JAVA", "RUST", "SWIFT", "PERL", "RUBY", "LISP",
    // 品牌/产品（读作单词）
    "VISA", "ASUS", "ACER", "DELL", "ZOOM", "UBER", "LYFT",
    "TESLA", "NASA",
    // 其他常见按单词读的全大写词
    "ASAP",
    "SCUBA", "RADAR", "LASER", "NATO", "JPEG",
    "GIF",
    "PIN", "SIM", "RAM",
    "VIP",
)

/** 需要按【字母】逐个读的缩写词集合 */
private val SPELL_OUT_ACRONYMS: Set<String> = setOf(
    // 技术/硬件相关
    "GPU", "CPU", "SSD", "HDD", "USB", "HDMI", "VGA", "DVI",
    "RGB", "LED", "LCD", "OLED", "NVME", "PCIE", "DDR", "RTX", "GTX", "FPS",
    // AI/ML 相关
    "AI", "ML", "DL", "NLP", "LLM", "GPT", "CNN", "RNN", "GAN", "VAE",
    "ONNX", "TTS", "ASR", "OCR", "CV",
    // 编程/软件相关
    "API", "SDK", "IDE", "CLI", "GUI", "URL", "HTTP", "HTTPS", "FTP",
    "SQL", "CSS", "HTML", "XML", "JSON", "YAML", "PDF", "PNG", "JPG",
    "MP3", "MP4", "AVI", "MKV", "WAV", "FLAC",
    // 网络/协议相关
    "IP", "TCP", "UDP", "DNS", "VPN", "LAN", "WAN", "NFC",
    "GPS", "GSM", "LTE", "5G", "4G", "3G",
    // 公司/组织缩写
    "IBM", "HP", "AMD", "ARM", "FBI", "CIA", "MIT", "UCLA",
    // 其他常见缩写
    "OK", "ID", "CEO", "CTO", "CFO", "HR", "PR", "QA", "PM",
    "FAQ", "DIY", "ETA", "FYI", "BTW", "OMG", "LOL",
    "TV", "DVD", "CD", "AC", "DC", "FM", "AM", "GG",
    "ROM",
)

/**
 * 混合拼读词：部分按单词读，部分按字母读
 * 格式: "原词(大写)" -> [("片段", "类型"), ...]
 *   类型: "word" = 按正常单词读, "spell" = 按字母拼读
 */
private val HYBRID_WORDS: Map<String, List<Pair<String, String>>> = mapOf(
    // 操作系统相关
    "MACOS" to listOf("mac" to "word", "OS" to "spell"),
    "IOS" to listOf("I" to "spell", "OS" to "spell"),
    "IPADOS" to listOf("I" to "spell", "pad" to "word", "OS" to "spell"),
    "WATCHOS" to listOf("watch" to "word", "OS" to "spell"),
    "TVOS" to listOf("TV" to "spell", "OS" to "spell"),
    "WEBOS" to listOf("web" to "word", "OS" to "spell"),
    "CHROMEOS" to listOf("chrome" to "word", "OS" to "spell"),
    // 编程/技术相关
    "DEVOPS" to listOf("dev" to "word", "ops" to "word"),
    "GITHUB" to listOf("git" to "word", "hub" to "word"),
    "GITLAB" to listOf("git" to "word", "lab" to "word"),
    "PYTORCH" to listOf("py" to "word", "torch" to "word"),
    "TENSORFLOW" to listOf("tensor" to "word", "flow" to "word"),
    "OPENAI" to listOf("open" to "word", "AI" to "spell"),
    "CHATGPT" to listOf("chat" to "word", "GPT" to "spell"),
    "DALL-E" to listOf("dall" to "word", "E" to "spell"),
    "DALLE" to listOf("dall" to "word", "E" to "spell"),
    // 微软相关
    "DIRECTX" to listOf("direct" to "word", "X" to "spell"),
    "ACTIVEX" to listOf("active" to "word", "X" to "spell"),
    "XBOX" to listOf("X" to "spell", "box" to "word"),
    // 苹果相关
    "IPHONE" to listOf("I" to "spell", "phone" to "word"),
    "IPAD" to listOf("I" to "spell", "pad" to "word"),
    "IMAC" to listOf("I" to "spell", "mac" to "word"),
    "IPOD" to listOf("I" to "spell", "pod" to "word"),
    "ICLOUD" to listOf("I" to "spell", "cloud" to "word"),
    "ITUNES" to listOf("I" to "spell", "tunes" to "word"),
    "IWATCH" to listOf("I" to "spell", "watch" to "word"),
    "AIRPODS" to listOf("air" to "word", "pods" to "word"),
    // 其他品牌/产品
    "YOUTUBE" to listOf("you" to "word", "tube" to "word"),
    "PAYPAL" to listOf("pay" to "word", "pal" to "word"),
    "WHATSAPP" to listOf("whats" to "word", "app" to "word"),
    "WECHAT" to listOf("we" to "word", "chat" to "word"),
    "TIKTOK" to listOf("tik" to "word", "tok" to "word"),
    "LINKEDIN" to listOf("linked" to "word", "in" to "word"),
    // 技术术语
    "WIFI" to listOf("WI" to "spell", "FI" to "spell"),
    "HIFI" to listOf("HI" to "spell", "FI" to "spell"),
)

/**
 * 单个字母到音素+音调的映射（基于 CMU 字典中的字母发音）
 * 音素使用小写格式（与 en_symbols 一致）
 * 音调：1=轻声(对应ARPAbet 0+1), 2=重音(1+1), 3=次重音(2+1) 或无重音标记时默认3
 * 对应 Python LETTER_TO_PHONEMES
 */
private val LETTER_TO_PHONEMES: Map<String, Pair<List<String>, List<Int>>> = mapOf(
    "A" to (listOf("ey") to listOf(2)),
    "B" to (listOf("b", "iy") to listOf(3, 2)),
    "C" to (listOf("s", "iy") to listOf(3, 2)),
    "D" to (listOf("d", "iy") to listOf(3, 2)),
    "E" to (listOf("iy") to listOf(2)),
    "F" to (listOf("eh", "f") to listOf(2, 3)),
    "G" to (listOf("jh", "iy") to listOf(3, 2)),
    "H" to (listOf("ey", "ch") to listOf(2, 3)),
    "I" to (listOf("ay") to listOf(2)),
    "J" to (listOf("jh", "ey") to listOf(3, 2)),
    "K" to (listOf("k", "ey") to listOf(3, 2)),
    "L" to (listOf("eh", "l") to listOf(2, 3)),
    "M" to (listOf("eh", "m") to listOf(2, 3)),
    "N" to (listOf("eh", "n") to listOf(2, 3)),
    "O" to (listOf("ow") to listOf(2)),
    "P" to (listOf("p", "iy") to listOf(3, 2)),
    "Q" to (listOf("k", "y", "uw") to listOf(3, 3, 2)),
    "R" to (listOf("aa", "r") to listOf(2, 3)),
    "S" to (listOf("eh", "s") to listOf(2, 3)),
    "T" to (listOf("t", "iy") to listOf(3, 2)),
    "U" to (listOf("y", "uw") to listOf(3, 2)),
    "V" to (listOf("V", "iy") to listOf(3, 2)),   // "V" 在 en_symbols 中是大写的
    "W" to (listOf("d", "ah", "b", "ah", "l", "y", "uw") to listOf(3, 2, 3, 1, 3, 3, 1)),
    "X" to (listOf("eh", "k", "s") to listOf(2, 3, 3)),
    "Y" to (listOf("w", "ay") to listOf(3, 2)),
    "Z" to (listOf("z", "iy") to listOf(3, 2)),
)

/* ============================= Helpers ============================== */

private fun splitByPunct(token: String, punct: List<String>): List<String> {
    // 把 token 中的单字符标点拆出来
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    for (ch in token) {
        val s = ch.toString()
        if (s in punct) {
            if (sb.isNotEmpty()) {
                out += sb.toString(); sb.clear()
            }
            out += s
        } else sb.append(ch)
    }
    if (sb.isNotEmpty()) out += sb.toString()
    return out
}