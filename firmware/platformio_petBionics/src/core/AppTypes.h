#pragma once

#include <Arduino.h>

struct RawSample
{
  uint32_t tLocalMs;
  int32_t raw;
  float filtered;
};

struct EventInfo
{
  bool triggered;
  float score;
};

struct AppStatus
{
  bool acquisitionEnabled;
  bool sdReady;
  uint32_t samples;
  uint32_t events;
};
