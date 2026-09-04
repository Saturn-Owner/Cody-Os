# Installing Cody Home

*(To be completed.)* Once the device is running LineageOS (see
[`../device-setup/`](../device-setup/)), installing Cody Home is:

1. Copy [`../../android/gradle.properties.example`](../../android/gradle.properties.example)
   to `android/gradle.properties` and fill in your own Gateway's URLs.
2. Build the APK (`./gradlew assembleDebug` or `assembleRelease` from
   `android/`).
3. Install it on the device (`adb install`, or via a recovery-flashable
   package once one exists).
4. On first launch, pair the app with your Gateway using a pairing code
   your Gateway issues.

Exact step-by-step instructions, screenshots, and troubleshooting will be
filled in here.
