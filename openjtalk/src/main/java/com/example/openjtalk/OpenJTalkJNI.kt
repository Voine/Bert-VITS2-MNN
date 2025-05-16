package com.example.openjtalk

import android.content.res.AssetManager

class OpenJTalkJNI {

    external fun initOpenJtalk(dicPath: String, assetManager: AssetManager): Boolean

    /**
     * @return sep, res, get_accent(parsed)
     */
    external fun text2sep_kata(text: String): Triple<Array<String>, Array<String>, Array<Accent>>

    external fun clearOpenJtalk()

    /**
     * return features, labels
     */
    external fun run_frontend_make_label(
        text: String,
    ): Pair<Array<Feature>, Array<String>>

    companion object {
        // Used to load the 'openjtalk' library on application startup.
        init {
            System.loadLibrary("openjtalk")
        }
    }
}

data class Accent(
    var phonemes: String,
    var accents: Int,
)

data class Feature(
    var string: String,
    var pron: String,
)