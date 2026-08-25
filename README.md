# Messenger v146 Native Android

Native Android Messenger client for the existing `facebook-extra.onrender.com` backend.

The Messenger interface is native Android. A contained WebView is used only for the in-app profile browser so the existing website profile can open without leaving Messenger. The Messenger UI, icon geometry, conversation spacing, message bubble grouping, timestamp rules, send-state assets, pinned icon, default profile image, voice-note waveform, and messaging API behavior were ported from the supplied v146 website implementation (`messenger.js` / `server.js`).

The project includes a GitHub Actions workflow at `.github/workflows/build-apk.yml`. Push to `main` or run the workflow manually and download the `Messenger-v146-native` artifact.
