package com.example.textpreprocess.jp

import android.content.res.AssetManager
import android.util.Log
import com.example.openjtalk.Feature
import com.example.openjtalk.OpenJTalkJNI

/**
 * Author: Voine
 * Date: 2025/5/14
 * Description: jp bv2 preprocess
 */
class JapaneseTextPreprocessor {
    companion object {
        private const val TAG = "JapaneseTextPreprocessor"
    }

    private val openJTalkJNI: OpenJTalkJNI by lazy {
        OpenJTalkJNI()
    }

    // 将 OpenJTalk 前端输出的节点信息封装为数据类
    private data class OpenJTalkNode(val string: String, val pron: String)

    // 地图：特殊符号或全角标点替换为标准符号
    private val repMap: Map<String, String> = mapOf(
        "：" to ",",  "；" to ",",  "，" to ",",  "。" to ".",
        "！" to "!",  "？" to "?",  "\n" to ".", "．" to ".",
        "…" to "...", "···" to "...", "・・・" to "...",
        "·" to ",",  "・" to ",",  "、" to ",",
        "$" to ".",   // 单独的美元符号替换为句点（避免无法识别）
        "“" to "'",   "”" to "'",   "\"" to "'",
        "‘" to "'",   "’" to "'",
        "（" to "'",   "）" to "'",   "(" to "'",   ")" to "'",
        "《" to "'",   "》" to "'",   "〖" to "'",   "〗" to "'",
        "[" to "'",   "]" to "'",
        "—" to "-",   "−" to "-",   "～" to "-",   "~" to "-",
        "「" to "'",   "」" to "'"
    )

    // 允许保留的标点符号集合（标准化后的符号）
    private val punctuation: Set<String> = setOf(",", ".", "!", "?", "'", "-")

    // 用于匹配带千位分隔符的数字串的正则
    private val NUMBER_WITH_SEPARATOR_REGEX = Regex("[0-9]{1,3}(,[0-9]{3})+")
    // 货币符号映射为日语读法
    private val CURRENCY_MAP: Map<String, String> = mapOf(
        "$" to "ドル", "¥" to "円", "£" to "ポンド", "€" to "ユーロ"
    )
    // 用于匹配货币金额（符号 + 数字）的正则
    private val CURRENCY_REGEX = Regex("([$¥£€])([0-9.]*[0-9])")
    // 用于匹配整数或小数的正则
    private val NUMBER_REGEX = Regex("[0-9]+(\\.[0-9]+)?")

    // 特殊字母和符号转写为日语读音的映射表
    private val ALPHASYMBOL_YOMI: Map<String, String> = mapOf(
        "#" to "シャープ",  "%" to "パーセント", "&" to "アンド", "+" to "プラス", "-" to "マイナス",
        ":" to "コロン",   ";" to "セミコロン", "<" to "小なり", "=" to "イコール", ">" to "大なり", "@" to "アット",
        // 英文字母 a-z 转写
        "a" to "エー", "b" to "ビー", "c" to "シー", "d" to "ディー", "e" to "イー", "f" to "エフ",
        "g" to "ジー", "h" to "エイチ", "i" to "アイ", "j" to "ジェー", "k" to "ケー", "l" to "エル",
        "m" to "エム", "n" to "エヌ", "o" to "オー", "p" to "ピー", "q" to "キュー", "r" to "アール",
        "s" to "エス", "t" to "ティー", "u" to "ユー", "v" to "ブイ", "w" to "ダブリュー",
        "x" to "エックス", "y" to "ワイ", "z" to "ゼット",
        // 希腊字母 α-ω 转写
        "α" to "アルファ", "β" to "ベータ", "γ" to "ガンマ", "δ" to "デルタ", "ε" to "イプシロン",
        "ζ" to "ゼータ", "η" to "イータ", "θ" to "シータ", "ι" to "イオタ", "κ" to "カッパ",
        "λ" to "ラムダ", "μ" to "ミュー", "ν" to "ニュー", "ξ" to "クサイ", "ο" to "オミクロン",
        "π" to "パイ", "ρ" to "ロー", "σ" to "シグマ", "τ" to "タウ", "υ" to "ウプシロン",
        "φ" to "ファイ", "χ" to "カイ", "ψ" to "プサイ", "ω" to "オメガ"
    )

    // 用于检测首字符是否为非字母/数字/日文字符（即标点或符号）的正则
    private val MARKS_REGEX = Regex("[^A-Za-z\\d\u3005\u3040-\u30FF\u4E00-\u9FFF\uFF11-\uFF19\uFF21-\uFF3A\uFF41-\uFF5A\uFF66-\uFF9D]")

