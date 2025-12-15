package com.example.textpreprocess.zh

/**
 * Author: Voine
 * Date: 2025/4/21
 * Description: translate from bv2 symbols file
 */
val pad = "_"
val punctuation = listOf("!", "?", "…", ",", ".", "'", "-")
val pu_symbols = punctuation + listOf("SP", "UNK")
val zh_symbols = listOf(
    "E",
    "En",
    "a",
    "ai",
    "an",
    "ang",
    "ao",
    "b",
    "c",
    "ch",
    "d",
    "e",
    "ei",
    "en",
    "eng",
    "er",
    "f",
    "g",
    "h",
    "i",
    "i0",
    "ia",
    "ian",
    "iang",
    "iao",
    "ie",
    "in",
    "ing",
    "iong",
    "ir",
    "iu",
    "j",
    "k",
    "l",
    "m",
    "n",
    "o",
    "ong",
    "ou",
    "p",
    "q",
    "r",
    "s",
    "sh",
    "t",
    "u",
    "ua",
    "uai",
    "uan",
    "uang",
    "ui",
    "un",
    "uo",
    "v",
    "van",
    "ve",
    "vn",
    "w",
    "x",
    "y",
    "z",
    "zh",
    "AA",
    "EE",
    "OO",
)

val ja_symbols = listOf(
    "N",
    "a",
    "a:",
    "b",
    "by",
    "ch",
    "d",
    "dy",
    "e",
    "e:",
    "f",
    "g",
    "gy",
    "h",
    "hy",
    "i",
    "i:",
    "j",
    "k",
    "ky",
    "m",
    "my",
    "n",
    "ny",
    "o",
    "o:",
    "p",
    "py",
    "q",
    "r",
    "ry",
    "s",
    "sh",
    "t",
    "ts",
    "ty",
    "u",
    "u:",
    "w",
    "y",
    "z",
    "zy",
)

val en_symbols = listOf(
    "aa",
    "ae",
    "ah",
    "ao",
    "aw",
    "ay",
    "b",
    "ch",
    "d",
    "dh",
    "eh",
    "er",
    "ey",
    "f",
    "g",
    "hh",
    "ih",
    "iy",
    "jh",
    "k",
    "l",
    "m",
    "n",
    "ng",
    "ow",
    "oy",
    "p",
    "r",
    "s",
    "sh",
    "t",
    "th",
    "uh",
    "uw",
    "V",
    "w",
    "y",
    "z",
    "zh",
)
val normal_symbols = (zh_symbols + ja_symbols + en_symbols).toSet().sorted()
val zhSymbolsMap = (listOf(pad) + normal_symbols + pu_symbols).mapIndexed { index, symbol ->
    symbol to index
}.toMap()

// 语种声调数
val num_zh_tones = 6
val num_ja_tones = 2
val num_en_tones = 4

fun <T> intersperse(lst: List<T>, item: T): List<T> {
    val result = MutableList(lst.size * 2 + 1) { item }
    for (i in lst.indices) {
        result[i * 2 + 1] = lst[i]
    }
    return result
}

/**
 * 标点归一化映射表（统一入口）
 * 将各类中日英特殊标点映射为模型支持的 punctuation symbol
 * 注意：使用 linkedMapOf 保证迭代顺序，多字符 key 放在其子串前面
 */
val normalizepunctuationMap = linkedMapOf(
    // 多字符 key 优先（避免被单字符 key 先匹配到子串）
    "···" to "…", "・・・" to "…", "..." to "…",
    // CJK 标点 → ASCII 等价
    "：" to ",", "；" to ",", "，" to ",", "。" to ".", "！" to "!", "？" to "?",
    "．" to ".",
    "\n" to ".",
    "·" to ",", "・" to ",", "、" to ",",
    "$" to ".",
    // 引号 / 括号 → 统一为 '
    """ to "'", """ to "'", "\"" to "'", "\u2018" to "'", "\u2019" to "'",
    "（" to "'", "）" to "'", "(" to "'", ")" to "'",
    "《" to "'", "》" to "'", "【" to "'", "】" to "'", "[" to "'", "]" to "'",
    "「" to "'", "」" to "'",
    // 破折号 / 波浪号 → -
    "—" to "-", "−" to "-", "～" to "-", "~" to "-",
)
