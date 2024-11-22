package com.gh.mp3player.testconnectesp8266

import android.app.Application

class App : Application() {
    private val dataList = mutableListOf<List<String>>()
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: App
        fun getInstance(): App {
            return instance
        }
    }
    fun addDataToList(data: List<String>) {
        if (dataList.size >= 25) {
            dataList.removeAt(0)
        }
        dataList.add(data)
    }

    fun getDataList(): List<List<String>> {
        return dataList
    }
}
