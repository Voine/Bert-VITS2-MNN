package com.example.cpptokenizer

class CppTokenizerJNI {

    /**
     * A native method that is implemented by the 'cpptokenizer' native library,
     * which is packaged with this application.
     */
    external fun initTokenizer(jsonPath: String): Boolean
    external fun encodeText(input: String): IntArray
    companion object {
        // Used to load the 'cpptokenizer' library on application startup.
        init {
            System.loadLibrary("cpptokenizer")
        }
    }
}