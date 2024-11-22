package com.gh.mp3player.testconnectesp8266

import android.content.Context

class CommonUtils private constructor() {
    companion object {
        private var instance: CommonUtils? = null

        fun getInstance(): CommonUtils {
            return instance ?: synchronized(this) {
                instance ?: CommonUtils().also { instance = it }
            }
        }
    }

    fun savePref(key: String, value: String) {
        val pre = App.getInstance().getSharedPreferences("An",Context.MODE_PRIVATE)
        with(pre.edit()){
            putString(key,value)
            apply()
        }
    }

    fun getPref(key: String): String? {
        val pre = App.getInstance().getSharedPreferences("An", Context.MODE_PRIVATE)
        return pre.getString(key,"")
    }

    fun clearPref(key: String) {
        val pre = App.getInstance().getSharedPreferences("An", Context.MODE_PRIVATE)
        pre.edit().remove(key).apply()
    }
}
