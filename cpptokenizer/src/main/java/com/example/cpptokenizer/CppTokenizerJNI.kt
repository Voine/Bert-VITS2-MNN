package com.example.cpptokenizer

/**
 * 每个实例持有独立的 native handle，避免不同语言（中/英/日）
 * 加载不同 tokenizer 时互相覆盖。
 */
class CppTokenizerJNI {

    @Volatile
    private var nativeHandle: Long = 0L

    fun initTokenizerFromBlobJson(jsonPath: String): Boolean {
        release()
        nativeHandle = nativeInitFromBlobJson(jsonPath)
        return nativeHandle != 0L
    }

    fun initTokenizerFromBlobSentencePiece(modelPath: String): Boolean {
        release()
        nativeHandle = nativeInitFromBlobSentencePiece(modelPath)
        return nativeHandle != 0L
    }

    fun encodeText(input: String): IntArray {
        check(nativeHandle != 0L) { "Tokenizer not initialized" }
        return nativeEncodeText(nativeHandle, input) ?: IntArray(0)
    }

    /**
     * Tokenize text into subword token strings.
     * For SentencePiece, tokens may start with "▁" (U+2581) to indicate word boundaries.
     */
    fun tokenizeText(input: String): Array<String> {
        check(nativeHandle != 0L) { "Tokenizer not initialized" }
        return nativeTokenizeText(nativeHandle, input) ?: emptyArray()
    }

    @Synchronized
    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0L
        }
    }

    protected fun finalize() {
        release()
    }

    private external fun nativeInitFromBlobJson(jsonPath: String): Long
    private external fun nativeInitFromBlobSentencePiece(modelPath: String): Long
    private external fun nativeRelease(handle: Long)
    private external fun nativeEncodeText(handle: Long, input: String): IntArray?
    private external fun nativeTokenizeText(handle: Long, input: String): Array<String>?

    companion object {
        init {
            System.loadLibrary("cpptokenizer")
        }
    }
}