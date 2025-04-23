package com.example.cppjieba

class CppJiebaJNI {

    /**
     * A native method that is implemented by the 'cppjieba' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String


    external fun initJieba(
        dict: String,
        hmm: String,
        user: String,
        idf: String,
        stop: String
    ): Boolean

    external fun cut(text: String): Array<String>

    external fun tag(text: String): Array<WordTag>

    companion object {
        // Used to load the 'cppjieba' library on application startup.
        init {
            System.loadLibrary("cppjieba")
        }
    }
}

data class WordTag(val word: String, val tag: String)
