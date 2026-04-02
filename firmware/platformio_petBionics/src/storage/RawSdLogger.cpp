#include "RawSdLogger.h"

#include <SD.h>

RawSdLogger::RawSdLogger(uint8_t csPin, const char *filePath)
    : _csPin(csPin), _filePath(filePath), _ready(false) {}

bool RawSdLogger::begin()
{
  _ready = SD.begin(_csPin);
  if (!_ready)
  {
    return false;
  }

  return ensureHeader();
}

bool RawSdLogger::ensureHeader()
{
  if (SD.exists(_filePath))
  {
    return true;
  }

  File file = SD.open(_filePath, FILE_WRITE);
  if (!file)
  {
    _ready = false;
    return false;
  }

  file.println("t_ms,raw,filtered,event,score");
  file.close();
  return true;
}

bool RawSdLogger::append(const RawSample &sample, const EventInfo &event)
{
  if (!_ready)
  {
    return false;
  }

  File file = SD.open(_filePath, FILE_APPEND);
  if (!file)
  {
    _ready = false;
    return false;
  }

  file.printf("%lu,%ld,%.3f,%u,%.3f\n",
              static_cast<unsigned long>(sample.tLocalMs),
              static_cast<long>(sample.raw),
              sample.filtered,
              event.triggered ? 1U : 0U,
              event.score);

  file.close();
  return true;
}
