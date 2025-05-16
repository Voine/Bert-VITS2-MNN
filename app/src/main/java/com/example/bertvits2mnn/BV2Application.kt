package com.example.bertvits2mnn

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

/**
 * Author: Voine
 * Date: 2025/5/18
 * Description: application
 */
class BV2Application: Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var context: Context
        lateinit var instance: BV2Application
    }

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        instance = this
        // Initialize any global resources or configurations here
    }
}