    // 内部集合：视为符号的特定字符（这些字符本身可直接作为读音，无需转换）
    private val SYMBOL_TOKENS: Set<String> = setOf("・", "、", "。", "？", "！")
    // 内部集合：不需要读音的字符（如引号、括号等，在读音中跳过）
    private val NO_YOMI_TOKENS: Set<String> = setOf("「", "」", "『", "』", "―", "（", "）", "［", "］", "[", "]")


    fun initOpenJTalk(dicPath: String, assetManager: AssetManager): Boolean {
        return openJTalkJNI.initOpenJtalk(dicPath, assetManager)
    }

    /**
     * 将阿拉伯数字（以及带货币符号的数值）转换为日语读法字符串。
     * 例如 "123" -> "ひゃくにじゅうさん"。
     * 注意：具体实现依赖外部库，此处使用占位 `TODO()`。
     */
    private fun num2wordsJa(numberStr: String): String {
        // TODO: 将数字字符串转换为日文读法
        throw NotImplementedError("Number to Japanese words conversion not implemented")
    }

    /**
     * 将字符串中的数字（包括货币金额）转换为日文朗读形式：
     *  - 移除数字中的千位分隔符`,`
     *  - 替换货币符号和数字为日文读法（例如 "$100" -> "100ドル"）
     *  - 将纯数字转换为日文读法（例如 "2023" -> "にせんにじゅうさん"）
     */
    fun japaneseConvertNumbersToWords(text: String): String {
        // 1. 去除千位分隔符（例如 "1,234" -> "1234"）
        var result = NUMBER_WITH_SEPARATOR_REGEX.replace(text) {
            it.value.replace(",", "")
        }
        // 2. 替换货币符号及其后的数字为日文读法（保留数字，符号替换为日文货币单位）
        result = CURRENCY_REGEX.replace(result) {
            val currencySign = it.groupValues[1]
            val numberPart = it.groupValues[2]
            numberPart + (CURRENCY_MAP[currencySign] ?: currencySign)
        }
        // 3. 将剩余的纯数字（含小数）转换为日文读法
        result = NUMBER_REGEX.replace(result) {
            num2wordsJa(it.value)
        }
        return result
    }

    /**
     * 将字符串中的英文字母及特定符号替换为日语读音。
     * 例如 `"ABC#%"` -> `"エービーシーシャープパーセント"`。
     */
    fun japaneseConvertAlphaSymbolsToWords(text: String): String {
        return text.lowercase().map { ch ->
            ALPHASYMBOL_YOMI[ch.toString()] ?: ch.toString()
        }.joinToString(separator = "")
    }

    /**
     * 替换字符串中的全角标点为半角标准符号，并移除无法处理的字符。
     * 仅保留日文字符、英文字母、数字和定义的标点，其余字符将被过滤掉。
     */
    fun replacePunctuation(text: String): String {
        // 按照映射表替换标点符号为标准符号
        val pattern = Regex(repMap.keys.joinToString("|") { Regex.escape(it) })
        var replaced = pattern.replace(text) { match ->
            repMap[match.value] ?: match.value
        }
        // 移除非日文、非字母数字、非允许标点的其他字符
        val disallowedPattern = Regex("[^" +
                "\u3040-\u309F" +    // 平假名
                "\u30A0-\u30FF" +    // 片假名
                "\u4E00-\u9FFF" +    // CJK统一表意字符（基本汉字）
                "\u3400-\u4DBF" +    // CJK扩展A
                "\u3005" +           // 々 (汉字重复符号)
                Regex.escape(punctuation.joinToString("")) +
                "]+")
        replaced = disallowedPattern.replace(replaced, "")
        return replaced
    }

    /**
     * 文本规范化处理：
     *  1. Unicode 正规化为 NFKC（全角转半角，兼容字符归一化）
     *  2. 转换数字和货币符号为日文读法
     *  3. 替换标点为标准符号，并移除不支持的字符
     *  4. 去除日文合成用的独立浊音符号（゙）
     */
    fun textNormalize(text: String): String {
        // 统一全角/半角等字符表示
        val nfkcNormalized: String = TODO("Normalize text to NFKC (Unicode normalization)")
        // 数字及货币符号转写
        var res = japaneseConvertNumbersToWords(nfkcNormalized)
        // （可选）过滤非日语字符：若只希望保留日文，可取消下面注释
        // res = res.filter { isJapaneseCharacter(it) }
        // 替换标点并移除非法字符
        res = replacePunctuation(res)
        // 去除日语浊音符号的独立字符（例如把 "が" 中的 "゙" 去掉）
        res = res.replace("゙", "")
        return res
    }

