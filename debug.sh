#!/bin/bash

echo "Capturing logcat for 30 seconds..."
adb logcat -s WeatherWidget -t 30
