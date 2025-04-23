package com.example.bertvits2mnn.preprocess

import kotlin.collections.iterator

/**
 * Author: Voine
 * Date: 2025/4/17
 * Description:
 */
class Normalizer {

    private fun normalizeNumberText(text: String): String {
        return text
            .replace(Regex("""(\d{2,4})年(\d{1,2})月(\d{1,2})日""")) {
                val (y, m, d) = it.destructured
                arabicToChinese(y) + "年" + arabicToChinese(m) + "月" + arabicToChinese(d) + "日"
            }
            .replace(Regex("""(\d{1,4})月(\d{1,2})日""")) {
                val (m, d) = it.destructured
                arabicToChinese(m) + "月" + arabicToChinese(d) + "日"
            }
            .replace(Regex("""(\d{1,2})日""")) {
                arabicToChinese(it.value.dropLast(1)) + "日"
            }
            .replace(Regex("""\d+/\d+""")) {
                val (a, b) = it.value.split("/")
                arabicToChinese(b) + "分之" + arabicToChinese(a)
            }
            .replace(Regex("""-?(\d+\.)?\d+%""")) {
                arabicToChinese(it.value.dropLast(1)) + "百分之"
            }
            .replace(Regex("""\d+℃""")) {
                arabicToChinese(it.value.dropLast(1)) + "摄氏度"
            }
            .replace(Regex("""-?(\d+\.)?\d+""")) {
                arabicToChinese(it.value)
            }
    }

    private fun arabicToChinese(numStr: String): String {
        val digits = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
        val units = listOf("", "十", "百", "千")

        return try {
            if (numStr.contains(".")) {
                val parts = numStr.split(".")
                val intPart = parts[0].mapIndexed { i, c -> digits[c.digitToInt()] + units.getOrNull(parts[0].length - 1 - i).orEmpty() }.joinToString("")
                val decPart = parts[1].map { digits[it.digitToInt()] }.joinToString("")
                intPart + "点" + decPart
            } else {
                numStr.mapIndexed { i, c -> digits[c.digitToInt()] + units.getOrNull(numStr.length - 1 - i).orEmpty() }.joinToString("")
            }
        } catch (e: Exception) {
            numStr // fallback
        }
    }

    fun normalizeText(text: String, punctuation: String): String {
        val repMap = mapOf(
            "：" to ",", "；" to ",", "，" to ",", "。" to ".", "！" to "!", "？" to "?",
            "\n" to ".", "·" to ",", "、" to ",", "..." to "…", "$" to ".",
            "“" to "'", "”" to "'", "\"" to "'", "‘" to "'", "’" to "'",
            "（" to "'", "）" to "'", "(" to "'", ")" to "'",
            "《" to "'", "》" to "'", "【" to "'", "】" to "'", "[" to "'", "]" to "'",
            "—" to "-", "～" to "-", "~" to "-", "「" to "'", "」" to "'"
        )

        var result = text.replace("嗯", "恩").replace("呣", "母")
        result = normalizeNumberText(result)
        for ((k, v) in repMap) {
            result = result.replace(k, v)
        }
        val regex = Regex("[^\u4e00-\u9fa5${punctuation}]+")
        return result.replace(regex, "")
    }
}