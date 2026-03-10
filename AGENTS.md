# Repository Guidelines

## Project Structure & Module Organization
- Android app module: `app/`.
- Kotlin source: `app/src/main/kotlin/com/hypex/hyflash/`.
  - `data/`: USB, ADB, Fastboot, platform-tools managers.
  - `viewmodel/`: app orchestration (`MainViewModel`).
  - `ui/`: Compose screens, components, and theme.
  - `model/`: state and domain models.
- Resources: `app/src/main/res/`.
- Runtime assets (binaries/scripts): `app/src/main/assets/`.
- Build scripts: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`.

## Build, Test, and Development Commands
Use Gradle from project root:
- `sh ./gradlew :app:assembleDebug` - builds debug APK.
- `sh ./gradlew :app:compileDebugKotlin` - fast Kotlin compile check.
- `sh ./gradlew :app:lintDebug` - Android lint for debug variant.
- `sh ./gradlew :app:testDebugUnitTest` - runs JVM unit tests.
- `sh ./gradlew :app:connectedDebugAndroidTest` - runs instrumentation tests on device/emulator.

## Coding Style & Naming Conventions
- Language: Kotlin, Jetpack Compose, Material 3.
- Indentation: 4 spaces, no tabs.
- Keep files ASCII unless existing file already uses Unicode.
- Naming:
  - Classes/objects: `PascalCase` (e.g., `FastbootManager`).
  - Functions/vars: `camelCase` (e.g., `flashPartition`).
  - Constants: `UPPER_SNAKE_CASE`.
  - Compose screens/components should end with `Screen` / clear UI suffix.
- Prefer small, focused functions and explicit logging for device/USB failures.

## Testing Guidelines
- Primary framework: JUnit for unit tests; AndroidX test stack for instrumentation.
- Test locations:
  - Unit: `app/src/test/...`
  - Instrumented: `app/src/androidTest/...`
- Naming pattern: `ClassNameTest` and descriptive method names (e.g., `flashFailsWhenNoFastbootDevice`).
- Add tests for protocol parsing, command normalization, and failure-path handling.

## Commit & Pull Request Guidelines
- Follow imperative commit style: `fix:`, `feat:`, `refactor:`, `docs:`.
- Keep commits scoped and atomic (one concern per commit).
- PRs should include:
  - concise summary,
  - risk/impact notes (ADB/Fastboot behavior changes),
  - test evidence (commands run),
  - UI screenshots for Compose changes.

## Security & Configuration Tips
- Never commit private keys, keystores, or secrets.
- Validate external binaries (ABI and source) before bundling into `assets/`.
- Prefer internal app storage for executable extraction; do not execute from shared storage.
