package com.example.bertvits2.tts

import android.content.Context

/**
 * Author: Voine
 * Date: 2026/8/5
 * Description: 引擎设置项持久化（每语言默认角色 + 基础语速）。
 * Service 与设置页在同一进程，直接用 SharedPreferences。
 */
object TtsPrefs {

    /** BV2 支持的语言，[tag] 为 BCP-47 标签，与 [com.example.bertvits2_infer_wrapper.interfaces.SpeakerInfo.languageTag] 对应 */
    enum class Lang(
        val tag: String,
        /** ISO3 语言码，系统 TTS 框架用的就是这套 */
        val iso3: String,
        /** ISO2 国家码，用于 onGetLanguage */
        val country: String,
        /** ISO3 国家码，用于 CHECK_TTS_DATA 返回的可用语音列表 */
        val iso3Country: String,
    ) {
        ZH("zh-CN", "zho", "CN", "CHN"),
        EN("en-US", "eng", "US", "USA"),
        JP("ja-JP", "jpn", "JP", "JPN"),
        ;

        companion object {
            fun fromTag(tag: String?): Lang? = entries.firstOrNull { it.tag == tag }

            /**
             * 系统传下来的语言码可能是 ISO3（zho/chi/eng/jpn）也可能是 ISO2（zh/en/ja），
             * 还有历史遗留的 chi / jpn 变体，统一在这里归一。
             */
            fun fromLanguageCode(code: String?): Lang? {
                val normalized = code?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
                return when (normalized) {
                    "zho", "chi", "zh", "cmn", "yue" -> ZH
                    "eng", "en" -> EN
                    "jpn", "ja", "jp" -> JP
                    else -> null
                }
            }
        }
    }

    const val DEFAULT_LENGTH_SCALE = 1.0f
    const val MIN_LENGTH_SCALE = 0.5f
    const val MAX_LENGTH_SCALE = 2.0f

    private const val PREF_NAME = "bv2_tts_engine"
    private const val KEY_SPEAKER_PREFIX = "default_speaker_"
    private const val KEY_LENGTH_SCALE = "base_length_scale"
    private const val KEY_LAST_LANG = "last_loaded_lang"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getDefaultSpeaker(context: Context, lang: Lang): String? =
        prefs(context).getString(KEY_SPEAKER_PREFIX + lang.name, null)

    fun setDefaultSpeaker(context: Context, lang: Lang, spkName: String) {
        prefs(context).edit().putString(KEY_SPEAKER_PREFIX + lang.name, spkName).apply()
    }

    fun getBaseLengthScale(context: Context): Float =
        prefs(context).getFloat(KEY_LENGTH_SCALE, DEFAULT_LENGTH_SCALE)
            .coerceIn(MIN_LENGTH_SCALE, MAX_LENGTH_SCALE)

    fun setBaseLengthScale(context: Context, scale: Float) {
        prefs(context).edit()
            .putFloat(KEY_LENGTH_SCALE, scale.coerceIn(MIN_LENGTH_SCALE, MAX_LENGTH_SCALE))
            .apply()
    }

    /** onLoadLanguage 记下来的语言，供 onGetLanguage 回报 */
    fun getLastLang(context: Context): Lang =
        Lang.fromTag(prefs(context).getString(KEY_LAST_LANG, null)) ?: Lang.ZH

    fun setLastLang(context: Context, lang: Lang) {
        prefs(context).edit().putString(KEY_LAST_LANG, lang.tag).apply()
    }
}
