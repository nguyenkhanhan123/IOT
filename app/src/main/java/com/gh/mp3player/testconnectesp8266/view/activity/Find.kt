package com.gh.mp3player.testconnectesp8266.view.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.gh.mp3player.testconnectesp8266.CommonUtils
import com.gh.mp3player.testconnectesp8266.R
import com.gh.mp3player.testconnectesp8266.databinding.ListGardenBinding
import com.gh.mp3player.testconnectesp8266.model.Garden
import com.gh.mp3player.testconnectesp8266.model.GardenAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage

class Find : BaseActivity<ListGardenBinding>() {
    private var client: MqttAndroidClient? = null
    private val brokerUrl = "tcp://192.168.27.82:1883"

    private var listval = mutableListOf<Garden>()
    private lateinit var gardenAdapter: GardenAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoroutineScope(Dispatchers.Main).launch {
            connectToMqttBroker()
        }
    }

    private suspend fun connectToMqttBroker() = withContext(Dispatchers.IO) {

        val clientId = MqttClient.generateClientId()

        client = MqttAndroidClient(this@Find.applicationContext, brokerUrl, clientId)

        val options = MqttConnectOptions().apply {
            userName = intent.getStringExtra("User")
            password = intent.getStringExtra("Pass")!!.toCharArray()
        }

        try {
            client?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Log.i("MQTT", "Connected successfully")
                        mbinding.wifi?.setImageResource(R.drawable.greenwifi)
                        subscribeToTopics()
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Log.i("MQTT", "Failed to connect $exception")
                        mbinding.wifi?.setImageResource(R.drawable.wifi)
                    }
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }


    private fun subscribeToTopics() {
        CoroutineScope(Dispatchers.IO).launch {
            subscribeToTopicSensor()
            callbackToTopics()
        }
    }

    private suspend fun subscribeToTopicSensor() = withContext(Dispatchers.IO) {
        try {
            val s = CommonUtils.getInstance().getPref("NOTIFY").toString()
            if (s != "") {
                val result = s.substring(1).split("-")
                for (i in result) {
                    client?.subscribe("$i/online", 1, null, object : IMqttActionListener {
                        override fun onSuccess(asyncActionToken: IMqttToken) {
                            Log.i("MQTT Sensor", "Subscribed to topic: $i")
                        }

                        override fun onFailure(
                            asyncActionToken: IMqttToken, exception: Throwable
                        ) {
                            Log.i("MQTT Sensor", "Failed to subscribe to topic: $i")
                        }
                    })
                }
            }

        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    private suspend fun callbackToTopics() = withContext(Dispatchers.IO) {
        try {
            client?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable) {
                    Log.i("MQTT Sensor", "Connection lost")
                }

                override fun messageArrived(topic: String, message: MqttMessage) {

                    val payload = String(message.payload)

                    Log.i("MQTT", "Nhận tin nhắn: $payload từ chủ đề $topic")

                    val result = topic.split("/")

                    for (i in listval.indices) {
                        if (listval[i].name == result[0]) {
                            if (payload == "online") {
                                listval[i].status = true
                                show(listval)
                            } else {
                                listval[i].status = false
                                show(listval)
                            }

                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken) {
                    Log.i("MQTT Sensor", "Message delivered")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    override fun initView() {
        adapter()

        mbinding.find.setOnClickListener {
            if (CommonUtils.getInstance().getPref("NOTIFY").toString()
                    .contains(mbinding.textfind.text)
            ) {
                Toast.makeText(this, "Đã tồn tại", Toast.LENGTH_SHORT).show()
            } else {
                CommonUtils.getInstance().savePref(
                    "NOTIFY", "${
                        CommonUtils.getInstance().getPref("NOTIFY").toString()
                    }-${mbinding.textfind.text}"
                )
            }

            adapter()

            CoroutineScope(Dispatchers.IO).launch {
                subscribeToTopicSensor()
            }
        }
    }

    private fun adapter() {
        val s = CommonUtils.getInstance().getPref("NOTIFY").toString()
        if (s != "") {
            val result = s.substring(1).split("-")
            listval.clear()
            for (i in result.indices) {
                listval.add(Garden(result[i], false))
            }
            if (result.isNotEmpty()) {
                show(listval)
            }
        }
    }

    private fun show(listval: MutableList<Garden>) {
        val event = View.OnClickListener { view ->
            val gardenName = view.tag as? String
            val originalString = CommonUtils.getInstance().getPref("NOTIFY").toString()
            val result = originalString.replace("-$gardenName", "")
            CommonUtils.getInstance().savePref(
                "NOTIFY", result
            )
            adapter()
        }

        val event2 = View.OnClickListener { view ->
            val gardenName = view.tag as? String
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("User", intent.getStringExtra("User"))
                putExtra("Pass", intent.getStringExtra("Pass"))
                putExtra("Garden",gardenName)
            }
            startActivity(intent)
            finish()
        }

        mbinding.rv.setAdapter(
            GardenAdapter(
                this.listval, this, event, event2
            )
        )
    }

    override fun initViewBinding(): ListGardenBinding {
        return ListGardenBinding.inflate(layoutInflater)
    }
}