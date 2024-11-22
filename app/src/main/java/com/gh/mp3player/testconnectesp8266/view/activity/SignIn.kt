package com.gh.mp3player.testconnectesp8266.view.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.gh.mp3player.testconnectesp8266.databinding.SigninBinding
import com.gh.mp3player.testconnectesp8266.view.service.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SignIn : BaseActivity<SigninBinding>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stopService(Intent(this, NotificationService::class.java))
    }
    override fun initView() {
        mbinding.btSignin.setOnClickListener {
            if (mbinding.edtEmail.text.toString() == "An" && mbinding.edtPass.text.toString() == "123") {
                val intent = Intent(this, Find::class.java).apply {
                    putExtra("User", mbinding.edtEmail.text.toString())
                    putExtra("Pass", mbinding.edtPass.text.toString())
                }
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Tài khoản hoặc mật khẩu không chính xác!", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    }

    override fun initViewBinding(): SigninBinding {
        return SigninBinding.inflate(layoutInflater)
    }
}