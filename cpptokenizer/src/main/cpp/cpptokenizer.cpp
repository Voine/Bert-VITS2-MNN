#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <memory>
#include "tokenizers_cpp.h"

using namespace tokenizers;

// 辅助函数：从路径读取 JSON blob
std::string LoadBytesFromFile(const std::string& path) {
    std::ifstream fs(path, std::ios::in | std::ios::binary);
    if (fs.fail()) {
        exit(1);
    }
    std::string data;
    fs.seekg(0, std::ios::end);
    size_t size = static_cast<size_t>(fs.tellg());
    fs.seekg(0, std::ios::beg);
    data.resize(size);
    fs.read(data.data(), size);
    return data;
}

// 把 shared_ptr<Tokenizer>* 当作 jlong 句柄传给 Java 侧，
// 每个 CppTokenizerJNI 实例持有独立的 handle，互不影响。
static inline jlong toHandle(std::shared_ptr<Tokenizer>* p) {
    return reinterpret_cast<jlong>(p);
}
static inline std::shared_ptr<Tokenizer>* fromHandle(jlong h) {
    return reinterpret_cast<std::shared_ptr<Tokenizer>*>(h);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_cpptokenizer_CppTokenizerJNI_nativeInitFromBlobJson(JNIEnv *env, jobject thiz, jstring jJsonPath) {
    const char *jsonPath = env->GetStringUTFChars(jJsonPath, nullptr);
    std::string jsonBlob = LoadBytesFromFile(jsonPath);
    env->ReleaseStringUTFChars(jJsonPath, jsonPath);

    auto tok = Tokenizer::FromBlobJSON(jsonBlob);
    if (tok == nullptr) {
        return 0;
    }
    auto* holder = new std::shared_ptr<Tokenizer>(std::move(tok));
    return toHandle(holder);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_cpptokenizer_CppTokenizerJNI_nativeInitFromBlobSentencePiece(JNIEnv *env, jobject thiz, jstring jModelPath) {
    const char *path = env->GetStringUTFChars(jModelPath, nullptr);
    std::string modelBlob = LoadBytesFromFile(path);
    env->ReleaseStringUTFChars(jModelPath, path);

    auto tok = Tokenizer::FromBlobSentencePiece(modelBlob);
    if (tok == nullptr) {
        return 0;
    }
    auto* holder = new std::shared_ptr<Tokenizer>(std::move(tok));
    return toHandle(holder);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_cpptokenizer_CppTokenizerJNI_nativeRelease(JNIEnv *env, jobject thiz, jlong handle) {
    if (handle == 0) return;
    delete fromHandle(handle);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_cpptokenizer_CppTokenizerJNI_nativeEncodeText(JNIEnv *env, jobject thiz, jlong handle, jstring jInputText) {
    if (handle == 0) {
        return nullptr;
    }
    auto& tokenizer = *fromHandle(handle);
    if (!tokenizer) {
        return nullptr;
    }

    const char *inputText = env->GetStringUTFChars(jInputText, nullptr);
    std::vector<int> ids = tokenizer->Encode(std::string(inputText));
    env->ReleaseStringUTFChars(jInputText, inputText);

    jintArray result = env->NewIntArray((int)ids.size());
    env->SetIntArrayRegion(result, 0, (int)ids.size(), reinterpret_cast<const jint*>(ids.data()));
    return result;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_cpptokenizer_CppTokenizerJNI_nativeTokenizeText(JNIEnv *env, jobject thiz, jlong handle, jstring jInputText) {
    if (handle == 0) {
        return nullptr;
    }
    auto& tokenizer = *fromHandle(handle);
    if (!tokenizer) {
        return nullptr;
    }

    const char *inputText = env->GetStringUTFChars(jInputText, nullptr);
    std::vector<int> ids = tokenizer->Encode(std::string(inputText));
    env->ReleaseStringUTFChars(jInputText, inputText);

    jobjectArray result = env->NewObjectArray((int)ids.size(), env->FindClass("java/lang/String"), nullptr);
    for (int i = 0; i < (int)ids.size(); i++) {
        std::string token = tokenizer->IdToToken(ids[i]);
        env->SetObjectArrayElement(result, i, env->NewStringUTF(token.c_str()));
    }
    return result;
}