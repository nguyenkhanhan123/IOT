package com.gh.mp3player.testconnectesp8266.view.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gh.mp3player.testconnectesp8266.NotificationReceiver
import com.gh.mp3player.testconnectesp8266.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage

class NotificationService : Service() {

    private var client: MqttAndroidClient? = null
    private val brokerUrl = "tcp://192.168.27.82:1883"
    private val topicSensor = "home/sensors"

    private val CHANNEL_ID = "MQTT_SERVICE_CHANNEL"
    private val CHANNEL_ID2 = "EMERGENCY"

    private var isNotify = 0

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannels()
        }

        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("MQTT Service")
                .setContentText("Connecting to MQTT broker...").setOnlyAlertOnce(true)
                .setSmallIcon(android.R.drawable.ic_notification_overlay).build()

        startForeground(1, notification)

        connectToMqttBroker()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun connectToMqttBroker() {
        val clientId = MqttClient.generateClientId()
        client = MqttAndroidClient(applicationContext, brokerUrl, clientId)

        val options = MqttConnectOptions().apply {
            userName = "An"
            password = "123".toCharArray()
        }

        client?.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken) {
                Log.i("MQTT", "Connected successfully")
                subscribeToTopic()
            }

            override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                Log.e("MQTT", "Failed to connect: $exception")
                connectToMqttBroker()
            }
        })
    }

    private fun subscribeToTopic() {
        try {
            client?.subscribe(topicSensor, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.i("MQTT", "Subscribed to topic: $topicSensor")
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.e("MQTT", "Failed to subscribe to topic: $topicSensor")
                }
            })

            client?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.i("MQTT", "Connection lost: $cause")
                    connectToMqttBroker()
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    val payload = String(message.payload)
                    if (topic == topicSensor) {
                        processSensorData(payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken) {
                    Log.i("MQTT", "Message delivered")
                }
            })

        } catch (e: MqttException) {
            Log.e("MQTT", "Exception during subscription: ${e.message}", e)
        }
    }

    private fun processSensorData(payload: String) {
        val dataParts = payload.split("-")
        if (dataParts.size == 4) {
            val (temperature, humidity, soilMoisture, light) = dataParts
            saveToFirebase(temperature, humidity, soilMoisture, light)
            showNotification(temperature, humidity, soilMoisture, light)
        } else {
            Log.i("MQTT", "Invalid sensor data: $payload")
        }
    }

    private fun showNotification(
        temperature: String,
        humidity: String,
        soilMoisture: String,
        light: String
    ) {

        val content = """
            Temperature: $temperature
            Humidity: $humidity
            Soil Moisture: $soilMoisture
            Light: $light
        """.trimIndent()

        // Intent để xử lý hành động tắt thông báo
        val dismissIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = "DISMISS_NOTIFICATION"
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder =
            NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Sensor Data Update")
                .setContentText("Xem thông tin chi tiết...")
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setSmallIcon(android.R.drawable.ic_notification_overlay)
                .setPriority(NotificationCompat.PRIORITY_HIGH).addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Tắt",
                    dismissPendingIntent
                ).setAutoCancel(true).setOngoing(false).setOnlyAlertOnce(true)

        if (isNotify== 0){

        }
        if (temperature.toFloat() > 40 || humidity.toFloat() > 80) {
            if (isNotify== 0){
                isNotify=1
                val notificationBuilder2 =
                    NotificationCompat.Builder(this, CHANNEL_ID2).setContentTitle("WARNING")
                        .setContentText("Xem thông tin chi tiết...")
                        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                        .setSmallIcon(android.R.drawable.ic_notification_overlay)
                        .setPriority(NotificationCompat.PRIORITY_HIGH).addAction(
                            android.R.drawable.ic_menu_close_clear_cancel,
                            "Tắt",
                            dismissPendingIntent
                        ).setAutoCancel(true).setOngoing(false)
                        .setColor(resources.getColor(android.R.color.holo_red_dark))
                val notification2 = notificationBuilder2.build()
                val notificationManager2 = getSystemService(NotificationManager::class.java)
                notificationManager2?.notify(2, notification2)
            }
        }
        else{
            isNotify=0
        }


        val notification = notificationBuilder.build()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(1, notification)
    }

    private fun saveToFirebase(
        temperature: String,
        humidity: String,
        soilMoisture: String,
        light: String
    ) {
        val data = hashMapOf(
            "temperature" to temperature,
            "humidity" to humidity,
            "soilMoisture" to soilMoisture,
            "light" to light,
            "time" to Timestamp.now()
        )

        FirebaseFirestore.getInstance().collection("DATA").add(data)
            .addOnSuccessListener { documentReference ->
                Log.i("Firebase", "Data saved successfully: $documentReference")
            }.addOnFailureListener { e ->
                Log.e("Firebase", "Error saving data", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            client?.disconnect()
            Log.i("MQTT", "Disconnected from broker")
        } catch (e: MqttException) {
            Log.e("MQTT", "Error disconnecting: ${e.message}", e)
        } finally {
            client?.unregisterResources()
        }
    }

    private fun createNotificationChannels() {
        val sound: Uri = Uri.parse("android.resource://$packageName/${R.raw.notification}")
        val attributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()

        val channel1 = NotificationChannel(
            CHANNEL_ID,
            "MQTT Service",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Service for connecting to MQTT broker"
        }

        val channel2 = NotificationChannel(
            CHANNEL_ID2,
            "Emergency Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for emergency situations"
            setSound(sound, attributes)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel1)
        notificationManager?.createNotificationChannel(channel2)
    }
}
