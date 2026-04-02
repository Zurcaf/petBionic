#include "LightFilter.h"

#include <Arduino.h>

LightFilter::LightFilter(float alpha) : _alpha(alpha), _state(0.0f), _initialized(false) {}

void LightFilter::setAlpha(float alpha)
{
  _alpha = constrain(alpha, 0.0f, 1.0f);
}

float LightFilter::update(float input)
{
  if (!_initialized)
  {
    _state = input;
    _initialized = true;
    return _state;
  }

  _state = _alpha * input + (1.0f - _alpha) * _state;
  return _state;
}
