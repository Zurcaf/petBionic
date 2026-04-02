#pragma once

#include <Arduino.h>

class RawSensor
{
public:
  explicit RawSensor(uint8_t analogPin);
  void begin();
  int32_t readRaw();

private:
  uint8_t _analogPin;
};
