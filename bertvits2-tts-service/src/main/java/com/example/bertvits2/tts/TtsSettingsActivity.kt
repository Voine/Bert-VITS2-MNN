package com.example.bertvits2.tts

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.bertvits2_infer_wrapper.interfaces.SpeakerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Author: Voine
 * Date: 2026/8/5
 * Description: 引擎设置页，系统 TTS 设置里点齿轮进来。
 * 让用户为中 / 英 / 日各挑一个默认角色，并调整基础语速，可就地试听。
 */
class TtsSettingsActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val previewPlayer = PreviewPlayer()

    private lateinit var statusView: TextView
    private lateinit var speakerContainer: LinearLayout
    private lateinit var lengthScaleLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tts_settings)
        Bv2InferManager.acquire(this)

        statusView = findViewById(R.id.tv_status)
        speakerContainer = findViewById(R.id.speaker_container)
        lengthScaleLabel = findViewById(R.id.tv_length_scale)
        setupLengthScale(findViewById(R.id.seek_length_scale))

        uiScope.launch {
            val success = Bv2InferManager.ensureInit()
            if (!success) {
                statusView.setText(R.string.bv2_tts_init_failed)
                return@launch
            }
            val speakers = Bv2InferManager.speakers()
            if (speakers.isEmpty()) {
                statusView.setText(R.string.bv2_tts_no_speaker)
                return@launch
            }
            statusView.text = getString(R.string.bv2_tts_engine_label)
            buildSpeakerRows(speakers)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
        previewPlayer.release()
        Bv2InferManager.release()
    }

    private fun setupLengthScale(seekBar: SeekBar) {
        val current = TtsPrefs.getBaseLengthScale(this)
        seekBar.progress = scaleToProgress(current)
        updateLengthScaleLabel(current)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLengthScaleLabel(progressToScale(progress))
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) {
                TtsPrefs.setBaseLengthScale(this@TtsSettingsActivity, progressToScale(bar?.progress ?: 0))
            }
        })
    }

    private fun updateLengthScaleLabel(scale: Float) {
        lengthScaleLabel.text = getString(R.string.bv2_tts_length_scale) + ": " + String.format("%.2f", scale)
    }

    private fun buildSpeakerRows(speakers: List<SpeakerInfo>) {
        speakerContainer.removeAllViews()
        TtsPrefs.Lang.entries.forEach { lang ->
            val candidates = speakers.filter { it.languageTag == lang.tag }
            if (candidates.isEmpty()) return@forEach
            val row = LayoutInflater.from(this)
                .inflate(R.layout.item_tts_speaker_row, speakerContainer, false)
            row.findViewById<TextView>(R.id.tv_lang_label).setText(labelOf(lang))

            val spinner = row.findViewById<Spinner>(R.id.sp_speaker)
            spinner.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                candidates.map { it.name }
            )
            val saved = TtsPrefs.getDefaultSpeaker(this, lang)
            val savedIndex = candidates.indexOfFirst { it.name == saved }
            if (savedIndex >= 0) spinner.setSelection(savedIndex)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    TtsPrefs.setDefaultSpeaker(this@TtsSettingsActivity, lang, candidates[position].name)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

            row.findViewById<Button>(R.id.btn_preview).setOnClickListener { button ->
                val selected = candidates.getOrNull(spinner.selectedItemPosition) ?: return@setOnClickListener
                preview(selected, lang, button as Button)
            }
            speakerContainer.addView(row)
        }
    }

    private fun preview(speaker: SpeakerInfo, lang: TtsPrefs.Lang, button: Button) {
        button.isEnabled = false
        val previousText = button.text
        button.setText(R.string.bv2_tts_previewing)
        uiScope.launch {
            val result = withContext(Dispatchers.Default) {
                Bv2InferManager.synthesize(
                    GetSampleTextActivity.sampleTextOf(lang),
                    speaker.name,
                    TtsPrefs.getBaseLengthScale(this@TtsSettingsActivity)
                )
            }
            button.isEnabled = true
            button.text = previousText
            if (result == null) {
                Log.e(TAG, "preview failed for ${speaker.name}")
                statusView.setText(R.string.bv2_tts_init_failed)
                return@launch
            }
            withContext(Dispatchers.Default) { previewPlayer.play(result.first, result.second) }
        }
    }

    private fun labelOf(lang: TtsPrefs.Lang): Int = when (lang) {
        TtsPrefs.Lang.ZH -> R.string.bv2_tts_speaker_zh
        TtsPrefs.Lang.EN -> R.string.bv2_tts_speaker_en
        TtsPrefs.Lang.JP -> R.string.bv2_tts_speaker_jp
    }

    private fun progressToScale(progress: Int): Float =
        TtsPrefs.MIN_LENGTH_SCALE +
                (TtsPrefs.MAX_LENGTH_SCALE - TtsPrefs.MIN_LENGTH_SCALE) * progress / 100f

    private fun scaleToProgress(scale: Float): Int =
        ((scale - TtsPrefs.MIN_LENGTH_SCALE) /
                (TtsPrefs.MAX_LENGTH_SCALE - TtsPrefs.MIN_LENGTH_SCALE) * 100f).toInt()

    companion object {
        private const val TAG = "TtsSettingsActivity"
    }
}
