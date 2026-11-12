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

grep -n 'applicationId\|archivesName' "$G"
grep -n 'name="app_name"' "$RES/values/strings.xml"
