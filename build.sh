#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"

sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Set ANDROID_SDK_ROOT or ANDROID_HOME to your Android SDK.}}
tools="$sdk/build-tools/36.0.0"
android_jar="$sdk/platforms/android-36/android.jar"
keystore="$HOME/.android/debug.keystore"
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/generated" "$work/classes" "$work/dex" dist

if [[ ! -f "$keystore" ]]; then
    mkdir -p "$(dirname -- "$keystore")"
    keytool -genkeypair -keystore "$keystore" -storepass android -keypass android \
        -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
fi

"$tools/aapt2" compile --dir res -o "$work/resources.zip"
"$tools/aapt2" link -I "$android_jar" --manifest AndroidManifest.xml --java "$work/generated" \
    -o "$work/raw.apk" "$work/resources.zip"
mapfile -t sources < <(find src "$work/generated" -name '*.java' -type f | sort)
javac --release 17 -Xlint:all -Werror -classpath "$android_jar" -d "$work/classes" "${sources[@]}"
jar --create --file "$work/classes.jar" -C "$work/classes" .
"$tools/d8" --min-api 30 --lib "$android_jar" --output "$work/dex" "$work/classes.jar"
zip -q -j "$work/raw.apk" "$work/dex/classes.dex"
"$tools/zipalign" -f 4 "$work/raw.apk" "$work/aligned.apk"
"$tools/apksigner" sign --ks "$keystore" --ks-key-alias androiddebugkey --ks-pass pass:android \
    --v4-signing-enabled false --out dist/copy-direct-link.apk "$work/aligned.apk"
"$tools/apksigner" verify dist/copy-direct-link.apk
