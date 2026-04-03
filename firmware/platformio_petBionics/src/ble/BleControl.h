#pragma once

#include <Arduino.h>

#include "../core/AppConfig.h"
#include "../core/AppTypes.h"

class BleControl
{
public:
  explicit BleControl(AppConfig &config);
  void begin(const char *deviceName = "PetBionic");
  void updateStatus(const AppStatus &status, uint32_t nowMs);
  void applyCommand(const String &cmd);
  uint64_t currentEpochMs(uint32_t nowMs) const;

private:
  AppConfig &_config;
  uint32_t _lastStatusMs;
  String _statusCache;
  bool tryApplyTimeCommand(const String &cmd);
  uint64_t nowEpochMs(uint32_t nowMs) const;
  bool _timeSyncRequested;
  uint32_t _lastTimeSetMs;
  bool _timeSynced;
  int64_t _epochOffsetMs;
};
