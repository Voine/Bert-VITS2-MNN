package com.example.bertvits2.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

/**
 * Author: Voine
 * Date: 2026/8/5
 * Description: 响应 android.speech.tts.engine.GET_SAMPLE_TEXT，
 * 让系统 TTS 设置页的「收听示例」有对应语言的示例文本。
 */
class GetSampleTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lang = TtsPrefs.Lang.fromLanguageCode(intent?.getStringExtra("language"))
            ?: TtsPrefs.Lang.ZH
        val result = Intent().apply {
            putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sampleTextOf(lang))
        }
        setResult(TextToSpeech.LANG_AVAILABLE, result)
        finish()
    }

    companion object {
        fun sampleTextOf(lang: TtsPrefs.Lang): String = when (lang) {
            TtsPrefs.Lang.ZH -> "你好，这里是 Bert-VITS2 端侧语音合成。"
            TtsPrefs.Lang.EN -> "Hello, this is Bert-VITS2 running on your device."
            TtsPrefs.Lang.JP -> "こんにちは、これはデバイス上の音声合成です。"
        }
    }
}
