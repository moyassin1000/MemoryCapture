# Phase 1 implementation notes

## Implemented

- Complete Android app skeleton
- Compose + Material 3 UI
- Navigation between Home and Settings
- English / Arabic resources and RTL support
- Recording state machine
- MediaProjection permission launcher
- Foreground RecordingService
- MediaProjectionController
- Notification channel + Stop action
- Basic state-machine tests
- GitHub Actions workflow

## Explicitly deferred

- VirtualDisplay creation
- MediaCodec / MediaMuxer video pipeline
- AudioRecord / AudioPlaybackCapture
- Room / DataStore persistence
- Hilt dependency injection
- screenshots, highlights, replay buffer, editor, albums

These are intentionally deferred according to the phased implementation plan so Phase 1 remains buildable and testable rather than mixing unfinished recording subsystems.
