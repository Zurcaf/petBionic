#pragma once

#include <Arduino.h>
#include <FS.h>

#include "../core/AppTypes.h"

class RawSdLogger
{
public:
  RawSdLogger(uint8_t csPin, const char *filePath);
  bool begin();
  bool isReady() const { return _ready; }
  bool append(const RawSample &sample, const EventInfo &event);

private:
  uint8_t _csPin;
  const char *_filePath;
  bool _ready;
  bool ensureHeader();
};
