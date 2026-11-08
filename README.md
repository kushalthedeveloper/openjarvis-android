
---

## Android Companion App

This fork includes a separate Android companion client application under the `android/` directory.

> **Important:** This is an Android companion/client app for OpenJarvis. The upstream Python framework remains unchanged and separate. The Android app is a native mobile client that communicates with OpenAI-compatible APIs via REST calls. It does not port the Python runtime or any of the original Python-based agent infrastructure to Android.

### Building the Android App

#### Prerequisites
- Android Studio Hedgehog or later
- JDK 17 or later
- Android SDK (minSdk 28, targetSdk 36)
- Gradle (managed via Gradle wrapper)

#### Build Commands

```bash
# Navigate to the Android project
cd android

# Build a debug APK
./gradlew assembleDebug

# Build a release APK
./gradlew assembleRelease
```

The debug APK will be generated at:
`android/app/build/outputs/apk/debug/app-debug.apk`

#### GitHub Actions (CI)
A workflow is provided at `.github/workflows/android-apk.yml` that:
- Triggers on `push` and `pull_request` events to the `main` branch
- Sets up the Android SDK and Gradle
- Builds a debug APK
- Uploads the APK as a workflow artifact

### App Features

- **Chat Interface:** Professional chat UI using Jetpack Compose with Material Design 3
- **Settings:** Configurable OpenAI-compatible base URL and API key
- **Security:** API key stored using Android EncryptedSharedPreferences (never committed to repo)
- **Networking:** Retrofit + OkHttp for HTTP requests to configurable `/v1/chat/completions` endpoints

### Architecture

The Android app is structured as follows:

```
android/
├── settings.gradle.kts         # Project-level configuration
├── build.gradle.kts            # Root build script
└── app/
    ├── build.gradle.kts        # App-level build configuration
    └── src/main/java/com/openjarvis/android/
        ├── MainActivity.kt     # Main entry point
        ├── ChatViewModel.kt    # Chat logic and API calls
        ├── ChatScreen.kt       # Compose chat UI
        ├── SettingsScreen.kt   # Compose settings UI
        └── util/
            ├── ChatApiService.kt    # Retrofit API client
            └── PreferencesHelper.kt # Secure credential storage
```

### Limitations

- This Android companion requires an external OpenAI-compatible API endpoint. The upstream Python local-first personal AI runtime is not included in this Android build.
- For local AI capabilities, users must run the upstream Python OpenJarvis framework on a desktop/server and configure the Android app's base URL to point to it.
- The Android app does not implement the full agent/agent-workflow features of the original Python framework. It is strictly a chat client.
- Model support depends on the upstream API being connected to.

### Security Notes

- Never commit API keys or credentials to the repository
- The app uses `EncryptedSharedPreferences` for local credential storage
- For development, use environment variables or a separate `.env` file (gitignored)
