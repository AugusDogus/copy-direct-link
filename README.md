# Copy Direct Link

Copy Direct Link turns the Google app’s in-app browser Share button into a copy button. Tap the same icon to copy the website’s address. It uses the copy action Chrome already provides when you hold down the page title.

## Install on a Pixel

1. Download `copy-direct-link.apk` from [Releases](https://github.com/AugusDogus/copy-direct-link/releases/latest) and install it. If you use ADB, run `adb install copy-direct-link.apk` with the phone selected.
2. Open **Copy Direct Link** and tap **Open accessibility settings**. Under **Downloaded apps**, open **Copy Direct Link** and enable it.
3. Open a web result in the Google app and tap **Share** in the toolbar. Paste the link to check it.

If Android shows **Restricted setting** after you install from a file, open **Settings → Apps → Copy Direct Link → ⋮ → Allow restricted settings**, then return to Accessibility. Android prompts for this because the app was sideloaded.

The app uses an accessibility service to identify the Google Share button inside a Chrome Custom Tab. It has no network permission and Chrome writes the URL directly to the clipboard. Disable the service in Accessibility or uninstall the APK to restore the original tap behavior.

## Build

Use JDK 17 or newer, Android SDK Platform 36, and Build Tools 36.0.0. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT`, then run `./build.sh`. The script creates `dist/copy-direct-link.apk` and signs it with your local Android debug key.
