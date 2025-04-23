package com.example.bertvits2mnn.preprocess

/**
 * Author: Voine
 * Date: 2025/4/17
 * Description:
 */
class ToneSandhi {
    fun modifiedTone(word: String, pos: String, finals: List<String>): List<String> {
        var modified = finals.toMutableList()
        modified = applyBuRule(word, modified)
        modified = applyYiRule(word, modified)
        modified = applyNeuralRule(word, pos, modified)
        modified = applyThreeToneRule(word, modified)
        return modified
    }

    fun preMergeForModify(seg: List<WordPos>): List<WordPos> {
        val bu = mergeBu(seg)
        val yi = mergeYi(bu)
        val reduplication = mergeReduplication(yi)
        val tripleThirdTone = mergeTripleThirdTone(reduplication)
        val tripleThirdToneAlt = mergeTripleThirdToneAlt(tripleThirdTone)
        val er = mergeEr(tripleThirdToneAlt)
        return er
    }

    private fun mergeBu(seg: List<WordPos>): List<WordPos> {
        val result = mutableListOf<WordPos>()
        var last: WordPos? = null
        for (current in seg) {
            if (last?.word == "不") {
                result.removeAt(result.lastIndex)
                result.add(WordPos(last.word + current.word, current.pos))
            } else {
                result.add(current)
            }
            last = current
        }
        return result
    }

    private fun mergeYi(seg: List<WordPos>): List<WordPos> {
        val result = mutableListOf<WordPos>()
        var i = 0
        while (i < seg.size) {
            if (
                i > 0 && i + 1 < seg.size &&
                seg[i].word == "一" &&
                seg[i - 1].word == seg[i + 1].word &&
                seg[i - 1].pos == "v"
            ) {
                val newWord = seg[i - 1].word + "一" + seg[i + 1].word
                result.removeAt(result.lastIndex)
                result.add(WordPos(newWord, "v"))
                i += 2
            } else {
                result.add(seg[i])
            }
            i++
        }
        return result
    }

    private fun mergeReduplication(seg: List<WordPos>): List<WordPos> {
        val result = mutableListOf<WordPos>()
        for (item in seg) {
            if (result.isNotEmpty() && result.last().word == item.word) {
                val last = result.removeAt(result.lastIndex)
                result.add(WordPos(last.word + item.word, last.pos))
            } else {
                result.add(item)
            }
        }
        return result
    }

    private fun mergeTripleThirdTone(seg: List<WordPos>): List<WordPos> = seg

    private fun mergeTripleThirdToneAlt(seg: List<WordPos>): List<WordPos> = seg

    private fun mergeEr(seg: List<WordPos>): List<WordPos> {
        val result = mutableListOf<WordPos>()
        for (i in seg.indices) {
            if (i > 0 && seg[i].word == "儿") {
                val prev = result.removeAt(result.lastIndex)
                result.add(WordPos(prev.word + "儿", prev.pos))
            } else {
                result.add(seg[i])
            }
        }
        return result
    }

    private fun applyBuRule(word: String, finals: MutableList<String>): MutableList<String> {
        if (word.length == 3 && word[1] == '不') {
            finals[1] = finals[1].dropLast(1) + "5"
        } else {
            for (i in word.indices) {
                if (word[i] == '不' && i + 1 < finals.size && finals[i + 1].last() == '4') {
                    finals[i] = finals[i].dropLast(1) + "2"
                }
            }
        }
        return finals
    }

    private fun applyYiRule(word: String, finals: MutableList<String>): MutableList<String> {
        for (i in word.indices) {
            if (word[i] == '一' && i + 1 < finals.size) {
                val nextTone = finals[i + 1].last()
                finals[i] = finals[i].dropLast(1) + if (nextTone == '4') "2" else "4"
            }
        }
        return finals
    }

    private fun applyNeuralRule(word: String, pos: String, finals: MutableList<String>): MutableList<String> {
        if (word.length >= 2 && word[word.length - 1] in "吧呢啊呀嘛哦哟哒额耶喔") {
            finals[finals.size - 1] = finals.last().dropLast(1) + "5"
        } else if (word.endsWith("的") || word.endsWith("得") || word.endsWith("地")) {
            finals[finals.size - 1] = finals.last().dropLast(1) + "5"
        }
        return finals
    }

    private fun applyThreeToneRule(word: String, finals: MutableList<String>): MutableList<String> {
        if (finals.size == 2 && finals.all { it.last() == '3' }) {
            finals[0] = finals[0].dropLast(1) + "2"
        }
        return finals
    }
}