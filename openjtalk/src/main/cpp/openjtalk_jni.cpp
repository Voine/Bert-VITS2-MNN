#include <jni.h>
#include <string>
#include "openjtalk/api/api.h"


static OpenJtalk openJtalk;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_openjtalk_OpenJTalkJNI_initOpenJtalk(JNIEnv *env, jobject thiz, jstring dic_path, jobject asset_manager) {
    AssetJNI assetJni(env, thiz, asset_manager);
    bool state = openJtalk.init("open_jtalk_dic_utf_8-1.11", &assetJni);
    if (state) return JNI_TRUE;
    else return JNI_FALSE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_openjtalk_OpenJTalkJNI_text2sep_1kata(JNIEnv *env, jobject thiz, jstring text) {
    char *ctext = (char *) env->GetStringUTFChars(text, nullptr);
    // 转换编码
    string stext(ctext);
    wstring wtext = utf8_decode(stext);
    auto features = openJtalk.run_frontend(wtext);
    auto labels = openJtalk.make_label(features);

    std::string phonemes = "アイウエオ";
    int accents = 3;
    std::vector<std::string> first_array = {"hello", "world"};
    std::vector<std::string> second_array = {"こんにちは", "世界"};

    // String array
    jclass stringClass = env->FindClass("java/lang/String");

    jobjectArray jFirstArray = env->NewObjectArray(first_array.size(), stringClass, nullptr);
    for (size_t i = 0; i < first_array.size(); ++i) {
        jstring str = env->NewStringUTF(first_array[i].c_str());
        env->SetObjectArrayElement(jFirstArray, i, str);
        env->DeleteLocalRef(str);
    }

    jobjectArray jSecondArray = env->NewObjectArray(second_array.size(), stringClass, nullptr);
    for (size_t i = 0; i < second_array.size(); ++i) {
        jstring str = env->NewStringUTF(second_array[i].c_str());
        env->SetObjectArrayElement(jSecondArray, i, str);
        env->DeleteLocalRef(str);
    }

    // Accent
    jclass accentClass = env->FindClass("com/example/openjtalk/Accent");
    jmethodID accentCtor = env->GetMethodID(accentClass, "<init>", "(Ljava/lang/String;I)V");
    struct AccentData {
        std::string phonemes;
        int accents;
    };

    std::vector<AccentData> accent_array = {
            {"アイ", 2},
            {"ウエオ", 5},
    };

    jobjectArray jAccentArray = env->NewObjectArray(accent_array.size(), accentClass, nullptr);
    for (size_t i = 0; i < accent_array.size(); ++i) {
        jstring jPhonemes = env->NewStringUTF(accent_array[i].phonemes.c_str());
        jobject accentObj = env->NewObject(accentClass, accentCtor, jPhonemes, accent_array[i].accents);
        env->SetObjectArrayElement(jAccentArray, i, accentObj);

        env->DeleteLocalRef(jPhonemes);
        env->DeleteLocalRef(accentObj);
    }

    // 2. Accent array
    for (size_t i = 0; i < accent_array.size(); ++i) {
        jstring jPhonemes = env->NewStringUTF(accent_array[i].phonemes.c_str());
        jobject accentObj = env->NewObject(accentClass, accentCtor, jPhonemes, accent_array[i].accents);
        env->SetObjectArrayElement(jAccentArray, i, accentObj);

        env->DeleteLocalRef(jPhonemes);
        env->DeleteLocalRef(accentObj);
    }

    // Triple
    jclass tripleClass = env->FindClass("kotlin/Triple");
    jmethodID tripleCtor = env->GetMethodID(tripleClass, "<init>",
                                            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");
    // 3. Triple
    jobject tripleObj = env->NewObject(tripleClass, tripleCtor, jFirstArray, jSecondArray, jAccentArray);
    return tripleObj;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_openjtalk_OpenJTalkJNI_clearOpenJtalk(JNIEnv *env, jobject thiz) {
    openJtalk.clear();
}