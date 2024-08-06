#include <ESP8266WiFi.h>
#include "./secrets.h"

const char* ssid     = SECRET_SSID;
const char* password = SECRET_PASSWORD;

const char* on_json_str = "\"on\"";
const char* off_json_str = "\"off\"";

// LIGHT1_PIN & LIGHT2_PIN are common anode, i.e. turn on when LOW voltage is applied
#define LIGHT1_PIN       2   // ESP Onboard LED
#define LIGHT2_PIN       16  // NodeMCU Onboard LED
#define AC_PIN           4
#define FAN_PIN          12
#define MOTOR_PIN        5
#define ANALOG_INPUT_PIN A0

WiFiServer server(80);

void setup() {
  Serial.begin(115200);
  delay(10);
  pinMode(LIGHT1_PIN, OUTPUT);
  pinMode(LIGHT2_PIN, OUTPUT);
  pinMode(AC_PIN, OUTPUT);
  pinMode(FAN_PIN, OUTPUT);
  pinMode(MOTOR_PIN, OUTPUT);
  digitalWrite(LIGHT1_PIN, HIGH);
  digitalWrite(LIGHT2_PIN, HIGH);
  digitalWrite(AC_PIN, LOW);
  digitalWrite(FAN_PIN, LOW);
  digitalWrite(MOTOR_PIN, LOW);

  // Connect to WiFi network
  Serial.println();
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.println("WiFi connected");

  // Start the server
  server.begin();
  Serial.println("Server started");

  // Print the IP address
  Serial.print("Use this URL to connect: ");
  Serial.print("http://");
  Serial.print(WiFi.localIP());
  Serial.println("/");
}

void loop() {
  // Check if a client has connected
  WiFiClient client = server.available();
  if (!client) { return; }

  // Wait until the client sends some data
  Serial.println("new client");
  while (!client.available()) { delay(1); }

  // Read the first line of the request
  String request = client.readStringUntil('\r');
  Serial.println(request);
  client.flush();

  // Match the request
  if (request.indexOf("/Light1?q=on") > 0) { digitalWrite(LIGHT1_PIN, LOW); }
  if (request.indexOf("/Light1?q=off") > 0) { digitalWrite(LIGHT1_PIN, HIGH); }
  if (request.indexOf("/Light2?q=on") > 0) { digitalWrite(LIGHT2_PIN, LOW); }
  if (request.indexOf("/Light2?q=off") > 0) { digitalWrite(LIGHT2_PIN, HIGH); }
  if (request.indexOf("/Fan?q=on") > 0) { digitalWrite(FAN_PIN, HIGH); }
  if (request.indexOf("/Fan?q=off") > 0) { digitalWrite(FAN_PIN, LOW); }
  if (request.indexOf("/AirConditioner?q=on") > 0) {
    digitalWrite(AC_PIN, HIGH);
  }
  if (request.indexOf("/AirConditioner?q=off") > 0) {
    digitalWrite(AC_PIN, LOW);
  }
  if (request.indexOf("/ElectricMotor?q=on") > 0) {
    digitalWrite(MOTOR_PIN, HIGH);
  }
  if (request.indexOf("/ElectricMotor?q=off") > 0) {
    digitalWrite(MOTOR_PIN, LOW);
  }

  if (request.indexOf("/Status") > 0) {
    // Return the response
    client.println("HTTP/1.1 200 OK");
    client.println("Content-Type: application/json");
    client.println("");
    client.println("{");
    client.print("\"Light1\": ");
    client.print(!digitalRead(LIGHT1_PIN) ? on_json_str : off_json_str);
    client.println(",");
    client.print("\"Light2\": ");
    client.print(!digitalRead(LIGHT2_PIN) ? on_json_str : off_json_str);
    client.println(",");
    client.print("\"Fan\": ");
    client.print(digitalRead(FAN_PIN) ? on_json_str : off_json_str);
    client.println(",");
    client.print("\"AirConditioner\": ");
    client.print(digitalRead(AC_PIN) ? on_json_str : off_json_str);
    client.println(",");
    client.print("\"ElectricMotor\": ");
    client.print(digitalRead(MOTOR_PIN) ? on_json_str : off_json_str);
    client.println(",");
    client.print("\"AnalogInput\": ");
    client.print(analogRead(ANALOG_INPUT_PIN));
    client.println("");
    client.println("}");
  } else {
    // Return the response
    client.println("HTTP/1.1 200 OK");
    client.println("Content-Type: text/plain");
    client.println("Content-Length: 0");
    client.println("");
  }
  client.flush();
  delay(1);
  Serial.println("Client disonnected");
  Serial.println("");
}