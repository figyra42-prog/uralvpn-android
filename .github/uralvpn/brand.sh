#!/usr/bin/env bash
# UralVPN branding for SFA fork: icon, name, applicationId.
# Icon assets live in .github/uralvpn/brand (copied from the old uralvpn-app repo 19.11.2026).
# Usage: brand.sh <sfa-root>
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
OLD="$HERE/brand"
SFA=$1
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
# gradient background, separate monochrome silhouette and the adaptive-icon declarations
if [ -f "$OLD/res/drawable/ic_launcher_background.xml" ]; then
  cp "$OLD/res/drawable/ic_launcher_background.xml" "$RES/drawable/ic_launcher_background.xml"
fi
if [ -f "$OLD/res/drawable/ic_launcher_monochrome.xml" ]; then
  cp "$OLD/res/drawable/ic_launcher_monochrome.xml" "$RES/drawable/ic_launcher_monochrome.xml"
else
  cp "$OLD/res/drawable/ic_launcher_foreground.xml" "$RES/drawable/ic_launcher_monochrome.xml"
fi
cp "$OLD/res/values/ic_launcher_background.xml" "$RES/values/ic_launcher_background.xml"
if [ -d "$OLD/res/mipmap-anydpi-v26" ]; then
  mkdir -p "$RES/mipmap-anydpi-v26"
  cp "$OLD/res/mipmap-anydpi-v26/ic_launcher.xml" "$RES/mipmap-anydpi-v26/ic_launcher.xml"
  cp "$OLD/res/mipmap-anydpi-v26/ic_launcher_round.xml" "$RES/mipmap-anydpi-v26/ic_launcher_round.xml"
fi
[ -f "$OLD/ic_launcher-web.png" ] && cp "$OLD/ic_launcher-web.png" "$SFA/app/src/main/ic_launcher-playstore.png"

# App name (only the base string, translations of app_name are not used)
sed -i 's#<string name="app_name" translatable="false">[^<]*</string>#<string name="app_name" translatable="false">UralVPN</string>#' "$RES/values/strings.xml"

# applicationId: separate from old app (app.uralvpn) so both can be installed side by side
G=$SFA/app/build.gradle.kts
sed -i 's#applicationId = "io.nekohasekai.sfa"#applicationId = "app.uralvpn.next"#' "$G"
sed -i 's#base.archivesName.set("SFA-${versionName}")#base.archivesName.set("UralVPN-${versionName}")#' "$G"
# Only one separate APK, arm64 (the universal APK keeps the old 32-bit phones working)
sed -i 's#include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")#include("arm64-v8a")#' "$G"

# Brand colors and theme (Compose), Material You disabled
TH=$SFA/app/src/main/java/io/nekohasekai/sfa/compose/theme
cp "$HERE/src/Color.kt" "$TH/Color.kt"
cp "$HERE/src/Theme.kt" "$TH/Theme.kt"
# Subscription requests with device headers (panel returns a stub config without x-hwid)
cp "$HERE/src/HTTPClient.kt" "$SFA/app/src/main/java/io/nekohasekai/sfa/utils/HTTPClient.kt"
# XML seed color (used by legacy views/dialogs)
sed -i 's#<color name="seed">[^<]*</color>#<color name="seed">\#4E6B00</color>#' "$RES/values/colors.xml"
grep -n 'name="seed"' "$RES/values/colors.xml"

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

# Home screen in the style of the old app (UralHomeScreen) replaces the SFA dashboard,
# and the SFA round start button is hidden (the big connect button does that job).
D=$SFA/app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard
cp "$HERE/src/UralHomeScreen.kt" "$D/UralHomeScreen.kt"
cp "$HERE/src/SubscriptionInfo.kt" "$D/SubscriptionInfo.kt"
cp "$HERE/src/UralDrawer.kt" "$D/UralDrawer.kt"
# Autostart switch (read by BootReceiver, patched in patch_nav.py)
cp "$HERE/src/UralAutostart.kt" "$SFA/app/src/main/java/io/nekohasekai/sfa/bg/UralAutostart.kt"
cp "$HERE/src/UralTools.kt" "$SFA/app/src/main/java/io/nekohasekai/sfa/bg/UralTools.kt"
cp "$HERE/src/UralExpiryWork.kt" "$SFA/app/src/main/java/io/nekohasekai/sfa/bg/UralExpiryWork.kt"
cp "$HERE/src/UralWatchdog.kt" "$SFA/app/src/main/java/io/nekohasekai/sfa/bg/UralWatchdog.kt"
cp "$HERE/src/UralBackup.kt" "$SFA/app/src/main/java/io/nekohasekai/sfa/bg/UralBackup.kt"
# Short settings list (replaces the SFA SettingsScreen, same package and signature)
cp "$HERE/src/UralSettingsScreen.kt" "$SFA/app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/SettingsScreen.kt"
python3 -I - "$SFA" <<'PY'
import re
import sys

