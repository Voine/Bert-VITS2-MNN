package com.example.bertvits2

class BertVITS2JNI {

    external fun initBertVITS2Loader()

    external fun setBertVITS2ModelPath(
        enc_model_path: String,
        dec_model_path: String,
        sdp_model_path: String,
        dp_model_path: String,
        emb_model_path: String,
        flow_model_path: String,
        bert_model_path: String,
    )

    external fun destroyBertVITS2Loader()

    external fun startAudioInfer(
        input_seq: IntArray,
        input_t: IntArray,
        input_language: IntArray,
        input_ids: IntArray,
        input_word2ph: IntArray,
        attention_mask: IntArray,
        spkid: Int
    ): FloatArray?

    external fun setAudioLengthScale(length_scale: Float)

    companion object {
        // Used to load the 'bertvits2' library on application startup.
        init {
            System.loadLibrary("MNN_Vulkan")
            System.loadLibrary("MNN_Express")
            System.loadLibrary("MNN")
            System.loadLibrary("bertvits2")
        }
    }
}