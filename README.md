# Messenger v146 Android wrapper

This APK shell loads the existing v146 web app from `https://facebook-extra.onrender.com`, preserves the authenticated web session, and automatically opens the exact Messenger section implemented by `upload/messenger.js` after login.

Included Android integrations:
- file/photo/video/audio chooser
- microphone permission for voice messages
- camera permission when web capture requests it
- cookies, IndexedDB/DOM storage, WebSocket-capable WebView
- external links open outside the app
- Android back button returns from a chat to the inbox; from inbox it exits

## Build
Run `./gradlew assembleDebug` in an Android-capable environment with Android SDK 35 and JDK 17+.
The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

The GitHub Actions workflow can also build the APK automatically after pushing this folder to a repository.
