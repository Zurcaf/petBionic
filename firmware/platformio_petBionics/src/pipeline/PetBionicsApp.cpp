#include "PetBionicsApp.h"

PetBionicsApp::PetBionicsApp()
    : _sensor(_config.analogPin),
      _filter(_config.filterAlpha),
      _detector(_config.eventThreshold, _config.eventCooldownMs),
      _logger(_config.sdCsPin, _config.sdPath),
      _ble(_config),
      _lastSampleMs(0),
      _status{true, false, 0, 0} {}

void PetBionicsApp::begin()
{
  Serial.begin(115200);
  delay(200);
  Serial.println("petBionics firmware started");

  _sensor.begin();

  _status.sdReady = _logger.begin();
  if (_status.sdReady)
  {
    Serial.println("SD logger ready");
  }
  else
  {
    Serial.println("SD logger not ready (running without logging)");
  }

  _ble.begin("petBionics");
}

void PetBionicsApp::update()
{
  uint32_t nowMs = _clock.nowMs();

  if (_config.acquisitionEnabled && (nowMs - _lastSampleMs) >= _config.samplePeriodMs)
  {
    sampleStep(nowMs);
    _lastSampleMs = nowMs;
  }

  _status.acquisitionEnabled = _config.acquisitionEnabled;
  _status.sdReady = _logger.isReady();
  _ble.updateStatus(_status, nowMs);
}

void PetBionicsApp::sampleStep(uint32_t nowMs)
{
  _filter.setAlpha(_config.filterAlpha);
  _detector.setThreshold(_config.eventThreshold);

  int32_t raw = _sensor.readRaw();
  float filtered = _filter.update(static_cast<float>(raw));
  EventInfo event = _detector.update(static_cast<float>(raw), filtered, nowMs);

  RawSample sample{nowMs, raw, filtered};
  _logger.append(sample, event);

  _status.samples++;
  if (event.triggered)
  {
    _status.events++;
    Serial.printf("event t=%lu score=%.2f raw=%ld filt=%.2f\n",
                  static_cast<unsigned long>(sample.tLocalMs),
                  event.score,
                  static_cast<long>(sample.raw),
                  sample.filtered);
  }
}
