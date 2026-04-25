# ClipKeeper

ClipKeeper is a local-first Android clipboard manager for saving and reusing text, links, codes, and shared images.

The app is designed to keep saved content on the device. Saved text and image data are encrypted locally with Android Keystore-backed AES-GCM, and the app does not request internet access.

## Privacy Policy

The Play Store privacy policy is hosted with GitHub Pages:

https://patriceac.github.io/clipkeeper/

## Build

```powershell
.\gradlew.bat :app:bundleRelease
```

Release signing uses local files in `release-secrets/`, which are intentionally not committed.
