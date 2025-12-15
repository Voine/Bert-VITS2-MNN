package com.example.textpreprocess.jp

/**
 * Author: Voine
 * Date: 2025/6/9
 * Description: jp preprocess constants
 */
internal object Constant {
    // 地图：特殊符号或全角标点替换为标准符号
    val repMap: Map<String, String> = mapOf(
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
    val punctuation: Set<String> = setOf(",", ".", "!", "?", "'", "-")

    // 用于匹配带千位分隔符的数字串的正则
    val NUMBER_WITH_SEPARATOR_REGEX = Regex("[0-9]{1,3}(,[0-9]{3})+")
    // 货币符号映射为日语读法
    val CURRENCY_MAP: Map<String, String> = mapOf(
        "$" to "ドル", "¥" to "円", "£" to "ポンド", "€" to "ユーロ"
    )
    // 用于匹配货币金额（符号 + 数字）的正则
    val CURRENCY_REGEX = Regex("([$¥£€])([0-9.]*[0-9])")
    // 用于匹配整数或小数的正则
    val NUMBER_REGEX = Regex("[0-9]+(\\.[0-9]+)?")

    // 特殊字母和符号转写为日语读音的映射表
    val ALPHASYMBOL_YOMI: Map<String, String> = mapOf(
        "#" to "シャープ",
        "%" to "パーセント",
        "&" to "アンド",
        "+" to "プラス",
        "-" to "マイナス",
//        ":" to "コロン",
//        ";" to "セミコロン",
        "<" to "小なり",
        "=" to "イコール",
        ">" to "大なり",
        "@" to "アット",
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

    val hiragana_map = mapOf (
        "う゛ぁ" to " v a",
        "う゛ぃ" to " v i",
        "う゛ぇ" to " v e",
        "う゛ぉ" to " v o",
        "う゛ゅ" to " by u",
        "ぅ゛" to " v u",
        // ゔ等の処理を追加
        "ゔぁ" to " v a",
        "ゔぃ" to " v i",
        "ゔぇ" to " v e",
        "ゔぉ" to " v o",
        "ゔゅ" to " by u",
        // 2文字からなる変換規則
        "あぁ" to " a a",
        "いぃ" to " i i",
        "いぇ" to " i e",
        "いゃ" to " y a",
        "うぅ" to " u:",
        "えぇ" to " e e",
        "おぉ" to " o:",
        "かぁ" to " k a:",
        "きぃ" to " k i:",
        "くぅ" to " k u:",
        "くゃ" to " ky a",
        "くゅ" to " ky u",
        "くょ" to " ky o",
        "けぇ" to " k e:",
        "こぉ" to " k o:",
        "がぁ" to " g a:",
        "ぎぃ" to " g i:",
        "ぐぅ" to " g u:",
        "ぐゃ" to " gy a",
        "ぐゅ" to " gy u",
        "ぐょ" to " gy o",
        "げぇ" to " g e:",
        "ごぉ" to " g o:",
        "さぁ" to " s a:",
        "しぃ" to " sh i",
        "すぅ" to " s u:",
        "すゃ" to " sh a",
        "すゅ" to " sh u",
        "すょ" to " sh o",
        "せぇ" to " s e:",
        "そぉ" to " s o:",
        "ざぁ" to " z a:",
        "じぃ" to " j i:",
        "ずぅ" to " z u:",
        "ずゃ" to " zy a",
        "ずゅ" to " zy u",
        "ずょ" to " zy o",
        "ぜぇ" to " z e:",
        "ぞぉ" to " z o:",
        "たぁ" to " t a:",
        "ちぃ" to " ch i",
        "つぁ" to " ts a",
        "つぃ" to " ts i",
        "つぅ" to " ts u",
        "つゃ" to " ch a",
        "つゅ" to " ch u",
        "つょ" to " ch o",
        "つぇ" to " ts e",
        "つぉ" to " ts o",
        "てぇ" to " t e:",
        "とぉ" to " t o:",
        "だぁ" to " d a:",
        "ぢぃ" to " j i:",
        "づぅ" to " d u:",
        "づゃ" to " zy a",
        "づゅ" to " zy u",
        "づょ" to " zy o",
        "でぇ" to " d e:",
        "なぁ" to " n a:",
        "にぃ" to " n i:",
        "ぬぅ" to " n u:",
        "ぬゃ" to " ny a",
        "ぬゅ" to " ny u",
        "ぬょ" to " ny o",
        "ねぇ" to " n e:",
        "のぉ" to " n o:",
        "はぁ" to " h a:",
        "ひぃ" to " h i:",
        "ふぅ" to " f u:",
        "ふゃ" to " hy a",
        "へぇ" to " h e:",
        "ほぉ" to " h o:",
        "ばぁ" to " b a:",
        "びぃ" to " b i:",
        "ぶぅ" to " b u:",
        "ぶゅ" to " by u",
        "べぇ" to " b e:",
        "ぼぉ" to " b o:",
        "ぱぁ" to " p a:",
        "ぴぃ" to " p i:",
        "ぷぅ" to " p u:",
        "ぷゃ" to " py a",
        "ぷゅ" to " py u",
        "ぷょ" to " py o",
        "ぺぇ" to " p e:",
        "ぽぉ" to " p o:",
        "まぁ" to " m a:",
        "みぃ" to " m i:",
        "むぅ" to " m u:",
        "むゃ" to " my a",
        "むゅ" to " my u",
        "むょ" to " my o",
        "めぇ" to " m e:",
        "もぉ" to " m o:",
        "やぁ" to " y a:",
        "ゆぅ" to " y u:",
        "ゆゃ" to " y a:",
        "ゆゅ" to " y u:",
        "ゆょ" to " y o:",
        "よぉ" to " y o:",
        "らぁ" to " r a:",
        "りぃ" to " r i:",
        "るぅ" to " r u:",
        "るゃ" to " ry a",
        "るゅ" to " ry u",
        "るょ" to " ry o",
        "れぇ" to " r e:",
        "ろぉ" to " r o:",
        "わぁ" to " w a:",
        "をぉ" to " o:",
        "う゛" to " b u",
        "でぃ" to " d i",
        "でゃ" to " dy a",
        "でゅ" to " dy u",
        "でょ" to " dy o",
        "てぃ" to " t i",
        "てゃ" to " ty a",
        "てゅ" to " ty u",
        "てょ" to " ty o",
        "すぃ" to " s i",
        "ずぁ" to " z u",
        "ずぃ" to " z i",
        "ずぇ" to " z e",
        "ずぉ" to " z o",
        "きゃ" to " ky a",
        "きゅ" to " ky u",
        "きょ" to " ky o",
        "しゃ" to " sh a",
        "しゅ" to " sh u",
        "しぇ" to " sh e",
        "しょ" to " sh o",
        "ちゃ" to " ch a",
        "ちゅ" to " ch u",
        "ちぇ" to " ch e",
        "ちょ" to " ch o",
        "とぅ" to " t u",
        "とゃ" to " ty a",
        "とゅ" to " ty u",
        "とょ" to " ty o",
        "どぁ" to " d o ",
        "どぅ" to " d u",
        "どゃ" to " dy a",
        "どゅ" to " dy u",
        "どょ" to " dy o",
        "どぉ" to " d o:",
        "にゃ" to " ny a",
        "にゅ" to " ny u",
        "にょ" to " ny o",
        "ひゃ" to " hy a",
        "ひゅ" to " hy u",
        "ひょ" to " hy o",
        "みゃ" to " my a",
        "みゅ" to " my u",
        "みょ" to " my o",
        "りゃ" to " ry a",
        "りゅ" to " ry u",
        "りょ" to " ry o",
        "ぎゃ" to " gy a",
        "ぎゅ" to " gy u",
        "ぎょ" to " gy o",
        "ぢぇ" to " j e",
        "ぢゃ" to " j a",
        "ぢゅ" to " j u",
        "ぢょ" to " j o",
        "じぇ" to " j e",
        "じゃ" to " j a",
        "じゅ" to " j u",
        "じょ" to " j o",
        "びゃ" to " by a",
        "びゅ" to " by u",
        "びょ" to " by o",
        "ぴゃ" to " py a",
        "ぴゅ" to " py u",
        "ぴょ" to " py o",
        "うぁ" to " u a",
        "うぃ" to " w i",
        "うぇ" to " w e",
        "うぉ" to " w o",
        "ふぁ" to " f a",
        "ふぃ" to " f i",
        "ふゅ" to " hy u",
        "ふょ" to " hy o",
        "ふぇ" to " f e",
        "ふぉ" to " f o",
        // 1音からなる変換規則
        "あ" to " a",
        "い" to " i",
        "う" to " u",
        "ゔ" to " v u",  // ゔの処理を追加
        "え" to " e",
        "お" to " o",
        "か" to " k a",
        "き" to " k i",
        "く" to " k u",
        "け" to " k e",
        "こ" to " k o",
        "さ" to " s a",
        "し" to " sh i",
        "す" to " s u",
        "せ" to " s e",
        "そ" to " s o",
        "た" to " t a",
        "ち" to " ch i",
        "つ" to " ts u",
        "て" to " t e",
        "と" to " t o",
        "な" to " n a",
        "に" to " n i",
        "ぬ" to " n u",
        "ね" to " n e",
        "の" to " n o",
        "は" to " h a",
        "ひ" to " h i",
        "ふ" to " f u",
        "へ" to " h e",
        "ほ" to " h o",
        "ま" to " m a",
        "み" to " m i",
        "む" to " m u",
        "め" to " m e",
        "も" to " m o",
        "ら" to " r a",
        "り" to " r i",
        "る" to " r u",
        "れ" to " r e",
        "ろ" to " r o",
        "が" to " g a",
        "ぎ" to " g i",
        "ぐ" to " g u",
        "げ" to " g e",
        "ご" to " g o",
        "ざ" to " z a",
        "じ" to " j i",
        "ず" to " z u",
        "ぜ" to " z e",
        "ぞ" to " z o",
        "だ" to " d a",
        "ぢ" to " j i",
        "づ" to " z u",
        "で" to " d e",
        "ど" to " d o",
        "ば" to " b a",
        "び" to " b i",
        "ぶ" to " b u",
        "べ" to " b e",
        "ぼ" to " b o",
        "ぱ" to " p a",
        "ぴ" to " p i",
        "ぷ" to " p u",
        "ぺ" to " p e",
        "ぽ" to " p o",
        "や" to " y a",
        "ゆ" to " y u",
        "よ" to " y o",
        "わ" to " w a",
        "ゐ" to " i",
        "ゑ" to " e",
        "ん" to " N",
        "っ" to " q",
        // ここまでに処理されてない ぁぃぅぇぉ はそのまま大文字扱い
        "ぁ" to " a",
        "ぃ" to " i",
        "ぅ" to " u",
        "ぇ" to " e",
        "ぉ" to " o",
        "ゎ" to " w a",
        // 長音の処理
        // for (pattern, replace_str) in JULIUS_LONG_VOWEL:
        //     text = pattern.sub(replace_str, text)
        // text = text.replace("o u", "o:")  # おう -> おーの音便
        "ー" to ":",
        "〜" to ":",
        "−" to ":",
        "-" to ":",
        // その他特別な処理
        "を" to " o",
        // ここまでに処理されていないゅ等もそのまま大文字扱い（追加）
        "ゃ" to " y a",
        "ゅ" to " y u",
        "ょ" to " y o",
    )

    // 用于检测首字符是否为非字母/数字/日文字符（即标点或符号）的正则
    val MARKS_REGEX = Regex("[^A-Za-z\\d\u3005\u3040-\u30FF\u4E00-\u9FFF\uFF11-\uFF19\uFF21-\uFF3A\uFF41-\uFF5A\uFF66-\uFF9D]")

    // 内部集合：视为符号的特定字符（这些字符本身可直接作为读音，无需转换）
    val SYMBOL_TOKENS: Set<String> = setOf("・", "、", "。", "？", "！")
    // 内部集合：不需要读音的字符（如引号、括号等，在读音中跳过）
    val NO_YOMI_TOKENS: Set<String> = setOf("「", "」", "『", "』", "―", "（", "）", "［", "］", "[", "]")

    val disallowedPattern = Regex("[^" +
            "\u3040-\u309F" +    // 平假名
            "\u30A0-\u30FF" +    // 片假名
            "\u4E00-\u9FFF" +    // CJK统一表意字符（基本汉字）
            "\u3400-\u4DBF" +    // CJK扩展A
            "\u3005" +           // 々 (汉字重复符号)
            Regex.escape(punctuation.joinToString("")) +
            "]+")

    val replacePunctuationPattern = Regex(repMap.keys.joinToString("|") { Regex.escape(it) })

    val phonemeMatchRegex = Regex("-([^+]+)\\+")

    val getAccentA1Regex = Regex("/A:(-?[0-9]+)\\+")

    val getAccentA2Regex = Regex("\\+([0-9]+)\\+")

    val getAccentNextPhonemeRegex = Regex("-([^+]+)\\+")

    val getAccentA2NextRegex = Regex("\\+([0-9]+)\\+")
}