package com.example.bertvits2

interface IBertVITS2JNI {

    fun initBertVITS2Loader()

    fun setBertVITS2ModelPath(
        enc_model_path: String,
        dec_model_path: String,
        sdp_model_path: String,
        dp_model_path: String,
        emb_model_path: String,
        flow_model_path: String,
        bert_model_path: String,
    )

    fun destroyBertVITS2Loader()

    fun startAudioInfer(
        input_seq: IntArray,
        input_t: IntArray,
        input_language: IntArray,
        input_ids: IntArray,
        input_word2ph: IntArray,
        attention_mask: IntArray,
        spkid: Int,
    ): FloatArray?

    fun setAudioLengthScale(length_scale: Float)
}

// Concrete implementation that keeps the native bindings.
// Keep the class name `BertVITS2JNI` so existing JNI C++ functions match the JVM name.
class BertVITS2JNI : IBertVITS2JNI {

    external override fun initBertVITS2Loader()

    external override fun setBertVITS2ModelPath(
        enc_model_path: String,
        dec_model_path: String,
        sdp_model_path: String,
        dp_model_path: String,
        emb_model_path: String,
        flow_model_path: String,
        bert_model_path: String,
    )

    external override fun destroyBertVITS2Loader()

    external override fun startAudioInfer(
        input_seq: IntArray,
        input_t: IntArray,
        input_language: IntArray,
        input_ids: IntArray,
        input_word2ph: IntArray,
        attention_mask: IntArray,
        spkid: Int
    ): FloatArray?

    external override fun setAudioLengthScale(length_scale: Float)

    companion object {
        // Used to load the 'bertvits2' library on application startup.
        init {
            System.loadLibrary("MNN_Express")
            System.loadLibrary("MNN")
            System.loadLibrary("bertvits2")
        }
    }
}