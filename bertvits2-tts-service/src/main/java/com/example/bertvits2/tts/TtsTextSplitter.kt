package com.example.bertvits2.tts

/**
 * Author: Voine
 * Date: 2026/8/5
 * Description: 把一段待朗读文本切成适合 BV2 单次推理的短句。
 *
 * 端侧推理耗时基本随文本长度线性增长，整段合成会让「首字延迟」等于整段耗时，
 * 而且长文本的中间结果会一次性驻留内存。分句之后可以边合成边通过
 * SynthesisCallback 往外吐音频，首字延迟只取决于第一句。
 *
 * 切分优先级：句末标点 -> 句中标点 -> 空格（英文）-> 硬切。
 */
object TtsTextSplitter {

    /** 单句最大长度，经验值：中文 40 字左右一次推理在中端机上约 2s */
    const val DEFAULT_MAX_LENGTH = 40

    /** 句末标点，切分后保留在前一句末尾，交给 BV2 预处理决定停顿 */
    private const val SENTENCE_END_PUNCTUATION = "。！？!?；;…\n\r"

    /** 句中标点，仅在单句超长时作为次级切点 */
    private const val CLAUSE_PUNCTUATION = "，,、：:—"

    fun split(text: String, maxLength: Int = DEFAULT_MAX_LENGTH): List<String> {
        if (maxLength <= 0) throw IllegalArgumentException("maxLength must be positive: $maxLength")
        // BOM(U+FEFF) / 不换行空格(U+00A0) 会被当成普通字符带进推理，先归一成空格
        val normalized = text.replace('\uFEFF', ' ').replace('\u00A0', ' ').trim()
        if (normalized.isEmpty()) return emptyList()

        return splitByPunctuation(normalized, SENTENCE_END_PUNCTUATION)
            .flatMap { sentence ->
                if (sentence.length <= maxLength) {
                    listOf(sentence)
                } else {
                    // 超长句：先按句中标点拆，拆完还超长再按空格 / 硬切
                    mergeToLimit(splitByPunctuation(sentence, CLAUSE_PUNCTUATION), maxLength)
                        .flatMap { clause ->
                            if (clause.length <= maxLength) listOf(clause)
                            else splitByLength(clause, maxLength)
                        }
                }
            }
            .map { it.trim() }
            .filter { hasSpeakableContent(it) }
    }

    /** 按给定标点集切分，标点保留在前一段末尾 */
    private fun splitByPunctuation(text: String, punctuation: String): List<String> {
        val result = mutableListOf<String>()
        val buffer = StringBuilder()
        for (ch in text) {
            buffer.append(ch)
            if (punctuation.indexOf(ch) >= 0) {
                result.add(buffer.toString())
                buffer.clear()
            }
        }
        if (buffer.isNotEmpty()) result.add(buffer.toString())
        return result.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 把过短的相邻片段合回来，避免「你好，」「我是，」这种一字一顿地推理。
     * 合并后单段不超过 [maxLength]。
     */
    private fun mergeToLimit(parts: List<String>, maxLength: Int): List<String> {
        val result = mutableListOf<String>()
        val buffer = StringBuilder()
        for (part in parts) {
            if (buffer.isNotEmpty() && buffer.length + part.length > maxLength) {
                result.add(buffer.toString())
                buffer.clear()
            }
            buffer.append(part)
        }
        if (buffer.isNotEmpty()) result.add(buffer.toString())
        return result
    }

    /** 兜底切分：英文优先在空格处断开，否则按长度硬切 */
    private fun splitByLength(text: String, maxLength: Int): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + maxLength).coerceAtMost(text.length)
            var cut = end
            if (end < text.length) {
                val lastSpace = text.lastIndexOf(' ', end - 1)
                // 空格太靠前就不用它，否则会切出很短的碎片
                if (lastSpace > start + maxLength / 2) cut = lastSpace + 1
            }
            result.add(text.substring(start, cut))
            start = cut
        }
        return result
    }

    /** 纯标点 / 空白的片段没有朗读价值，直接丢掉，否则 BV2 预处理容易返回空结果 */
    private fun hasSpeakableContent(text: String): Boolean =
        text.any { it.isLetterOrDigit() }
}