    /**
     * 判断单个字符是否属于日语字符（平假名、片假名或中日汉字）。
     */
    fun isJapaneseCharacter(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x3040..0x309F) ||    // 平假名
                (code in 0x30A0..0x30FF) ||    // 片假名
                (code in 0x4E00..0x9FFF) ||    // 汉字（基本区）
                (code in 0x3400..0x4DBF)       // 汉字（扩展A区）
    }

    /**
     * 将 nPhone 个音素平均分配给 nWord 个词片段（BERT 分词后的子词）。
     * 返回长度为 nWord 的列表，每个元素表示对应词片段包含的音素数量。
     */
    fun distributePhone(nPhone: Int, nWord: Int): List<Int> {
        val phonesPerWord = MutableList(nWord) { 0 }
        repeat(nPhone) {
            // 每次将一个音素分配给当前音素数最少的片段
            val minValue = phonesPerWord.minOrNull() ?: 0
            val minIndex = phonesPerWord.indexOf(minValue)
            phonesPerWord[minIndex] += 1
        }
        return phonesPerWord
    }

    /**
     * 处理片假名音素列表中的长音符号 "ー"：
     * 将其替换为前一个音素（通常是元音），实现前一音素的延长发音。
     *  - 如果序列开头是 "ー"，则用上一序列最后一个音素替换之
     *  - 对序列中每个 "ー"，用其前一个音素的最后一个字符替换
     */
    fun handleLong(sepPhonemes: List<List<String>>): List<List<String>> {
        return sepPhonemes.mapIndexed { i, phonemeList ->
            val list = phonemeList.toMutableList()
            if (list.isNotEmpty() && list[0] == "ー" && i > 0) {
                // 若当前序列第一个元素是 "ー"，用前一序列最后一个音素替换
                list[0] = sepPhonemes[i - 1].last()
            }
            for (j in list.indices) {
                if (list[j] == "ー" && j > 0) {
                    // 将 "ー" 替换为其前一个音素的最后一个字符
                    list[j] = list[j - 1].last().toString()
                }
            }
            list.toList()
        }
    }

    /**
     * 将片假名字符串转换为对应的音素序列（使用 pyopenjtalk G2P）。
     * 返回音素列表。注：依赖 pyopenjtalk 库，此处以 `TODO()` 占位。
     */
    fun kata2phoneme(text: String): List<String> {
        var txt = text.trim()
        // 特殊情况：字符串仅为长音符号
        if (txt == "ー") {
            return listOf("ー")
        } else if (txt.startsWith("ー")) {
            // 若以 "ー" 开头，保留该长音并递归处理余下部分
            return listOf("ー") + kata2phoneme(txt.substring(1))
        }
        val res = mutableListOf<String>()
        while (txt.isNotEmpty()) {
            if (MARKS_REGEX.matches(txt.substring(0, 1))) {
                // 文本开头是符号/标点
                res.add(txt)
                txt = txt.substring(1)
                continue
            }
            if (txt.startsWith("ー")) {
                // 处理连续长音符号
                if (res.isNotEmpty()) {
                    res.add(res.last())
                }
                txt = txt.substring(1)
                continue
            }
            // 正常情况：调用 OpenJTalk 的 G2P 获取剩余文本的音素序列
            val phonemeStr: String = TODO("Call pyopenjtalk.g2p for text -> phoneme sequence")
            // 转小写并将 'cl' 替换为 'q'
            val phonemeTokens = phonemeStr.lowercase()
                .replace("cl", "q")
                .split(" ")
            res.addAll(phonemeTokens)
            // 由于一次处理完整文本，这里跳出循环
            break
        }
        return res
    }

    /**
     * 调用 pyopenjtalk 前端，对规范化文本进行分词和假名转换，并提取重音信息。
     * 返回三元组 (sepText, sepKata, acc)：
     *  - sepText: 原文本按单词和标点分割的序列
     *  - sepKata: 对应的假名读音序列（与 sepText 对齐）
     *  - acc: 对应的音素重音信息列表（音素与高低音标志对齐的序列）
     */
    fun text2sepKata(text: String): Triple<List<String>, List<String>, List<Pair<String, Int>>>? {
        val openJTalkResult = openJTalkJNI.run_frontend_make_label(text)
        Log.i(TAG, "OpenJTalk result: ${openJTalkResult.first.toList()} , \n ${openJTalkResult.second.toList()} labels")
        val parsed: List<Feature> = openJTalkResult.first.toList()
        val sepText = mutableListOf<String>()
        val res = mutableListOf<String>()
        for (node in parsed) {
            var word = replacePunctuation(node.string)
            var yomi = node.pron.replace("’", "")
            if (yomi.isNotEmpty()) {
                if (MARKS_REGEX.matches(yomi)) {
                    // 该部分读音是标点/符号
                    if (word.length > 1) {
                        // 多字符符号（如省略号）拆分成单个符号
                        val chars = word.map { replacePunctuation(it.toString()) }
                        res.addAll(chars)
                        sepText.addAll(chars)
                        continue
                    } else if (word !in repMap.keys && word !in repMap.values) {
                        word = ","
                        yomi = word
                    }
                }
                res.add(yomi)
            } else {
                if (word in SYMBOL_TOKENS) {
                    res.add(word)
                } else if (word in listOf("っ", "ッ")) {
                    res.add("ッ")
                } else if (word in NO_YOMI_TOKENS) {
                    //ignored
                } else {
                    res.add(word)
                }
            }
            sepText.add(word)
        }
        // 获取音素级别的重音信息
        val acc: List<Pair<String, Int>> = getAccent(openJTalkResult.second.toList())
        return Triple(sepText, res, acc)
    }

    /**
     * 从 OpenJTalk 的解析结果中提取音素的重音模式（高低音序列）。
     * 解析 HTS 全上下文标签，推断音调的升降变化。
     * 返回 (phoneme, accentFlag) 列表：
     *  - phoneme: 音素符号
     *  - accentFlag: 重音标志（0=无变化，1=音调上升，-1=音调下降）
     */
    fun getAccent(labels: List<String>): List<Pair<String, Int>> {
        val phonemes = mutableListOf<String>()
        val accents = mutableListOf<Int>()
        for ((n, label) in labels.withIndex()) {
            // 提取当前音素（取标签中 '-' 与 '+' 之间的部分）
            val phonemeMatch = Regex("-([^+]+)\\+").find(label)
            val phoneme = phonemeMatch?.groupValues?.get(1) ?: continue
            if (phoneme == "sil" || phoneme == "pau") {
                // 静音和停顿不计入
                continue
            }
            phonemes.add(phoneme.replace("cl", "q").lowercase())
            // 提取标签中的重音数值 A:x+y+
            val a1 = Regex("/A:(-?[0-9]+)\\+").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val a2 = Regex("\\+([0-9]+)\\+").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            // 获取下一标签的相关数值判断音调变化
            val nextLabel = labels.getOrNull(n + 1)
            val nextPhoneme = nextLabel?.let {
                Regex("-([^+]+)\\+").find(it)?.groupValues?.get(1)
            }
            val a2Next = if (nextPhoneme == null || nextPhoneme == "sil" || nextPhoneme == "pau") {
                -1
            } else {
                Regex("\\+([0-9]+)\\+").find(nextLabel)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            }
            // 判断音调升降趋势
            if (a1 == 0 && a2Next == a2 + 1) {
                accents.add(-1)  // Falling: 此音素处音调下降
            } else if (a2 == 1 && a2Next == 2) {
                accents.add(1)   // Rising: 此音素后音调上升
            } else {
                accents.add(0)   // 平调或无显著变化
            }
        }
        return phonemes.zip(accents)
    }

    /**
     * 将重音标志序列对齐到对应的音素序列上：
     * 对每个词的音素列表，根据音素匹配重音标志中的音素顺序，生成与音素等长的 0/1 标志序列。
     * 返回展开后的简单音高标志列表（长度等于所有音素数量）。
     */
    fun alignTones(phones: List<List<String>>, tones: List<Pair<String, Int>>): List<Int> {
        val toneList = tones.toMutableList()
        val aligned = mutableListOf<List<Int>>()
        for (phoList in phones) {
            val temp = MutableList(phoList.size) { 0 }
            for ((idx, p) in phoList.withIndex()) {
                if (toneList.isEmpty()) break
                if (p == toneList.first().first) {
                    temp[idx] = toneList.first().second
                    if (idx > 0) {
                        // 若上一音素标记为高音，则当前标记累加（保持连续高音标记）
                        temp[idx] += temp[idx - 1]
                    }
                    toneList.removeAt(0)
                }
            }
            // 在序列前插入 0，末尾去掉最后一个元素，实现整体右移对齐
            val shifted = listOf(0) + temp
            var toneSeq = shifted.dropLast(1)
            // 若存在 -1 标志，将整个序列的标志加1（-1变0，0变1，1变2）
            if (toneSeq.any { it == -1 }) {
                toneSeq = toneSeq.map { it + 1 }
            }
            aligned.add(toneSeq)
        }
        val flatList = aligned.flatten()
        require(flatList.none { it < 0 || it > 1 }) { "Tone alignment out of expected range" }
        return flatList
    }

    /**
     * （可选）整理高低音标志序列，将音调**上升**标记为 2，音调**下降**标记为 3。
     * 基于 alignTones 输出的 0/1 序列，进一步标记出下降位置（3）和新高音开始位置（2）。
     * **注意：默认未使用该函数，可根据需要启用。**
     */
    fun rearrangeTones(tones: List<Int>, phones: List<String>): List<Int> {
        val res = MutableList(tones.size) { 0 }
        var prevTone = tones.getOrNull(0) ?: 0
        for (i in tones.indices) {
            if (i == 0) {
                if (phones[i] !in punctuation) {
                    res[i] = 1
                }
                prevTone = tones[i]
            } else {
                if (tones[i] == prevTone) {
                    res[i] = if (phones[i] in punctuation) 0 else 1
                } else if (tones[i] > prevTone) {
                    res[i] = 2
                } else if (tones[i] < prevTone) {
                    res[i - 1] = 3
                    res[i] = 1
                }
                prevTone = tones[i]
            }
        }
        return res
    }

    /**
     * 将规范化日文文本转换为音素序列、音高标志序列和词到音素的对齐序列。
     * 返回 Triple<List<String>, List<Int>, List<Int>>：
     *  - 第一个 List<String> 是包含句首句尾静音符号 "_" 的音素序列；
     *  - 第二个 List<Int> 是对应长度的音高标志序列（0/1，包含首尾填充的 0）；
     *  - 第三个 List<Int> 是对应长度的词与音素映射序列（包含首尾填充的 1）。
     *
     * **注意：** 使用前请先对输入文本调用 `textNormalize` 进行规范化。
     */
    fun g2p(normText: String): Triple<List<String>, List<Int>, List<Int>> {
        // 1. 分词并获取假名读音及重音信息
        val (sepText, sepKata, acc) = text2sepKata(normText) ?: return Triple(emptyList(), emptyList(), emptyList())
        // 2. 使用 BERT 分词器将每个词进一步拆分为子词；标点符号直接作为单独 token
        val sepTokenized: List<List<String>> = sepText.map { token ->
            if (token in punctuation) {
                listOf(token)
            } else {
                jpTokenize(token)
            }
        }
        // 3. 将每个假名词转换为音素列表，并处理长音符号
        val sepPhonemes: List<List<String>> = handleLong(sepKata.map { kata2phoneme(it) })
        // 4. 校验所有生成的音素是否在支持的符号集合内（符号集需根据模型定义）
        for ((i, phonemeList) in sepPhonemes.withIndex()) {
            for (phoneme in phonemeList) {
                require(/* TODO: 检查 phoneme 是否在 symbols 集合中 */ true) {
                    "Phoneme '$phoneme' in token \"${sepText[i]}\" is not recognized."
                }
            }
        }
        // 5. 将重音信息对齐到音素序列，获得简单 0/1 音高标志序列
        val toneFlags: List<Int> = alignTones(sepPhonemes, acc)
        // 6. 生成 word2ph 列表：每个 BERT 子词对应的音素数量（与 phones 对齐的标记序列）
        val word2phList = mutableListOf<Int>()
        for ((tokenList, phonemeList) in sepTokenized.zip(sepPhonemes)) {
            word2phList += distributePhone(phonemeList.size, tokenList.size)
        }
        // 7. 在音素序列首尾添加静音标记 "_"，在音高和 word2ph 序列首尾添加对应填充值
        val phones: List<String> = listOf("_") + sepPhonemes.flatten() + listOf("_")
        // 如需进一步区分上升/下降，可用 rearrangeTones 处理 toneFlags（默认直接使用 0/1 标志）
        // val tones: List<Int> = listOf(0) + rearrangeTones(toneFlags, phones.drop(1).dropLast(1)) + listOf(0)
        val tones: List<Int> = listOf(0) + toneFlags + listOf(0)
        val word2ph: List<Int> = listOf(1) + word2phList + listOf(1)
        require(phones.size == tones.size) { "Phones and tones length mismatch" }
        return Triple(phones, tones, word2ph)
    }

    private fun jpTokenize(text: String): List<String> {
        TODO()
    }
}
