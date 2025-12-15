package com.example.bertvits2_infer_wrapper.impl

import android.content.Context
import android.util.Log
import com.example.bertvits2_infer_wrapper.interfaces.IBertVITS2FullInfer
import com.example.bertvits2_infer_wrapper.interfaces.IBertVITS2SimpleInfer
import com.example.bertvits2_infer_wrapper.utils.copyAssets2Local
import com.example.bertvits2_infer_wrapper.utils.safeResume
import com.example.textpreprocess.preprocess.LANGUAGE_EN
import com.example.textpreprocess.preprocess.LANGUAGE_JP
import com.example.textpreprocess.preprocess.LANGUAGE_MIX_ZH_EN
import com.example.textpreprocess.preprocess.LANGUAGE_ZH
import com.example.textpreprocess.mix.DetectedLanguage
import com.example.textpreprocess.mix.detectLanguage
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.TreeMap

/**
 * Author: Voine
 * Date: 2025/12/9
 * Description:
 */
class BertVITS2SimpleInferImpl(val context: Context): IBertVITS2SimpleInfer {
    private val bertVITS2FullInfer: IBertVITS2FullInfer by lazy {
        BertVITS2FullInferImpl(context)
    }
    // name <-> (id-modelDirName)
    private val nameListCache: MutableMap<String, String> = TreeMap()
    // modelDirName -> sampleRate
    private val speakerSampleRateCache: MutableMap<String, Int> = TreeMap()

    override suspend fun init(): Boolean {
        val bertResult = suspendCancellableCoroutine {
            context.copyAssets2Local(
                true,
                "bert",
                context.filesDir.absolutePath
            ) { isSuccess: Boolean, absPath: String ->
                Log.i("copyAssets2Local", "isSuccess: $isSuccess, absPath: $absPath")
                it.safeResume(absPath)
            }
        }
        if (bertResult.isEmpty()) {
            Log.e("BertVITS2SimpleInferImpl", "Failed to copy bert assets")
            return false
        }
        val bv2ModelResult = suspendCancellableCoroutine {
            context.copyAssets2Local(
                true,
                "bv2_model",
                context.filesDir.absolutePath
            ) { isSuccess: Boolean, absPath: String ->
                Log.i("copyAssets2Local", "isSuccess: $isSuccess, absPath: $absPath")
                it.safeResume(absPath)
            }
        }
        if (bv2ModelResult.isEmpty()) {
            Log.e("BertVITS2SimpleInferImpl", "Failed to copy bv2_model assets")
            return false
        }

        File(bv2ModelResult).walkTopDown().forEach {
            if (it.isFile && it.name == "config.json") {
                val configJson = it
                val gson = Gson()
                val root = gson.fromJson(configJson.readText(), JsonObject::class.java);
                val spk2idJson = root
                    .getAsJsonObject("data")
                    .getAsJsonObject("spk2id");
                val sampleRate = root
                    .getAsJsonObject("data")
                    .getAsJsonPrimitive("sampling_rate");
                // 转为 Map<String, Integer>
                val type = object : TypeToken<Map<String, Int>>() {}.type
                val spk2idMap: Map<String, Int> = gson.fromJson(spk2idJson, type)
                if (it.parentFile!!.name == "zh") {
                    spk2idMap.forEach { (name, spkId) ->
                        nameListCache[name] = "${spkId}-zh"
                        speakerSampleRateCache[name] = sampleRate.asInt
                    }
                } else if (it.parentFile!!.name == "en") {
                    spk2idMap.forEach { (name, spkId) ->
                        nameListCache[name] = "${spkId}-en"
                        speakerSampleRateCache[name] = sampleRate.asInt
                    }
                } else if (it.parentFile!!.name == "jp") {
                    spk2idMap.forEach { (name, spkId) ->
                        nameListCache[name] = "${spkId}-jp"
                        speakerSampleRateCache[name] = sampleRate.asInt
                    }
                } else if (it.parentFile!!.name == "mix") {
                    spk2idMap.forEach { (name, spkId) ->
                        nameListCache[name] = "${spkId}-mix"
                        speakerSampleRateCache[name] = sampleRate.asInt
                    }
                }
            }
        }
        bertVITS2FullInfer.initBertVITS2Loader()
        if (!bertVITS2FullInfer.initPreprocessor()) {
            Log.e("BertVITS2SimpleInferImpl", "Failed to init preprocessor")
            return false
        }
        return true
    }

    override fun getSpkNameList(): List<String> {
        return nameListCache.keys.toList()
    }

