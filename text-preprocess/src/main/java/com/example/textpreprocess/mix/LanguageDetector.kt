package com.example.textpreprocess.mix

/**
 * Author: Voine
 * Date: 2026/3/3
 * Description: 简单的中英语言检测工具
 */

enum class DetectedLanguage {
    PURE_ZH,
    PURE_EN,
    MIX_ZH_EN,
}

/**
 * 快速判断文本是纯中文、纯英文还是中英混合。
 *
 * 策略：
 * 1. 忽略标点、空白、数字等非语言字符
 * 2. 只看「字母」和「CJK 汉字」的比例
 * 3. 纯中文 = 只有 CJK 字符（无英文字母）
 * 4. 纯英文 = 只有英文字母（无 CJK 字符）
 * 5. 其他 = 中英混合
 */
fun detectLanguage(text: String): DetectedLanguage {
    var hasChinese = false
    var hasEnglish = false

    for (ch in text) {
        if (isCjk(ch)) {
            hasChinese = true
        } else if (ch in 'A'..'Z' || ch in 'a'..'z') {
            hasEnglish = true
        }
        // 同时检测到中文和英文时可以提前返回
        if (hasChinese && hasEnglish) return DetectedLanguage.MIX_ZH_EN
    }

    return when {
        hasChinese && !hasEnglish -> DetectedLanguage.PURE_ZH
        hasEnglish && !hasChinese -> DetectedLanguage.PURE_EN
        hasChinese && hasEnglish -> DetectedLanguage.MIX_ZH_EN
        else -> DetectedLanguage.PURE_ZH // 纯标点/数字 fallback 到中文
    }
}

/** 判断字符是否属于 CJK 统一表意文字区 */
private fun isCjk(ch: Char): Boolean {
    val type = Character.UnicodeBlock.of(ch)
    return type == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || type == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || type == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || type == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            || type == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
}
