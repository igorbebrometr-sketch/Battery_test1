# Battery_test

Android battery information app built with Kotlin and Jetpack Compose.

This repository contains the Android app source. You can download and build it
on a PC; the resulting APK runs on Android phones and is not a native Windows
application.

## Features

- Live battery percentage, charging state, temperature, voltage, and cycle count when the device exposes it.
- Vertical liquid battery animation with charging rain and accelerometer-based slosh.
- Russian, Ukrainian, and English interface, plus system-language mode.
- Light and dark themes.
- Samsung One UI battery details shortcut.

## Build

Requirements: JDK 17 and Android SDK Platform 35 with Build Tools 35.0.0.

Set `JAVA_HOME` to JDK 17 and `ANDROID_SDK_ROOT` to the Android SDK directory, then run:

```bat
build.bat
```

Alternatively, use `gradlew.bat assembleDebug`. The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

For machine-specific paths, create `build.local.bat` in the project root. It is ignored by Git. Example:

```bat
set "JAVA_HOME=C:\Path\To\JDK17"
set "ANDROID_SDK_ROOT=C:\Path\To\Android\Sdk"
```

Battery health percentage is not exposed consistently by Android. The Samsung shortcut opens the device's battery details screen when available; it does not bypass system access restrictions.

## License

Licensed under the MIT License. See [LICENSE](LICENSE).
