package com.example.textpreprocess.jp

import java.io.File

/**
 * Author: Voine
 * Date: 2025/12/15
 * Description:
 */
class JapaneseCharTokenizer(
    vocabFilePath: String
) {
    private val tokenToId = HashMap<String, Int>()
    private val idPad: Int
    private val idCls: Int
    private val idSep: Int
    private val idUnk: Int

    init {
        File(vocabFilePath).inputStream().bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line ->
                tokenToId[line.trim()] = index
            }
        }
        idPad = tokenToId["[PAD]"]!!
        idCls = tokenToId["[CLS]"]!!
        idSep = tokenToId["[SEP]"]!!
        idUnk = tokenToId["[UNK]"]!!
    }

    fun encode(
        text: String,
    ): Pair<List<Int>, List<Int>> {

        val tokens = ArrayList<Int>()
        val mask = ArrayList<Int>()

        tokens.add(idCls)
        mask.add(1)

        for (ch in splitToCharacters(text)) {
            val id = tokenToId[ch] ?: idUnk
            tokens.add(id)
            mask.add(1)
        }

        tokens.add(idSep)
        mask.add(1)

        return Pair(tokens, mask)
    }

    private fun splitToCharacters(text: String): List<String> {
        val result = ArrayList<String>(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            result.add(String(Character.toChars(cp)))
            i += Character.charCount(cp)
        }
        return result
    }
}