root = sys.argv[1]
nav = root + "/app/src/main/java/io/nekohasekai/sfa/compose/navigation/Navigation.kt"
s = open(nav).read()
call = re.compile(r"DashboardScreen\(\s*serviceStatus = serviceStatus,\s*showStartFab = showStartFab,\s*showStatusBar = showStatusBar,\s*onOpenNewProfile = onOpenNewProfile,\s*viewModel = dashboardViewModel,\s*\)")
assert len(call.findall(s)) == 1, "dashboard call not found"
s = call.sub("UralHomeScreen(serviceStatus = serviceStatus, showStatusBar = showStatusBar, onOpenNewProfile = onOpenNewProfile, dashboardViewModel = dashboardViewModel)", s)
imp = "import io.nekohasekai.sfa.compose.screen.dashboard.DashboardScreen\n"
assert imp in s, "DashboardScreen import not found"
s = s.replace(imp, imp + "import io.nekohasekai.sfa.compose.screen.dashboard.UralHomeScreen\n", 1)
open(nav, "w").write(s)

main = root + "/app/src/main/java/io/nekohasekai/sfa/compose/MainActivity.kt"
m = open(main).read()
a = "val showStartFab = !isRemote && !serviceRunning && dashboardUiState.selectedProfileId != -1L"
assert m.count(a) == 1, "showStartFab not found"
m = m.replace(a, "val showStartFab = false")
fab = re.compile(r"visible = !isRemote &&(\s+)currentServiceStatus == Status\.Stopped &&")
assert len(fab.findall(m)) == 1, "start fab not found"
m = fab.sub(lambda x: "visible = false &&" + x.group(1) + "currentServiceStatus == Status.Stopped &&", m)
open(main, "w").write(m)
print("home screen applied")
PY

# Side menu (burger) instead of the bottom tabs, like in the old app.
python3 -I "$HERE/patch_nav.py" "$SFA"

# Last update time in the subscription card (shows the background refresh).
python3 -I "$HERE/patch_card.py" "$SFA"

# The config text of a location (long press) can be selected and partly copied.
python3 -I "$HERE/patch_select.py" "$SFA"

# The links of the bot button "Add to UralVPN" (uralvpn://install-sub?url=...) open the import dialog.
python3 -I "$HERE/patch_link.py" "$SFA"

# Icons of the card follow the theme, no Ghostty config in the settings, theme switch.
python3 -I "$HERE/patch_theme.py" "$SFA"

# The service screen is gone; its battery-optimisation switch lives in the settings list.
python3 -I "$HERE/patch_service.py" "$SFA"

# The QR scan screen can also take a picture (gallery, screenshot).
python3 -I "$HERE/patch_qrimage.py" "$SFA"

# The app selection screen: title "Приложения (обход VPN)", visible mode switch (bypass / only selected).
python3 -I "$HERE/patch_perapp.py" "$SFA"

# The flags of the locations are 10% bigger.
python3 -I "$HERE/patch_flag.py" "$SFA"


# The logo of the top bar is centered, the burger button has the word "Меню".
python3 -I "$HERE/patch_title.py" "$SFA"

# The "+" is bigger and white, the refresh button moves from the top bar to the card next to the ping.
python3 -I "$HERE/patch_actions.py" "$SFA"

# Russian apps (banks, Gosuslugi, MAX, Yandex, VK, shops, taxi, telecom) go around the VPN by default:
# exclude mode on at the first start, the list is filled by a scan, a new Russian app joins the list itself.
python3 -I "$HERE/patch_rusapps.py" "$SFA"

# Dead-tunnel watchdog: checks traffic through the VPN on network change, screen unlock and every 2 minutes.
python3 -I "$HERE/patch_watchdog.py" "$SFA"
