#include <jni.h>
#include <string>
#include "bert_vits2_loader.hpp"

extern "C" JNIEXPORT void JNICALL
Java_com_example_bertvits2_BertVITS2JNI_initBertVITS2Loader(JNIEnv *env,
                                                            jobject /* this */) {
    MNN_BERT_VITS2::init_vits_loader();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_bertvits2_BertVITS2JNI_setBertVITS2ModelPath(JNIEnv *env, jobject thiz,
                                                              jstring enc_model_path,
                                                              jstring dec_model_path,
                                                              jstring sdp_model_path,
                                                              jstring dp_model_path,
                                                              jstring emb_model_path,
                                                              jstring flow_model_path,
                                                              jstring bert_model_path) {
    const char* encPathCStr = env->GetStringUTFChars(enc_model_path, nullptr);
    const char* decPathCStr = env->GetStringUTFChars(dec_model_path, nullptr);
    const char* sdpPathCStr = env->GetStringUTFChars(sdp_model_path, nullptr);
    const char* dpPathCStr = env->GetStringUTFChars(dp_model_path, nullptr);
    const char* embPathCStr = env->GetStringUTFChars(emb_model_path, nullptr);
    const char* flowPathCStr = env->GetStringUTFChars(flow_model_path, nullptr);
    const char* bertPathCStr = env->GetStringUTFChars(bert_model_path, nullptr);
    MNN_BERT_VITS2::set_vits_model_path(encPathCStr, decPathCStr, sdpPathCStr, dpPathCStr,
                                        embPathCStr, flowPathCStr, bertPathCStr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_bertvits2_BertVITS2JNI_destroyBertVITS2Loader(JNIEnv *env, jobject thiz) {
    MNN_BERT_VITS2::destroy_vits_loader();
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_bertvits2_BertVITS2JNI_startAudioInfer(JNIEnv *env, jobject thiz,
                                                        jintArray input_seq,
                                                        jintArray input_t,
                                                        jintArray input_language,
                                                        jintArray input_ids,
                                                        jintArray input_word2ph,
                                                        jintArray attention_mask,
                                                        jint spkid) {
    jsize input_seq_length = env->GetArrayLength(input_seq);
    jsize input_t_length = env->GetArrayLength(input_t);
    jsize input_language_length = env->GetArrayLength(input_language);
    jsize input_ids_length = env->GetArrayLength(input_ids);
    jsize input_word2ph_length = env->GetArrayLength(input_word2ph);
    jsize attention_mask_length = env->GetArrayLength(attention_mask);

    jint* input_seq_elements = env->GetIntArrayElements(input_seq, nullptr);
    jint* input_t_elements = env->GetIntArrayElements(input_t, nullptr);
    jint* input_language_elements = env->GetIntArrayElements(input_language, nullptr);
    jint* input_ids_elements = env->GetIntArrayElements(input_ids, nullptr);
    jint* attention_mask_elements = env->GetIntArrayElements(attention_mask, nullptr);
    jint* input_word2ph_elements = env->GetIntArrayElements(input_word2ph, nullptr);

    std::vector<int> input_seq_vector(input_seq_elements, input_seq_elements + input_seq_length);
    std::vector<int> input_t_vector(input_t_elements, input_t_elements + input_t_length);
    std::vector<int> input_language_vector(input_language_elements, input_language_elements + input_language_length);
    std::vector<int> input_ids_vector(input_ids_elements, input_ids_elements + input_ids_length);
    std::vector<int> input_word2ph_vector(input_word2ph_elements, input_word2ph_elements + input_word2ph_length);
    std::vector<int> attention_mask_vector(attention_mask_elements, attention_mask_elements + attention_mask_length);

    std::vector<float> audio = MNN_BERT_VITS2::start_audio_infer(input_seq_vector,
                                                                 input_t_vector,
                                                                 input_language_vector,
                                                                 input_ids_vector,
                                                                 input_word2ph_vector,
                                                                 attention_mask_vector,
                                                                 spkid);

    env->ReleaseIntArrayElements(input_seq, input_seq_elements, 0);
    env->ReleaseIntArrayElements(input_t, input_t_elements, 0);
    env->ReleaseIntArrayElements(input_language, input_language_elements, 0);
    env->ReleaseIntArrayElements(input_ids, input_ids_elements, 0);
    env->ReleaseIntArrayElements(attention_mask, attention_mask_elements, 0);
    env->ReleaseIntArrayElements(input_word2ph, input_word2ph_elements, 0);
    jfloatArray jResult = env->NewFloatArray(static_cast<jsize>(audio.size()));
    if (jResult == nullptr) {
        return nullptr; // Out of memory error thrown
    }
    env->SetFloatArrayRegion(jResult, 0, static_cast<jsize>(audio.size()), audio.data());
    return jResult;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_bertvits2_BertVITS2JNI_setAudioLengthScale(JNIEnv *env, jobject thiz,
                                                            jfloat length_scale) {
    MNN_BERT_VITS2::set_length_scale(length_scale);
}