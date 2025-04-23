#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include "cppjieba/Jieba.hpp"

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_cppjieba_CppJiebaJNI_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}


static std::unique_ptr<cppjieba::Jieba> jiebaPtr = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_cppjieba_CppJiebaJNI_initJieba(
        JNIEnv *env,
        jobject,
        jstring dictPath_,
        jstring hmmPath_,
        jstring userPath_,
        jstring idfPath_,
        jstring stopPath_
) {
    if (jiebaPtr != nullptr) {
        return JNI_TRUE; // 已初始化
    }

    const char *dictPath = env->GetStringUTFChars(dictPath_, nullptr);
    const char *hmmPath = env->GetStringUTFChars(hmmPath_, nullptr);
    const char *userPath = env->GetStringUTFChars(userPath_, nullptr);
    const char *idfPath = env->GetStringUTFChars(idfPath_, nullptr);
    const char *stopPath = env->GetStringUTFChars(stopPath_, nullptr);

    jiebaPtr = std::make_unique<cppjieba::Jieba>(
            dictPath, hmmPath, userPath, idfPath, stopPath
    );

    env->ReleaseStringUTFChars(dictPath_, dictPath);
    env->ReleaseStringUTFChars(hmmPath_, hmmPath);
    env->ReleaseStringUTFChars(userPath_, userPath);
    env->ReleaseStringUTFChars(idfPath_, idfPath);
    env->ReleaseStringUTFChars(stopPath_, stopPath);

    return JNI_TRUE;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_example_cppjieba_CppJiebaJNI_tag(
        JNIEnv *env,
        jobject,
        jstring inputText_
) {
    if (!jiebaPtr) return nullptr;

    const char *input = env->GetStringUTFChars(inputText_, nullptr);
    std::vector<std::pair<std::string, std::string>> tagResult;
    jiebaPtr->Tag(input, tagResult);
    env->ReleaseStringUTFChars(inputText_, input);

    // 找到 Kotlin/Java 的 WordTag 类
    jclass wordTagClass = env->FindClass("com/example/cppjieba/WordTag");
    jmethodID ctor = env->GetMethodID(wordTagClass, "<init>",
                                      "(Ljava/lang/String;Ljava/lang/String;)V");

    jobjectArray result = env->NewObjectArray(
            tagResult.size(), wordTagClass, nullptr);

    for (size_t i = 0; i < tagResult.size(); ++i) {
        jstring word = env->NewStringUTF(tagResult[i].first.c_str());
        jstring tag = env->NewStringUTF(tagResult[i].second.c_str());

        jobject wordTagObj = env->NewObject(wordTagClass, ctor, word, tag);
        env->SetObjectArrayElement(result, i, wordTagObj);
    }

    return result;
}


extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_example_cppjieba_CppJiebaJNI_cut(
        JNIEnv *env,
        jobject,
        jstring inputText_
) {
    if (!jiebaPtr) {
        return nullptr; // 没有初始化
    }

    const char *input = env->GetStringUTFChars(inputText_, nullptr);
    std::vector<std::string> words;
    jiebaPtr->Cut(input, words, true);
    env->ReleaseStringUTFChars(inputText_, input);

    jobjectArray result = env->NewObjectArray(
            words.size(), env->FindClass("java/lang/String"), nullptr
    );
    for (size_t i = 0; i < words.size(); ++i) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(words[i].c_str()));
    }
    return result;
}
