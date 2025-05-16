package com.example.bertvits2mnn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.textpreprocess.jp.JapaneseTextPreprocessor

class MainActivity : ComponentActivity() {


    private val viewModel: VoiceViewModel by lazy {
        ViewModelProvider(this)[VoiceViewModel::class.java]
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceGenerationScreen(viewModel)
        }
        viewModel.init(this)
    }
}
