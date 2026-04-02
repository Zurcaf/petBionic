#pragma once

#include <Arduino.h>

#include "../core/AppTypes.h"

class SimpleEventDetector
{
public:
  SimpleEventDetector(float threshold, uint32_t cooldownMs);
  EventInfo update(float rawValue, float filteredValue, uint32_t nowMs);
  void setThreshold(float threshold) { _threshold = threshold; }

private:
  float _threshold;
  uint32_t _cooldownMs;
  uint32_t _lastEventMs;
};
