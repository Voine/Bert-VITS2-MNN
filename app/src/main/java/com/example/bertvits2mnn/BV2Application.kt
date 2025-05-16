package com.example.bertvits2mnn

import android.app.Application

/**
 * Author: Voine
 * Date: 2025/6/5
 * Description:
 */
class BV2Application: Application(){
    companion object {
        lateinit var instance: BV2Application
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize any global resources or configurations here
    }
}