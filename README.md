# MemoryCapture

MemoryCapture is a privacy-first Android screen and memory recorder. Phase 1 establishes the production project structure, Compose UI, navigation, recording state machine, MediaProjection consent flow, and a real foreground service that acquires a MediaProjection token after explicit user approval.

## Phase 1 features

- Kotlin + Jetpack Compose + Material 3
- Android 8.0+ (`minSdk 26`)
- `compileSdk/targetSdk 37`
- English + Arabic with RTL support
- Home screen with audio, quality, and FPS choices
- Settings screen
- Explicit MediaProjection consent flow
- Foreground `mediaProjection` service
- Recording state machine that rejects conflicting transitions
- Unit tests for core state transitions
- GitHub Actions for lint, tests, and debug APK build

## Current technical baseline

- Android Gradle Plugin 9.4.0
- Gradle 9.6.1
- Kotlin / Compose compiler 2.4.20
- Compose BOM 2026.09.00
- Activity 1.13.0
- Lifecycle 2.11.0
- Navigation 2.10.2

## Audio capture limitations

Phase 1 does not capture audio or encode video yet. Future phases will use Android's public APIs only. Internal playback audio will use `AudioPlaybackCaptureConfiguration` on Android 10+ and only when the source app allows capture. The project will not use root, hidden APIs, Accessibility abuse, ADB privilege escalation, or other bypass techniques to record protected/call audio.

## Build

The repository is configured for Gradle 9.6.1. A lightweight `gradlew`/`gradlew.bat` launcher is included: it uses the canonical wrapper JAR when present, otherwise it delegates to a system Gradle installation. Generate the standard wrapper once in an environment with Gradle available:

```bash
gradle wrapper --gradle-version 9.6.1
./gradlew lint test assembleDebug
```

The included GitHub Actions workflow installs Gradle 9.6.1, generates the canonical wrapper, and then runs `./gradlew lint`, `./gradlew test`, and `./gradlew assembleDebug`.

## Architecture

Phase 1 keeps the executable app in a single Android module while reserving the requested future module boundaries (`core`, `feature`, and `recording-engine`). This avoids premature multi-module build complexity before the recording engine stabilizes.

Main runtime areas:

- `recording/` – recording state model/store
- `projection/` – MediaProjection token ownership
- `service/` – foreground recording lifecycle
- `navigation/` – app destinations and NavHost
- `ui/` – Compose screens and theme

## Privacy

- No hidden recording
- No automatic recording on boot
- No upload or analytics in Phase 1
- Explicit system consent before screen capture
- Protected windows are not bypassed

## Roadmap

1. Phase 1 – structure, UI, state machine, MediaProjection consent/service
2. Phase 2 – MediaCodec video encoder + VirtualDisplay
3. Phase 3 – microphone capture
4. Phase 4 – internal playback audio
5. Phase 5 – device + mic mixing
6. Phase 6 – hardened foreground controls
7. Phase 7 – Room gallery
8. Phase 8 – highlights + screenshots
9. Phase 9 – instant replay buffer
10. Phase 10 – preview + trimming
11. Phase 11 – trips/albums
12. Phase 12 – privacy + security
13. Phase 13 – testing/optimization
14. Phase 14 – release CI/documentation hardening
