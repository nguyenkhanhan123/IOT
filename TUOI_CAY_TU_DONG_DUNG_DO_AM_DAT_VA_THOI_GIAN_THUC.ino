#include <ESP8266WiFi.h>
#include <PubSubClient.h>
#include <WiFiClientSecure.h>
#include <DHT.h>
#include <math.h>


#define DHTPIN D3
#define DHTTYPE DHT11

const int soilMoisturePin = A0;
const int lightPin = D5;
const int ledPin = D6;
const int pumpPin = D7;

const char* ssid = "HotenNguoiYeuTui";
const char* password = "lamdeogico";

const char* mqtt_server = "192.168.27.82";  
const int mqtt_port = 1883;
const char* mqtt_user = "An"; 
const char* mqtt_pass = "123"; 

DHT dht(DHTPIN, DHTTYPE);
WiFiClient espClient;
PubSubClient client(espClient);

bool isAutoMode = false;
bool manualLEDState = false;
bool manualPumpState = false;

void setup_wifi() {
  delay(10);
  Serial.println();
  Serial.print("Đang kết nối tới WiFi: ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("");
  Serial.println("Đã kết nối WiFi");
  Serial.print("Địa chỉ IP: ");
  Serial.println(WiFi.localIP());
}

void callback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (unsigned int i = 0; i < length; i++) {
    message += (char)payload[i];
  }

  Serial.print("Nhận tin nhắn từ chủ đề: ");
  Serial.println(topic);
  Serial.print("Tin nhắn: ");
  Serial.println(message);

  if (String(topic) == "home/type") {
    if (message == "manual") {
      isAutoMode = false;
      Serial.println("Chuyển sang chế độ Manual");
    } else if (message == "auto") {
      isAutoMode = true;
      Serial.println("Chuyển sang chế độ Auto");
    }
  } else if (String(topic) == "home/device/led") {
    if (!isAutoMode) {
      if (message == "onled") {
        manualLEDState = true;
        digitalWrite(ledPin, HIGH);
        Serial.println("Đèn bật (Manual)");
      } else if (message == "offled") {
        manualLEDState = false;
        digitalWrite(ledPin, LOW);
        Serial.println("Đèn tắt (Manual)");
      }
    }
  } else if (String(topic) == "home/device/pump") {
    if (!isAutoMode) {
      if (message == "onpump") {
        manualPumpState = true;
        digitalWrite(pumpPin, HIGH);
        Serial.println("Máy bơm bật (Manual)");
      } else if (message == "offpump") {
        manualPumpState = false;
        digitalWrite(pumpPin, LOW);
        Serial.println("Máy bơm tắt (Manual)");
      }
    }
  }
}

void reconnect() {
  while (!client.connected()) {
    Serial.print("Đang kết nối MQTT...");
    String clientId = "ESP8266Client-" + String(random(0xffff), HEX);

    // Cấu hình LWT: nếu thiết bị mất kết nối, broker gửi "ESP8266 is offline"
    if (client.connect(clientId.c_str(), mqtt_user, mqtt_pass, "home/online", 0, true, "offline")) {
      Serial.println("Đã kết nối MQTT");
      
      // Gửi tin nhắn thông báo online
      client.publish("home/online", "online");

      // Đăng ký các chủ đề cần thiết
      client.subscribe("home/type");
      client.subscribe("home/device/led");
      client.subscribe("home/device/pump");
    } else {
      Serial.print("Kết nối thất bại, rc=");
      Serial.print(client.state());
      Serial.println(" Thử lại sau 5 giây");
      delay(5000);
    }
  }
}


void setup_esp() {
  pinMode(ledPin, OUTPUT);
  pinMode(pumpPin, OUTPUT);
  pinMode(lightPin, INPUT);
  dht.begin();
}

void setup() {
  Serial.begin(115200);
  setup_wifi();
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);
  setup_esp();

  client.setKeepAlive(5); // 5 giây
}

unsigned long previousMillis = 0; // Biến lưu thời gian trước đó
const long interval = 2000; // Khoảng thời gian giữa các lần cập nhật (2 giây)
unsigned long previousOnlineMillis = 0; // Biến lưu thời gian trước đó để gửi "online"
const long onlineInterval = 10000;      // Gửi "online" mỗi 10 giây

void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();

  // Lấy thời gian hiện tại
  unsigned long currentMillis = millis();

  // Gửi trạng thái "online" mỗi 10 giây
  if (currentMillis - previousOnlineMillis >= onlineInterval) {
    previousOnlineMillis = currentMillis;

    client.publish("home/online", "online", true); // `true` để giữ tin nhắn trong trường hợp mất kết nối
    Serial.println("Gửi trạng thái: online");
  }

  // Chỉ thực hiện khi đủ 2 giây trôi qua
  if (currentMillis - previousMillis >= interval) {
    previousMillis = currentMillis;

    float temperature = dht.readTemperature();
    float humidity = dht.readHumidity();

    // Đọc độ ẩm đất
    int soilRawValue = analogRead(soilMoisturePin);
    float soilMoistureValue = (soilRawValue >= 0 && soilRawValue <= 1023)
                                  ? map(soilRawValue, 0, 1023, 100, 0)
                                  : NAN;

    // Đọc trạng thái ánh sáng
    int lightRawValue = digitalRead(lightPin);
    int lightState = (lightRawValue == HIGH || lightRawValue == LOW)
                           ? lightRawValue
                           : NAN;

    Serial.print("Humidity: ");
    Serial.print(humidity);
    Serial.print("%\t");
    Serial.print("Temperature: ");
    Serial.print(temperature);
    Serial.print("*C\t");
    Serial.print("Soil Moisture: ");
    Serial.print(soilMoistureValue);
    Serial.print("%\t");
    Serial.print("Light State: ");
    if (lightState == HIGH) {
      Serial.println("High");
    } else if (lightState == LOW) {
      Serial.println("Low");
    }

    String payload = String(temperature) + "-" + String(humidity) + "-" + String(soilMoistureValue) + "-" + String(lightState);
    client.publish("home/sensors", payload.c_str());

    if (isAutoMode) {
      if (lightState == 1) {
        digitalWrite(ledPin, HIGH);
        client.publish("home/device/led", "onled");
        Serial.println("Đèn bật (Auto)");
      } else {
        digitalWrite(ledPin, LOW);
        client.publish("home/device/led", "offled");
        Serial.println("Đèn tắt (Auto)");
      }

      if (soilMoistureValue < 30) {
        digitalWrite(pumpPin, HIGH);
        client.publish("home/device/pump", "onpump");
        Serial.println("Máy bơm bật (Auto)");
      } else {
        digitalWrite(pumpPin, LOW);
        client.publish("home/device/pump", "offpump");
        Serial.println("Máy bơm tắt (Auto)");
      }
    } else {
      if (manualLEDState) {
        digitalWrite(ledPin, HIGH);
      } else {
        digitalWrite(ledPin, LOW);
      }

      if (manualPumpState) {
        digitalWrite(pumpPin, HIGH);
      } else {
        digitalWrite(pumpPin, LOW);
      }
    }
  }
}

