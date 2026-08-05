package com.example.bertvits2.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

/**
 * Author: Voine
 * Date: 2026/8/5
 * Description: 响应 android.speech.tts.engine.CHECK_TTS_DATA。
 * 本引擎的模型全部打在 apk assets 里，不需要额外下载语音数据，直接返回 PASS。
 */
class CheckVoiceDataActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val available = ArrayList(TtsPrefs.Lang.entries.map { "${it.iso3}-${it.iso3Country}" })
        val result = Intent().apply {
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, available)
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList())
        }
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result)
        finish()
    }
}
