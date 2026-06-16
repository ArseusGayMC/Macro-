package com.ggmacro.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MacroApplication : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}
