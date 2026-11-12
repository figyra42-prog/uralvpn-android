#!/usr/bin/env bash
# UralVPN branding for SFA fork: icon, name, applicationId taken from uralvpn-app.
# Usage: brand.sh <uralvpn-app/V2rayNG/app/src/main> <sfa-root>
set -euo pipefail
OLD=$1
SFA=$2
RES=$SFA/app/src/main/res

# Launcher icons (png per density + adaptive foreground/background)
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  src=$OLD/res/mipmap-$d
  [ -d "$src" ] || continue
  cp "$src/ic_launcher.png" "$RES/mipmap-$d/ic_launcher.png"
  if [ -f "$src/ic_launcher_round.png" ]; then
    cp "$src/ic_launcher_round.png" "$RES/mipmap-$d/ic_launcher_round.png"
  else
    cp "$src/ic_launcher.png" "$RES/mipmap-$d/ic_launcher_round.png"
  fi
done
cp "$OLD/res/drawable/ic_launcher_foreground.xml" "$RES/drawable/ic_launcher_foreground.xml"
cp "$OLD/res/drawable/ic_launcher_foreground.xml" "$RES/drawable/ic_launcher_monochrome.xml"
cp "$OLD/res/values/ic_launcher_background.xml" "$RES/values/ic_launcher_background.xml"
[ -f "$OLD/ic_launcher-web.png" ] && cp "$OLD/ic_launcher-web.png" "$SFA/app/src/main/ic_launcher-playstore.png"

# App name (only the base string, translations of app_name are not used)
sed -i 's#<string name="app_name" translatable="false">[^<]*</string>#<string name="app_name" translatable="false">UralVPN</string>#' "$RES/values/strings.xml"

# applicationId: separate from old app (app.uralvpn) so both can be installed side by side
G=$SFA/app/build.gradle.kts
sed -i 's#applicationId = "io.nekohasekai.sfa"#applicationId = "app.uralvpn.next"#' "$G"
sed -i 's#base.archivesName.set("SFA-${versionName}")#base.archivesName.set("UralVPN-${versionName}")#' "$G"
# Only real-phone ABIs (libbox is built for arm64 and arm only)
sed -i 's#include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")#include("armeabi-v7a", "arm64-v8a")#' "$G"

grep -n 'applicationId\|archivesName' "$G"
grep -n 'name="app_name"' "$RES/values/strings.xml"

# Core compat: hiddify-based uralvpn-core still has PlatformInterface.SystemCertificates(),
# upstream SFA removed its implementation. Add it back (KeyStore/Base64 are already imported).
W=$SFA/app/src/main/java/io/nekohasekai/sfa/bg/PlatformInterfaceWrapper.kt
if ! grep -q 'fun systemCertificates' "$W"; then
  python3 -I - "$W" <<'PY'
import sys
p = sys.argv[1]
s = open(p).read()
anchor = "    override fun underNetworkExtension(): Boolean = false\n"
add = '''    @OptIn(ExperimentalEncodingApi::class)
    override fun systemCertificates(): StringIterator {
        val certificates = mutableListOf<String>()
        val keyStore = KeyStore.getInstance("AndroidCAStore")
        if (keyStore != null) {
            keyStore.load(null, null)
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val cert = keyStore.getCertificate(aliases.nextElement())
                certificates.add(
                    "-----BEGIN CERTIFICATE-----\\n" + Base64.encode(cert.encoded) + "\\n-----END CERTIFICATE-----",
                )
            }
        }
        return StringArray(certificates.iterator())
    }

'''
assert anchor in s, "anchor not found"
open(p, "w").write(s.replace(anchor, add + anchor, 1))
print("systemCertificates added")
PY
fi
