# VOIDEX S2

Android companion app for Standoff 2, built with Kotlin.

## Current features

- Local image collection with persistent document access where supported.
- Full-size local image preview.
- Floating Android overlay bubble and quick panel.
- Overlay display toggles for boxes, names, distance and health.
- 30-second timer in the overlay panel.
- Standalone reaction-time trainer with a saved personal best.
- 30-second target-tapping exercise with hit/miss count, accuracy, average reaction time and saved personal record.
- One-tap attempt to open Standoff 2 together with the overlay.
- Stop the overlay from the app or its foreground notification.

## Important limitations

The overlay renderer currently has no game-data provider, so it does **not** display real player markers. It does not read game memory, inspect game files, intercept traffic, inject code or bypass anti-cheat. Android's overlay permission only allows a window to be drawn over other apps; it does not reveal objects hidden behind walls.

The image collection is a local gallery and preview. It does not replace skins or weapon models inside Standoff 2.

The target exercise is a self-contained practice game inside VOIDEX. It does not detect, track or interact with players in Standoff 2.

## Build

Requirements:

- JDK 17
- Android SDK Platform 35
- Android Gradle Plugin 8.7.3
- Gradle 8.9

Open the project in Android Studio and sync Gradle, or run from a terminal with Gradle 8.9 installed:

```bash
gradle --no-daemon assembleDebug
```

The debug APK is created at:

```
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions

The workflow at `.github/workflows/android.yml` builds the debug APK on pushes to `main` and on manual workflow dispatch. When a run succeeds, download the `VOIDEX-S2-debug` artifact from that workflow run's Artifacts section.

## Android permissions

The app requires the user to grant **Display over other apps** permission to show the floating panel. Android 13+ may also ask for notification permission so the foreground service can show its persistent notification.

## Package

`com.voidex.s2`
