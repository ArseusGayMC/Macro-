package com.ggmacro.app.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder

object GsonProvider {
    val gson: Gson = GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .create()
}
