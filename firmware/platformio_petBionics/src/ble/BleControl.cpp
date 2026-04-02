#include "BleControl.h"

#include <cstdio>
#include <cstdlib>
#include <string>

#if __has_include(<BLEDevice.h>)
#include <BLEDevice.h>
#include <BLE2902.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#define PETBIONICS_HAS_BLE 1
#else
#define PETBIONICS_HAS_BLE 0
#endif

namespace
{
#if PETBIONICS_HAS_BLE
  const char *kServiceUuid = "14f16000-9d9c-470f-9f6a-6e6fe401a001";
  const char *kControlUuid = "14f16001-9d9c-470f-9f6a-6e6fe401a001";
  const char *kStatusUuid = "14f16002-9d9c-470f-9f6a-6e6fe401a001";

  BLECharacteristic *g_controlCharacteristic = nullptr;
  BLECharacteristic *g_statusCharacteristic = nullptr;
  BleControl *g_instance = nullptr;

  class ControlCallbacks : public BLECharacteristicCallbacks
  {
    void onWrite(BLECharacteristic *pCharacteristic) override
    {
      if (!g_instance || !pCharacteristic)
      {
        return;
      }

      std::string value = pCharacteristic->getValue();
      if (value.empty())
      {
        return;
      }

      String command = String(value.c_str());
      command.trim();
      if (command.length() == 0)
      {
        return;
      }

      g_instance->applyCommand(command);
    }
  };
#endif
} // namespace

BleControl::BleControl(AppConfig &config)
    : _config(config),
      _lastStatusMs(0),
      _statusCache("{}"),
      _timeSyncRequested(true),
      _lastTimeSetMs(0),
      _timeSynced(false),
      _epochOffsetMs(0) {}

bool BleControl::tryApplyTimeCommand(const String &cmd)
{
  if (!cmd.startsWith("TIME="))
  {
    return false;
  }

  String rawValue = cmd.substring(5);
  rawValue.trim();
  if (rawValue.length() == 0)
  {
    return true;
  }

  char *endPtr = nullptr;
  long long parsed = strtoll(rawValue.c_str(), &endPtr, 10);
  if (endPtr == rawValue.c_str() || (endPtr && *endPtr != '\0') || parsed <= 0)
  {
    return true;
  }

  uint64_t epochValue = static_cast<uint64_t>(parsed);
  const uint64_t kMillisThreshold = 100000000000ULL;
  uint64_t epochMs = epochValue;
  if (epochValue < kMillisThreshold)
  {
    epochMs = epochValue * 1000ULL;
  }

  _epochOffsetMs = static_cast<int64_t>(epochMs) - static_cast<int64_t>(millis());
  _timeSynced = true;
  _timeSyncRequested = false;
  _lastTimeSetMs = millis();
  return true;
}

uint64_t BleControl::nowEpochMs(uint32_t nowMs) const
{
  if (!_timeSynced)
  {
    return 0;
  }

  int64_t shifted = static_cast<int64_t>(nowMs) + _epochOffsetMs;
  if (shifted < 0)
  {
    return 0;
  }

  return static_cast<uint64_t>(shifted);
}

void BleControl::begin(const char *deviceName)
{
#if PETBIONICS_HAS_BLE
  BLEDevice::init(deviceName);

  BLEServer *server = BLEDevice::createServer();
  BLEService *service = server->createService(kServiceUuid);

  g_controlCharacteristic = service->createCharacteristic(
      kControlUuid, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
  g_controlCharacteristic->setCallbacks(new ControlCallbacks());

  g_statusCharacteristic = service->createCharacteristic(
      kStatusUuid, BLECharacteristic::PROPERTY_NOTIFY | BLECharacteristic::PROPERTY_READ);
  g_statusCharacteristic->addDescriptor(new BLE2902());
  g_statusCharacteristic->setValue(_statusCache.c_str());

  service->start();
  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(kServiceUuid);
  advertising->start();

  g_instance = this;
#else
  (void)deviceName;
  Serial.println("BLE headers not found, BLE control disabled at compile time");
#endif
}

void BleControl::updateStatus(const AppStatus &status, uint32_t nowMs)
{
#if PETBIONICS_HAS_BLE
  if (!g_statusCharacteristic)
  {
    return;
  }

  if ((nowMs - _lastStatusMs) < 1000)
  {
    return;
  }

  const uint32_t kPeriodicResyncMs = 60000;
  if (_timeSynced && (nowMs - _lastTimeSetMs) >= kPeriodicResyncMs)
  {
    _timeSyncRequested = true;
  }

  uint64_t epochMs = nowEpochMs(nowMs);
  char epochMsBuffer[24];
  snprintf(epochMsBuffer, sizeof(epochMsBuffer), "%llu", static_cast<unsigned long long>(epochMs));

  _statusCache = String("{") +
                 "\"acq\":" + (status.acquisitionEnabled ? "true" : "false") + "," +
                 "\"sd\":" + (status.sdReady ? "true" : "false") + "," +
                 "\"samples\":" + String(status.samples) + "," +
                 "\"events\":" + String(status.events) + "," +
                 "\"uptime_ms\":" + String(nowMs) + "," +
                 "\"time_sync_needed\":" + (_timeSyncRequested ? "true" : "false") + "," +
                 "\"time_synced\":" + (_timeSynced ? "true" : "false") + "," +
                 "\"time_ms\":" + String(epochMsBuffer) + "}";

  g_statusCharacteristic->setValue(_statusCache.c_str());
  g_statusCharacteristic->notify();
  _lastStatusMs = nowMs;
#else
  (void)status;
  (void)nowMs;
#endif
}

void BleControl::applyCommand(const String &cmd)
{
  if (cmd.equalsIgnoreCase("TIME_SYNC_NOW"))
  {
    _timeSyncRequested = true;
    return;
  }

  if (tryApplyTimeCommand(cmd))
  {
    return;
  }

  if (cmd.equalsIgnoreCase("START"))
  {
    _config.acquisitionEnabled = true;
    return;
  }

  if (cmd.equalsIgnoreCase("STOP"))
  {
    _config.acquisitionEnabled = false;
    return;
  }

  if (cmd.startsWith("ALPHA="))
  {
    float v = cmd.substring(6).toFloat();
    if (v >= 0.0f && v <= 1.0f)
    {
      _config.filterAlpha = v;
    }
    return;
  }

  if (cmd.startsWith("THR="))
  {
    float v = cmd.substring(4).toFloat();
    if (v >= 0.0f)
    {
      _config.eventThreshold = v;
    }
    return;
  }

  if (cmd.startsWith("PERIOD="))
  {
    long v = cmd.substring(7).toInt();
    if (v >= 1)
    {
      _config.samplePeriodMs = static_cast<uint32_t>(v);
    }
  }
}
