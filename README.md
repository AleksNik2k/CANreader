# CANreader

Android CAN client in development.

[Documentation on English](docs/en/README.md)

[Документация на русском](docs/ru/README.md)

## Build

Install JDK 17 or newer, Android SDK platform 37, and Android SDK Build-Tools 37.0.0. In Android Studio, open this directory and allow Gradle to sync; the project uses Gradle 9.8.0 and Android Gradle Plugin 9.4.1. Dependency versions are managed in `gradle/libs.versions.toml`.

The Android, Java, Gradle, and XML extensions are listed in `.vscode/extensions.json`. Local SDK paths belong in the ignored `local.properties` file.

Build and test from a terminal with:

```sh
bash gradlew assembleDebug
bash gradlew testDebugUnitTest
bash gradlew lintDebug
```
