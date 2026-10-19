# Building

## With Gradle (PC or CI)

Requirements: JDK 17 and the Android SDK (API 36 to compile; the app targets API 35). The Gradle wrapper downloads the right
Gradle version.

```bash
./gradlew testDebugUnitTest   # unit tests (run on the JVM, no device needed)
./gradlew assembleDebug       # APK in app/build/outputs/apk/debug/
```

Every push to `main` runs the same two tasks in GitHub Actions
([workflow](../.github/workflows/android.yml)) and uploads the debug APK as an artifact.

## With CodeAssist (on an Android phone)

This project was developed and built entirely on a phone with CodeAssist.

1. Import the folder that contains `settings.gradle` as a **new project**.
2. Build the **Debug** variant.

**Keep `org.gradle.jvmargs=-Xmx2048m` in `gradle.properties`.** With `-Xmx1536m`, CodeAssist
fails in `mergeProjectDexDebug` with `ClassNotFoundException: com.android.tools.r8.D8`.
See [Case 2](case-studies/02-codeassist-d8-heap.md) for how that was found.

**Why `compileSdk 36`:** `libvlc-all:3.7.6` declares `minCompileSdk=36` in its AAR metadata.
CodeAssist doesn't enforce that check, so the phone builds passed with 35; the first Gradle build
in GitHub Actions failed in `checkDebugAarMetadata` until `compileSdk` went to 36. `targetSdk`
stays at 35, so runtime behavior is unchanged.

## Dependencies

| Dependency | Version | Use |
|---|---|---|
| Android Gradle Plugin | 8.10.1 | build (first version that supports compileSdk 36) |
| `org.videolan.android:libvlc-all` | 3.7.6 | RTSP playback (LGPL-2.1+) |
| JUnit | 4.13.2 | unit tests |
