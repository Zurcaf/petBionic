#include "RawSensor.h"

RawSensor::RawSensor(uint8_t analogPin) : _analogPin(analogPin) {}

void RawSensor::begin()
{
  pinMode(_analogPin, INPUT);
}

int32_t RawSensor::readRaw()
{
  return analogRead(_analogPin);
}
