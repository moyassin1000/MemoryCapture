# Contributing

1. Create a focused branch for one phase or issue.
2. Keep recording behavior explicit and user-consented.
3. Do not add root, hidden API, Accessibility abuse, OEM exploits, or permission-bypass techniques.
4. Run `gradle lint test assembleDebug` (or `./gradlew ...` once a standard wrapper is generated) before opening a PR.
5. Add tests for recording state transitions and permission-denial paths.