    override suspend fun infer(text: String, spkName: String): Pair<FloatArray?, Int>? {
        val declaredLanguage = getLanguageTypeFromSpkName(spkName)

        // 对于 MIX 类型的 speaker，先检测输入文本的实际语言
        // 纯中文 → 走 ZH 流程（有 BERT）；纯英文 → 走 EN 流程（有 BERT）；混合 → 走 MIX 流程（BERT=0）
        val actualLanguage = if (declaredLanguage == LANGUAGE_MIX_ZH_EN) {
            when (detectLanguage(text)) {
                DetectedLanguage.PURE_ZH -> {
                    Log.i("BertVITS2SimpleInferImpl", "MIX speaker detected pure ZH input, routing to ZH pipeline")
                    LANGUAGE_ZH
                }
                DetectedLanguage.PURE_EN -> {
                    Log.i("BertVITS2SimpleInferImpl", "MIX speaker detected pure EN input, routing to EN pipeline")
                    LANGUAGE_EN
                }
                DetectedLanguage.MIX_ZH_EN -> {
                    Log.i("BertVITS2SimpleInferImpl", "MIX speaker detected mixed ZH+EN input, using MIX pipeline")
                    LANGUAGE_MIX_ZH_EN
                }
            }
        } else {
            declaredLanguage
        }

        setInternalModelPath(spkName, actualLanguage)
        val preprocessResult = bertVITS2FullInfer.preprocess(
            text,
            language = actualLanguage
        ) ?: return Log.e("BertVITS2SimpleInferImpl", "Preprocess failed for text: $text").let { null }
        if (preprocessResult.errorMsg?.isNotEmpty() == true) {
            Log.e("BertVITS2SimpleInferImpl", "Preprocess error: ${preprocessResult.errorMsg}")
            return null
        }
        val arr = bertVITS2FullInfer.startAudioInfer(preprocessResult, nameListCache[spkName]!!.split("-")[0].toInt())
        return Pair(arr, speakerSampleRateCache[spkName] ?: throw IllegalArgumentException("Sample rate not found for spkName: $spkName"))
    }

    override fun setAudioLengthScale(length_scale: Float) {
        bertVITS2FullInfer.setAudioLengthScale(length_scale)
    }

    override fun release() {
        bertVITS2FullInfer.destroyBertVITS2Loader()
    }

    private fun setInternalModelPath(spkName: String, actualLanguage: Int = getLanguageTypeFromSpkName(spkName)) {
        val modelDir = nameListCache[spkName]!!.split("-")[1]
        val basePath = "${context.filesDir.absolutePath}/bv2_model/$modelDir"
        var encPath = ""
        var decPath = ""
        var flowPath = ""
        var embPath = ""
        var dpPath = ""
        var sdpPath = ""
        File(basePath).walkTopDown().forEach {
            if (it.isFile) {
                when {
                    it.name.endsWith("_enc.mnn") -> encPath = it.absolutePath
                    it.name.endsWith("_dec.mnn") -> decPath = it.absolutePath
                    it.name.endsWith("_flow.mnn") -> flowPath = it.absolutePath
                    it.name.endsWith("_emb.mnn") -> embPath = it.absolutePath
                    it.name.endsWith("_dp.mnn") -> dpPath = it.absolutePath
                    it.name.endsWith("_sdp.mnn") -> sdpPath = it.absolutePath
                }
            }
        }
        val bertModelPath = when (actualLanguage) {
            LANGUAGE_ZH -> "${context.filesDir.absolutePath}/bert/zh/chinese-roberta-wwm-ext-large-distilled-fp16.mnn"
            LANGUAGE_EN -> "${context.filesDir.absolutePath}/bert/en/deberta-v3-large-distilled.mnn"
            LANGUAGE_JP -> "${context.filesDir.absolutePath}/bert/jp/deberta-v2-large-japanese-char-wwm-distilled.mnn"
            LANGUAGE_MIX_ZH_EN -> ""
            else -> throw IllegalArgumentException("Unsupported language type")
        }
        bertVITS2FullInfer.setBertVITS2ModelPath(
            enc_model_path = encPath,
            dec_model_path = decPath,
            sdp_model_path = sdpPath,
            dp_model_path = dpPath,
            emb_model_path = embPath,
            flow_model_path = flowPath,
            bert_model_path = bertModelPath,
        )
    }

    private fun getLanguageTypeFromSpkName(spkName: String): Int {
        return if (spkName.endsWith("_ZH")) {
            LANGUAGE_ZH
        } else if(spkName.endsWith("_EN")){
            LANGUAGE_EN
        } else if(spkName.endsWith("_MIX")){
            LANGUAGE_MIX_ZH_EN
        } else if(spkName.endsWith("_JP")){
            LANGUAGE_JP
        } else {
            throw IllegalArgumentException("Unsupported spkName suffix: $spkName")
        }
    }
}