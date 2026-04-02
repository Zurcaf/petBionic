#include "SimpleEventDetector.h"

#include <math.h>

SimpleEventDetector::SimpleEventDetector(float threshold, uint32_t cooldownMs)
    : _threshold(threshold), _cooldownMs(cooldownMs), _lastEventMs(0) {}

EventInfo SimpleEventDetector::update(float rawValue, float filteredValue, uint32_t nowMs)
{
  float score = fabsf(rawValue - filteredValue);
  bool enoughGap = (nowMs - _lastEventMs) >= _cooldownMs;
  bool triggered = score >= _threshold && enoughGap;

  if (triggered)
  {
    _lastEventMs = nowMs;
  }

  return EventInfo{triggered, score};
}
