# Local setup on this machine

The build toolchain is installed outside the repo (so it stays out of git):

- JDK 17.0.21: `C:\Users\conan\Documents\Assignment\.tools\jdk17\jdk-17.0.21.1+2`
- Android SDK 36 + build-tools + platform-tools: `C:\Users\conan\Documents\Assignment\.tools\android-sdk`
- Gradle wrapper: committed in the repo; distribution is cached under `%GRADLE_USER_HOME%`.

`local.properties` (gitignored) already points `sdk.dir` at that SDK and has DAT
placeholders `0`/`0`. Add your own `gemini_api_key` if you want damage/note extraction.

## Commands

```powershell
$env:JAVA_HOME = 'C:\Users\conan\Documents\Assignment\.tools\jdk17\jdk-17.0.21.1+2'
cd C:\Users\conan\Documents\Assignment\FieldCapture
.\gradlew.bat assembleDebug test
.\gradlew.bat compileDebugAndroidTestKotlin
```

APK: `app\build\outputs\apk\debug\app-debug.apk`

With a phone connected and USB debugging on:

```powershell
& 'C:\Users\conan\Documents\Assignment\.tools\android-sdk\platform-tools\adb.exe' devices
& 'C:\Users\conan\Documents\Assignment\.tools\android-sdk\platform-tools\adb.exe' install -r app\build\outputs\apk\debug\app-debug.apk
```

`connectedAndroidTest` also needs a connected device; no device was attached at scaffold time.
