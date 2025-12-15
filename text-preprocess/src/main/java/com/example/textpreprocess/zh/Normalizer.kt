package com.example.textpreprocess.zh

/**
 * Author: Voine
 * Date: 2025/4/17
 * Description: simple ver for normalizer: cn2an.transform(x, "an2cn")
 */
class Normalizer {

    // 需要结构读法的单位（useUnits = true）
    private val structuredUnits = listOf(
        "元","块","角","分","毛","个","只","条","根","把","枚","粒","朵","位","名","件","台","辆","架","间","棵","株",
        "瓶","袋","双","次","例","段","年级","层","圈","面","道","年","月","日","天","小时","分钟","分","秒","毫秒",
        "米","厘米","毫米","公里","丈","尺","寸","吨","千克","公斤","克","斤","两","磅",
        "平方米","平方米","平方公里","亩","顷","平方英尺",
        "升","毫升","立方米","立方厘米","立方公里","加仑","桶",
        "度","℃","瓦","千瓦","兆瓦","千瓦时","安","伏","毫安","伏特","倍","成"
    )

    private val unitPattern by lazy {
        """(?<![A-Za-z])(-?(?:\d+)(?:\.\d+)?)(\s*)(${structuredUnits.joinToString("|")})"""
    }

    private fun normalizeNumberText(text: String): String {
        return text
            // 年
            .replace(Regex("""(\d{2,4})(\s*)(年)""")) {
                arabicToChinese(it.groupValues[1], useUnits = false) + it.groupValues[3]
            }
            // 月
            .replace(Regex("""(\d{1,2})(\s*)(月)""")) {
                arabicToChinese(it.groupValues[1], useUnits = false) + it.groupValues[3]
            }
            // 日
            .replace(Regex("""(\d{1,2})(\s*)(日)""")) {
                arabicToChinese(it.groupValues[1], useUnits = false) + it.groupValues[3]
            }
            // 分数：b分之a
            .replace(Regex("""(\d+)\s*/\s*(\d+)""")) {
                val a = it.groupValues[1]
                val b = it.groupValues[2]
                arabicToChinese(b, useUnits = true) + "分之" + arabicToChinese(a, useUnits = true)
            }
            // 百分号：百分之X
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*%""")) {
                "百分之" + arabicToChinese( it.groupValues[1], useUnits = true)
            }
            // 摄氏度
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*℃""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "摄氏度"
            }
            // 度
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*°""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "度"
            }
            // 公里每小时
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*km/h(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "公里每小时"
            }
            // 平方毫米
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*mm²(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "平方毫米"
            }
            // 平方厘米
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*cm²(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "平方厘米"
            }
            // 平方米
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*m²(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "平方米"
            }
            // 立方厘米
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*cm³(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "立方厘米"
            }
            // 立方米
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*m³(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "立方米"
            }
            // 千瓦时
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*[kK][wW][hH](?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "千瓦时"
            }
            // 毫安时
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*[mM][aA][hH](?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "毫安时"
            }
            // 毫升
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*ml(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "毫升"
            }
            // 毫米
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*mm(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "毫米"
            }
            // 厘米
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*cm(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "厘米"
            }
            // 公里
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*km(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "公里"
            }
            // 千克
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*kg(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "千克"
            }
            // 千瓦
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*[kK][wW](?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "千瓦"
            }
            // 千伏
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*[kK][vV](?![\da-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "千伏"
            }
            // 伏：只匹配“数字+v”且 v 后不是空格、数字、字母的情况，否则可能存在误伤，如“5v5”
            .replace(Regex("""(?<!\d)\b(-?(?:\d+\.)?\d+)\s*[vV](?![\da-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "伏"
            }
            // 欧
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*Ω(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "欧"
            }
            // 千欧
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*[kK]Ω(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "千欧"
            }
            // 米
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*m(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "米"
            }
            // 吨
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*T(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "吨"
            }
            // 瓦
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*[wW](?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "瓦"
            }
            // 克
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*g(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "克"
            }
            // 升
            .replace(Regex("""(-?(?:\d+\.)?\d+)\s*L(?![a-zA-Z])""")) {
                arabicToChinese(it.groupValues[1], useUnits = true) + "升"
            }
            // 5) 单位场景：数字 + 量词（先于“通用数字”替换）
            // 可按需扩展量词列表
            .replace(Regex(unitPattern)) { m ->
                val num = m.groupValues[1]
                // groupValues[2] 是空格，不做处理
                val unit = m.groupValues[3]
                arabicToChinese(num, useUnits = true) + unit
            }
            // 6) 通用数字（最后兜底）
            .replace(Regex("""-?(?:\d+\.)?\d+""")) {
                arabicToChinese(it.value, useUnits = false)
            }
    }

    /**
     * 阿拉伯数字 → 中文数字读法
     * - 支持：负号、小数、万/亿/兆分节、10~19前导"一"省略、单位场景下“二”→“两”（百/千/万/亿/兆位）
     * - useUnits=true：用于“元/个/粒/例”这类单位场景
     */
    private fun arabicToChinese(numStrRaw: String, useUnits: Boolean = false): String {
        val digits = arrayOf("零","一","二","三","四","五","六","七","八","九")
        val smallUnits = arrayOf("", "十", "百", "千")
        val bigUnits = arrayOf("", "万", "亿", "兆") // 可继续扩到 京/垓 … 如需

        fun cleanLeadingZeros(s: String): String = s.trimStart('0').ifEmpty { "0" }

        // --- 预处理：符号、小数 ---
        var numStr = numStrRaw.trim()
        if (numStr.isEmpty()) return numStr

        var sign = ""
        if (numStr.startsWith("-")) {
            sign = "负"
            numStr = numStr.drop(1)
        }

        // 小数
        if (numStr.contains('.')) {
            val (intPartRaw, decPartRaw) = numStr.split('.', limit = 2)
            val intPart = cleanLeadingZeros(intPartRaw)
            val decPart = decPartRaw.trimEnd('0') // 尾部无效0可裁剪；保留也行
            val intText = convertInteger(intPart, useUnits, digits, smallUnits, bigUnits)
            if (decPart.isEmpty()) return sign + intText // 如 "1.0" -> “一”
            val decText = decPart.map { c ->
                if (c.isDigit()) digits[c - '0'] else c
            }.joinToString("")
            return sign + intText + "点" + decText
        }

        // 纯整数
        val intPart = cleanLeadingZeros(numStr)
        val intText = convertInteger(intPart, useUnits, digits, smallUnits, bigUnits)
        return sign + intText
    }

    private fun convertInteger(
        intPart: String,
        useUnits: Boolean,
        digits: Array<String>,
        smallUnits: Array<String>,
        bigUnits: Array<String>
    ): String {
        if (intPart == "0") return digits[0]

        // ---- 非单位场景，且数字长度 <= 6 且不是日期/分数时，逐位读 ----
        if (!useUnits) {
            // 如果字符串中没有'.'，且看起来像普通整数，就逐位念
            // 注意：此函数只处理整数部分
            return intPart.map { c ->
                if (c.isDigit()) digits[c - '0'] else c
            }.joinToString("")
        }

        // 每4位分节：… [兆][亿][万][个]
        val sections = mutableListOf<String>()
        var s = intPart
        while (s.isNotEmpty()) {
            val take = s.takeLast(4)
            sections.add(take)
            s = s.dropLast(4)
        }
        // sections: 从低位到高位

        val sectionTexts = mutableListOf<String>()
        for ((idx, sec) in sections.withIndex()) {
            val secText = convertSection(sec, useUnits, digits, smallUnits)
            if (secText.isNotEmpty()) {
                sectionTexts.add(secText + bigUnits[idx])
            } else {
                sectionTexts.add("") // 该节为0
            }
        }

        // 组装并处理“零”的连接
        var result = ""
        for (i in sectionTexts.indices.reversed()) { // 从高位到低位拼接
            val cur = sectionTexts[i]
            if (cur.isEmpty()) {
                // 若当前节为 0，但后面（低位）已经有非空且不以“零”开头，需要填一个“零”
                if (result.isNotEmpty() && !result.endsWith("零")) {
                    // 仅当前后之间存在非空节才补零
                    // 查找低位是否存在非空节
                    if ((i downTo 0).any { sectionTexts[it].isNotEmpty() }) {
                        result += "零"
                    }
                }
            } else {
                // 如果上一个字符是“零”，且当前以“零”开头，去重
                if (result.endsWith("零") && cur.startsWith("零")) {
                    result = result.dropLast(1)
                }
                result += cur
            }
        }

        // 去掉末尾可能的“零”
        result = result.trimEnd('零')

        // 10~19 省略“一十” → “十”
        if (result.startsWith("一十")) {
            result = result.removePrefix("一")
        }

        return result
    }

    /**
     * 把 0..9999（字符串）转为中文读法，不带大单位（万/亿）。
     * 在 useUnits=true 时：
     *   - 在“百/千”位或更高位（跨节的大单位）遇到“2”时优先用“两”（不影响“二十”）
     */
    private fun convertSection(
        secRaw: String,
        useUnits: Boolean,
        digits: Array<String>,
        smallUnits: Array<String>
    ): String {
        val sec = secRaw.padStart(4, '0')
        if (sec == "0000") return ""

        val sb = StringBuilder()
        var zeroPending = false

        fun digitToWord(d: Int, pos: Int): String {
            // pos: 0个位,1十位,2百位,3千位
            // 在百/千位时（pos>=2）且 useUnits=true 且 d==2，读“两”
            val base = if (useUnits && d == 2 && pos >= 2) "两" else digits[d]
            return if (d == 0) "" else base + smallUnits[pos]
        }

        for (i in 3 downTo 0) {
            val d = sec[3 - i] - '0' // i:3千位→0个位
            if (d == 0) {
                zeroPending = sb.isNotEmpty() // 只有前面已经有内容才可能需要“零”
            } else {
                if (zeroPending) {
                    // 补一个“零”
                    if (!sb.endsWith("零")) sb.append("零")
                    zeroPending = false
                }
                sb.append(digitToWord(d, i))
            }
        }

        var res = sb.toString()

        // 特例：本节数值在 10~19 且该节本身最高位为十位（如 0013 -> “一十三”→“十三”）
        // 但仅当该节是“本段起始”（由上层控制时更精确），这里做保守处理：
        if (res.startsWith("一十") && res.length >= 2) {
            // 例如 “一十三” -> “十三”
            res = res.removePrefix("一")
        }

        return res
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
        val resultBuilder = StringBuilder()
        for (ch in result) {
            resultBuilder.append(repMap[ch.toString()] ?: ch.toString())
        }
        result = resultBuilder.toString()
        result = result.replace(Regex("\\s+"), ",").trim()
        //保留数学单位符号以及推理 symbols
        val allowedPunctuation = ".-/%°℃Ω²³$punctuation"
        val allowedEscaped =  Regex.escape(allowedPunctuation)
        val pattern = Regex("[^\u4e00-\u9fa5A-Za-z0-9$allowedEscaped]+")
        result = result.replace(pattern, "")
        result = normalizeNumberText(result)
        return result
    }
}