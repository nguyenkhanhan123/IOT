package com.gh.mp3player.testconnectesp8266.view.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.gh.mp3player.testconnectesp8266.App
import com.gh.mp3player.testconnectesp8266.R
import com.gh.mp3player.testconnectesp8266.databinding.ActivityMainBinding
import com.gh.mp3player.testconnectesp8266.view.service.NotificationService
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MainActivity : BaseActivity<ActivityMainBinding>() {
    private var client: MqttAndroidClient? = null
    private val brokerUrl = "tcp://192.168.27.82:1883"
    private val topicSensor = "home/sensors"
    private val topicLed = "home/device/led"
    private val topicPump = "home/device/pump"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoroutineScope(Dispatchers.Main).launch {
            fetchDataFromFirestore()
            connectToMqttBroker()
        }
    }

    private suspend fun fetchDataFromFirestore() = withContext(Dispatchers.IO) {
        try {
            val documents = FirebaseFirestore.getInstance().collection("DATA")
                .orderBy("time", Query.Direction.DESCENDING)
                .limit(25)
                .get()
                .await()

            for (document in documents) {
                val temperature = document.getString("temperature") ?: "-1"
                val humidity = document.getString("humidity") ?: "-1"
                val soilMoisture = document.getString("soilMoisture") ?: "-1"
                val light = document.getString("light") ?: "-1"

                val dataParts = listOf(soilMoisture, temperature, humidity, light)
                App.getInstance().addDataToList(dataParts)
            }
            Log.i("Firebase", "Data list size: ${App.getInstance().getDataList().size}")
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Log.e("Firestore", "Error fetching data: ", e)
                Toast.makeText(this@MainActivity, "Error fetching data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun connectToMqttBroker() = withContext(Dispatchers.IO) {
        val clientId = MqttClient.generateClientId()
        client = MqttAndroidClient(this@MainActivity.applicationContext, brokerUrl, clientId)

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
        }
    }

    override fun initView() {
        mbinding.light.setOnClickListener {
            setupChart()
        }
        mbinding.switchauto.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                mbinding.switchlamp.isEnabled = false
                mbinding.switchpump.isEnabled = false
                sendMqttMessage("home/type", "auto")
            } else {
                mbinding.switchlamp.isEnabled = true
                mbinding.switchpump.isEnabled = true
                sendMqttMessage("home/type", "manual")
            }
        }

        mbinding.switchlamp.setOnCheckedChangeListener { _, isChecked ->
            sendMqttMessage(topicLed, if (isChecked) "onled" else "offled")
        }

        mbinding.switchpump.setOnCheckedChangeListener { _, isChecked ->
            sendMqttMessage(topicPump, if (isChecked) "onpump" else "offpump")
        }

    }

    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    private suspend fun subscribeToTopicSensor() = withContext(Dispatchers.IO) {
        try {
            client?.subscribe(topicSensor, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.i("MQTT Sensor", "Subscribed to topic: $topicSensor")
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.i("MQTT Sensor", "Failed to subscribe to topic: $topicSensor")
                }
            })

            client!!.subscribe(topicLed, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.i("KQ Led", "Đăng ký thành công vào chủ đề $topicLed")
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.i("KQ Led", "Đăng ký thất bại vào chủ đề $topicLed")
                }
            })

            client!!.subscribe(topicPump, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.i("KQ Pump", "Đăng ký thành công vào chủ đề $topicPump")
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.i("KQ Pump", "Đăng ký thất bại vào chủ đề $topicPump")
                }
            })

            client?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable) {
                    Log.i("MQTT Sensor", "Connection lost")
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    val payload = String(message.payload)

                    if (topic==topicSensor){
                        Log.i("MQTT Sensor", "Message arrived from topic $topic: $payload")

                        val dataParts = payload.split("-")
                        if (dataParts.size == 4) {
                            val (temperature, humidity, soilMoisture, light) = dataParts
                            val time = Timestamp.now()

                            CoroutineScope(Dispatchers.Main).launch {

                                mbinding.lighttext.text = if (light == "0") "Sáng" else "Tối"

                                if (temperature=="nan"){
                                    mbinding.temperature.setBackgroundResource(R.drawable.bg_edt1)
                                }
                                else{
                                    mbinding.temperature.setBackgroundResource(R.drawable.bg_edt)
                                }
                                if (soilMoisture=="nan"){
                                    mbinding.soil.setBackgroundResource(R.drawable.bg_edt1)
                                }
                                else{
                                    mbinding.soil.setBackgroundResource(R.drawable.bg_edt)
                                }
                                if (humidity=="nan"){
                                    mbinding.air.setBackgroundResource(R.drawable.bg_edt1)
                                }
                                else{
                                    mbinding.air.setBackgroundResource(R.drawable.bg_edt)
                                }
                                mbinding.temperaturetext.text = "$temperature°C"
                                mbinding.soiltext.text = "$soilMoisture%"
                                mbinding.airtext.text = "$humidity%"

                                FirebaseFirestore.getInstance().collection("DATA").add(
                                    hashMapOf(
                                        "temperature" to temperature,
                                        "humidity" to humidity,
                                        "soilMoisture" to soilMoisture,
                                        "light" to light,
                                        "time" to time
                                    )
                                )
                                App.getInstance().addDataToList(dataParts)
                            }
                        } else {
                            Log.i("MQTT Sensor", "Invalid data: $payload")
                        }
                    }
                    if (topic==topicLed){
                        Log.i("MQTT Led", "Nhận tin nhắn: $payload từ chủ đề $topic")
                        if (payload=="onled") {
                            runOnUiThread {
                                mbinding.switchlamp.isChecked=true
                            }

                        } else {
                            runOnUiThread {
                                mbinding.switchlamp.isChecked=false
                            }
                        }
                    }
                    if (topic==topicPump){
                        Log.i("MQTT Pump", "Nhận tin nhắn: $payload từ chủ đề $topic")
                        if (payload=="onpump") {
                            runOnUiThread {
                                mbinding.switchpump.isChecked=true
                            }

                        } else {
                            runOnUiThread {
                                mbinding.switchpump.isChecked=false
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

    private fun sendMqttMessage(topicType: String, message: String) {
        try {
            val mqttMessage = MqttMessage(message.toByteArray())
            client?.publish(topicType, mqttMessage)
            Log.i("MQTT Send", "Message sent: $message to topic $topicType")
        } catch (e: MqttException) {
            e.printStackTrace()
            Log.e("MQTT Send", "Failed to send message: $message to topic $topicType", e)
        }
    }

    private fun setupChart() {
        val lineChart: LineChart = mbinding.lineChart
        val entries = generateEntriesFromDataList()

        val dataSet = LineDataSet(entries, "").apply {
            color = Color.RED
            valueTextColor = Color.BLACK
            setDrawValues(false)
        }

        val lineData = LineData(dataSet)
        lineChart.data = lineData

        val description = Description().apply {
            text = "Biểu đồ với dữ liệu từ dataList"
        }
        lineChart.description = description

        lineChart.legend.isEnabled = false
        lineChart.invalidate()
    }

    private fun generateEntriesFromDataList(): ArrayList<Entry> {
        val entries = ArrayList<Entry>()
        var i=1
        for ((index, data) in App.getInstance().getDataList().withIndex()) {
            val xValue = i.toFloat()
            i++
            val yValue = data[1].toFloat()
            entries.add(Entry(xValue, yValue))
        }
        return entries
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        startForegroundService(Intent(this, NotificationService::class.java))
        super.onDestroy()
    }
}