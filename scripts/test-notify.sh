#!/usr/bin/env bash
# Post a test notification from the shell (shows as from com.android.shell).
adb shell cmd notification post -S bigtext -t "Test ${1:-$(date +%T)}" "bedtime-test-${RANDOM}" "Should be hidden during bedtime"
