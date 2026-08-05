package com.example.bertvits2.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Author: Voine
 * Date: 2026/8/5
 * Description: 分句逻辑单测（纯 JVM）
 */
class TtsTextSplitterTest {

    @Test
    fun `blank text yields nothing`() {
        assertTrue(TtsTextSplitter.split("").isEmpty())
        assertTrue(TtsTextSplitter.split("   \n ").isEmpty())
    }

    @Test
    fun `punctuation only text yields nothing`() {
        assertTrue(TtsTextSplitter.split("。。。！？").isEmpty())
    }

    @Test
    fun `keeps sentence ending punctuation with previous chunk`() {
        assertEquals(
            listOf("你好。", "今天天气不错！", "对吧？"),
            TtsTextSplitter.split("你好。今天天气不错！对吧？")
        )
    }

    @Test
    fun `splits on newline`() {
        assertEquals(listOf("第一行", "第二行"), TtsTextSplitter.split("第一行\n第二行"))
    }

    @Test
    fun `long sentence falls back to clause punctuation`() {
        val text = "甲乙丙丁戊己庚辛，壬癸子丑寅卯辰巳，午未申酉戌亥"
        val chunks = TtsTextSplitter.split(text, maxLength = 10)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 10 })
        assertEquals(text.filter { it.isLetterOrDigit() }, chunks.joinToString("").filter { it.isLetterOrDigit() })
    }

    @Test
    fun `long text without punctuation is hard split`() {
        val text = "一".repeat(95)
        val chunks = TtsTextSplitter.split(text, maxLength = 20)
        assertEquals(5, chunks.size)
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun `english long sentence prefers space boundary`() {
        val text = "the quick brown fox jumps over the lazy dog again and again"
        val chunks = TtsTextSplitter.split(text, maxLength = 20)
        assertTrue(chunks.all { it.length <= 20 })
        // 不应该把单词切断
        assertTrue(chunks.none { it.endsWith("qui") })
        assertEquals(text.replace(" ", ""), chunks.joinToString("").replace(" ", ""))
    }

    @Test
    fun `short clauses are merged instead of one by one`() {
        // 单句超长触发次级切分后，短子句会被合并到接近上限
        val chunks = TtsTextSplitter.split("你好，我是甲，他是乙，她是丙，我们是丁。", maxLength = 12)
        assertTrue(chunks.all { it.length <= 12 })
        assertTrue(chunks.size < 5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non positive maxLength is rejected`() {
        TtsTextSplitter.split("你好", maxLength = 0)
    }
}
