#pragma once

class LightFilter
{
public:
  explicit LightFilter(float alpha = 0.2f);
  void setAlpha(float alpha);
  float update(float input);
  float value() const { return _state; }
  bool initialized() const { return _initialized; }

private:
  float _alpha;
  float _state;
  bool _initialized;
};
