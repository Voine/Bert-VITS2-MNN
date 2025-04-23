#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include "tokenizers_cpp.h"

using namespace tokenizers;

static std::shared_ptr<Tokenizer> global_tokenizer = nullptr;

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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_cpptokenizer_CppTokenizerJNI_initTokenizer(JNIEnv *env, jobject thiz, jstring jJsonPath) {
    const char *jsonPath = env->GetStringUTFChars(jJsonPath, nullptr);
    std::string jsonBlob = LoadBytesFromFile(jsonPath);
    env->ReleaseStringUTFChars(jJsonPath, jsonPath);

    global_tokenizer = Tokenizer::FromBlobJSON(jsonBlob);
    return global_tokenizer != nullptr;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_cpptokenizer_CppTokenizerJNI_encodeText(JNIEnv *env, jobject thiz, jstring jInputText) {
    if (global_tokenizer == nullptr) {
        return nullptr;
    }

    const char *inputText = env->GetStringUTFChars(jInputText, nullptr);
    std::vector<int> ids = global_tokenizer->Encode(std::string(inputText));
    env->ReleaseStringUTFChars(jInputText, inputText);

    jintArray result = env->NewIntArray((int)ids.size());
    env->SetIntArrayRegion(result, 0, (int)ids.size(), reinterpret_cast<const jint*>(ids.data()));
    return result;
}
