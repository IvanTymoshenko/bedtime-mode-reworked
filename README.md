# Bedtime Mode

A tiny Android app that brings back a **working bedtime mode**: at night every notification is
hidden from the notification shade and the phone stays silent. In the morning everything comes
back to the shade so you can review it.

Built because ColorOS 17 (OPPO Find N6 and others) broke the built-in bedtime mode — notifications
kept showing up in the shade and as pop-ups. It should work on any Android 10+ phone.

## Features

- **Hides notifications at night** and returns them all to the shade when bedtime ends
- **Silent**: no sound, vibration or pop-ups (alarms still ring; optionally starred contacts and repeat callers can call)
- **Schedule** with start/end time and days of the week
- **Quick Settings tile** to turn bedtime on/off anytime, regardless of the schedule
- **"Bedtime is on" notification** with a *Turn off* button while it's active
- **History**: what was hidden each night, per app, and how much came back
- **Allowed apps**: let specific apps through
- **In-app updates**: the app tells you when a new version is out, shows what's new and updates itself
- No internet use except checking GitHub for updates. No ads, no tracking, no accounts.

## Install

1. On your phone, open the [latest release](../../releases/latest) and download `BedtimeMode-x.y.z.apk`.
2. Open the downloaded file. If asked, allow your browser / file manager to **install unknown apps**.
   (ColorOS may show "Risk detection failed" — tap **Continue installation**.)
3. Open **Bedtime Mode** and follow the setup. It walks you through each permission, one per screen:
   1. **Notification access** — needed to hide and restore notifications.
      If the switch is greyed out ("Restricted setting"): *App info → ⋮ → Allow restricted settings*, then try again.
   2. **Do Not Disturb access** — to keep the phone silent (often granted automatically with step 1).
   3. **Notifications** — for the "Bedtime is on" notice and update alerts.
   4. **Unrestricted battery** — so the phone doesn't stop the app at night.
      On ColorOS/OxygenOS also: *App info → Battery usage → Allow background activity*.
   5. **Turn off the phone's own Bedtime / DND schedules** so they don't conflict.
   6. **Add the Quick Settings tile.**
   7. **Pick your bedtime.**

Updates install from inside the app — no need to come back here.

## How it works

- Notifications are hidden with Android's own **snooze** feature (via notification access). They are
  snoozed in 10-minute chunks while bedtime lasts, so if you turn bedtime off early they come back
  within ~10 minutes; at the scheduled end they come back right on time.
- Silence comes from the app's own **Do Not Disturb rule** ("Bedtime Mode" in your DND settings —
  keep it enabled). Your other DND settings are never touched.
- Ongoing notifications (music, navigation, calls, downloads) and alarms are never hidden.

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Notification access switch is greyed out | App info → ⋮ → *Allow restricted settings* |
| Stops working overnight | Allow unrestricted battery / background activity; lock the app in Recents |
| Phone still makes sounds | Check "Silence with Do Not Disturb" is on and the *Bedtime Mode* entry in DND schedules is enabled |
| Two bedtime modes fighting | Turn off the system Bedtime (Digital Wellbeing → Bedtime mode → routine: None) |

## Building

Requires JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew assembleDebug           # debug build
./gradlew assembleRelease         # needs keystore.properties (see below)
scripts/install.sh                # build release + install over adb + pre-grant permissions
```

Release signing reads `keystore.properties` (not committed):

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=bedtimemode
keyPassword=...
```

All releases must be signed with the same key, otherwise in-app updates are rejected by Android.

### Publishing a release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Write the changes in `CHANGELOG.md` under the new version.
3. `scripts/release.sh` — builds, tags and uploads the APK to GitHub Releases with those notes.
   Installed apps pick it up within 12 hours (or immediately when opened).

## License

MIT — see [LICENSE](LICENSE).
