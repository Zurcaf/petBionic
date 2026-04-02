#pragma once

#include <Arduino.h>

struct AppConfig
{
  uint32_t samplePeriodMs = 20;  // 50 Hz
  float filterAlpha = 0.2f;      // light EMA filter
  float eventThreshold = 100.0f; // abs(filtered - raw)
  uint32_t eventCooldownMs = 300;
  uint8_t analogPin = A0; // fallback raw source
  bool acquisitionEnabled = true;

  // Change this to the real CS pin used by your SD module.
  uint8_t sdCsPin = 5;
  const char *sdPath = "/raw_log.csv";
};
