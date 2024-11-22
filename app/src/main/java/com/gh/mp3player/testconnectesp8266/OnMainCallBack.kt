package com.gh.mp3player.testconnectesp8266

import java.util.Objects

interface OnMainCallBack {
    fun showFragment(tag:String, data: Objects?, isBack:Boolean,viewID:Int)
}