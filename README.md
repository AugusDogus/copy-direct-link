<div align="center">
  <img src="docs/icon.svg" alt="" width="96" height="96">
  <h1>Copy Direct Link</h1>
  <p>Tap Share in the Google app to copy the page’s real URL. Android 11 and later, no root required.</p>
  <p><a href="https://github.com/AugusDogus/copy-direct-link/releases/latest">Download APK</a> · <a href="#use-it">Get started</a> · <a href="https://augusdogus.github.io/copy-direct-link/copy-direct-link-demo.mp4?v=2">Play demo in 1080p60</a></p>
  <a href="https://augusdogus.github.io/copy-direct-link/copy-direct-link-demo.mp4?v=2">
    <picture>
      <source media="(prefers-reduced-motion: reduce)" srcset="docs/demo.png">
      <img src="docs/demo.webp" width="960" alt="Both toolbar Share buttons are tapped together. Google’s share sheet copies a share.google redirect. Copy Direct Link copies the full New York Magazine URL, and Chrome opens the article from the clipboard.">
    </picture>
  </a>
</div>

The demo shows the same Share tap on both sides, the two clipboard results, and Chrome opening the copied article URL. [New York Magazine article](https://nymag.com/intelligencer/2018/05/google-changes-motto-from-dont-be-evil-to-be-skynet.html)

## Use it

1. **Install.** Download `copy-direct-link.apk` from [Releases](https://github.com/AugusDogus/copy-direct-link/releases/latest) and open it on your phone.
2. **Enable it.** Open **Copy Direct Link**, tap **Open accessibility settings**, then select **Copy Direct Link** under **Downloaded apps**. Turn on **Use Copy Direct Link** and tap **Allow**.
3. **Copy a page link.** Open a search result in the Google app and tap **Share** in the page’s top toolbar. The full page URL is ready to paste.

The development build also has a **When you tap Share** setting. **Copy link** is the default. Choose **Share link** to open Android’s share sheet with the real URL. This mode also leaves the URL on your clipboard.

If Android shows **Restricted setting**, open **Settings → Apps → Copy Direct Link → ⋮ → Allow restricted settings**, then return to Accessibility. If you installed a debug build earlier, uninstall it before switching to the signed release and enable the service again.

## Compatibility

Copy Direct Link works when the Google app opens a result in a Chrome Custom Tab. The copy action was tested on an unrooted Pixel 8 Pro running Android 17 and on an Android 16 emulator. Both toolbar modes were tested on Android 16.

<details>
<summary>How it works</summary>

Copy Direct Link’s accessibility service finds the Share button in the Custom Tab toolbar and places a transparent button over it. That tap invokes Chrome’s built-in copy action, the same one you get by holding the page title. In Share link mode, the app reads the freshly copied URL and passes it to Android’s share sheet.

The app has no network permission. Turn off its accessibility service to restore the original Share button behavior.

</details>

## Build

With JDK 17 or later, Android SDK Platform 36, and Build Tools 36.0.0 installed, set `ANDROID_HOME` or `ANDROID_SDK_ROOT` and run `./build.sh`. The debug-signed APK is at `dist/copy-direct-link.apk`.

GitHub Actions builds development APKs for pull requests and pushes to `main`. Pushing a version tag publishes a signed release APK. [Build workflow](https://github.com/AugusDogus/copy-direct-link/actions/workflows/android.yml)
