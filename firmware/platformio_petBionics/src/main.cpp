#include <Arduino.h>

void setup()
{
  Serial.begin(115200);
  delay(200);
  Serial.println("petBionics firmware started");
}

void loop()
{
  delay(1000);
}