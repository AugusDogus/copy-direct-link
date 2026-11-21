#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"

sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Set ANDROID_SDK_ROOT or ANDROID_HOME to your Android SDK.}}
tools="$sdk/build-tools/36.0.0"
android_jar="$sdk/platforms/android-36/android.jar"
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/generated" "$work/classes" "$work/dex" dist
shizuku_version=13.1.5
shizuku_maven="https://repo.maven.apache.org/maven2/dev/rikka/shizuku"
shizuku_jars=()
while read -r module checksum; do
    archive="$work/$module.aar"
    curl -fsSL --max-time 30 "$shizuku_maven/$module/$shizuku_version/$module-$shizuku_version.aar" -o "$archive"
    printf '%s  %s\n' "$checksum" "$archive" | sha256sum --check --status
    unzip -p "$archive" classes.jar > "$work/$module.jar"
    shizuku_jars+=("$work/$module.jar")
done <<'EOF'
aidl 33fe7191cdd69fcb66d649264f3b0c47acb2f3d6343afc05b98dbbff6f221963
api 4def9bde498ef8626614c2fc5db9af4749c86f16f6c33e3f5658d35e70bab59b
provider b0f18cd9812464ec171c53cac93a819fe411718a3965c311f01eb4de265381b3
shared 4659642c9339be0a26e9c65bb8648f7ad6d8f4a465f557993ccbc78802381635
EOF
annotation_jar="$work/annotation.jar"
curl -fsSL --max-time 30 \
    "https://dl.google.com/dl/android/maven2/androidx/annotation/annotation/1.3.0/annotation-1.3.0.jar" \
    -o "$annotation_jar"
printf '97dc45afefe3a1e421da42b8b6e9f90491477c45fc6178203e3a5e8a05ee8553  %s\n' "$annotation_jar" | sha256sum --check --status
classpath=$(IFS=:; printf '%s' "${shizuku_jars[*]}")

if [[ -n ${COPY_DIRECT_LINK_KEYSTORE:-} ]]; then
    : "${COPY_DIRECT_LINK_KEYSTORE_PASSWORD:?Set the release keystore password.}"
    keystore="$COPY_DIRECT_LINK_KEYSTORE"
    key_alias=copy-direct-link
    store_password=env:COPY_DIRECT_LINK_KEYSTORE_PASSWORD
else
    keystore="$HOME/.android/debug.keystore"
    key_alias=androiddebugkey
    store_password=pass:android
    if [[ ! -f "$keystore" ]]; then
        mkdir -p "$(dirname -- "$keystore")"
        keytool -genkeypair -keystore "$keystore" -storepass android -keypass android \
            -alias "$key_alias" -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
    fi
fi

"$tools/aapt2" compile --dir res -o "$work/resources.zip"
"$tools/aapt2" link -I "$android_jar" --manifest AndroidManifest.xml --java "$work/generated" -A assets \
    -o "$work/raw.apk" "$work/resources.zip"
mapfile -t sources < <(find src "$work/generated" -name '*.java' -type f | sort)
javac --release 17 -Xlint:all -Werror -classpath "$android_jar:$annotation_jar:$classpath" \
    -d "$work/classes" "${sources[@]}"
jar --create --file "$work/classes.jar" -C "$work/classes" .
"$tools/d8" --min-api 30 --lib "$android_jar" --classpath "$annotation_jar" --output "$work/dex" \
    "$work/classes.jar" "${shizuku_jars[@]}"
zip -q -j "$work/raw.apk" "$work/dex/classes.dex"
"$tools/zipalign" -f 4 "$work/raw.apk" "$work/aligned.apk"
"$tools/apksigner" sign --ks "$keystore" --ks-key-alias "$key_alias" --ks-pass "$store_password" \
    --v4-signing-enabled false --out dist/copy-direct-link.apk "$work/aligned.apk"
"$tools/apksigner" verify dist/copy-direct-link.apk
