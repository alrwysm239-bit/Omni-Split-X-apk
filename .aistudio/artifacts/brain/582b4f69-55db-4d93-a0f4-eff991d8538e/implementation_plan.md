# Implementation Plan - GitHub Actions Debug APK Build Workflow

## Goal
Create `.github/workflows/build-apk.yml` to automatically build the debug APK using Gradle 9.3.1 and AGP 9.1.1, with Java 17 verification and temporary debug keystore generation.

## Steps
1. Verify `agp = "9.1.1"` in `gradle/libs.versions.toml`.
2. Create `.github/workflows/build-apk.yml` with:
   - Triggers on push to main and `workflow_dispatch`.
   - `ubuntu-latest` runner.
   - Java 17 setup (`actions/setup-java`).
   - Gradle 9.3.1 setup (`gradle/actions/setup-gradle`).
   - `Verify Java environment` step.
   - `Create temporary debug keystore` step.
   - Build step using `gradle :app:assembleDebug --stacktrace --no-daemon`.
   - Artifact upload step for `app/build/outputs/apk/debug/app-debug.apk` named `app-debug-apk`.
3. Do not modify any other files.
