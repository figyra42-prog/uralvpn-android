#!/usr/bin/env python3
# UralVPN: preparation of the PUBLIC release build. Usage: patch_release.py <sfa-root>
#  1) the app name is UralVPN in every language (the installer showed "sing-box" on Russian phones),
#     and the visible "sing-box" in the texts of the app is replaced too;
#  2) the update check looks at the releases of figyra42-prog/uralvpn-android (not SagerNet),
#     and takes the universal APK;
#  3) R8 (the release build is minified) must not touch the core classes and the app classes.
import glob
import re
import sys

root = sys.argv[1]
res = root + "/app/src/main/res"

# 1) names and visible texts
changed = 0
for path in sorted(glob.glob(res + "/values*/strings.xml")):
    text = open(path, encoding="utf-8").read()
    new = re.sub(r'(<string name="app_name"[^>]*>)[^<]*(</string>)', r"\1UralVPN\2", text)
    # the word in the texts, but not in links and names: sing-box://, sing-box.sagernet.org, sing-box-for-android
    new = re.sub(r"sing-box(?!://|-\w|\.sagernet|\.org|\w)", "UralVPN", new)
    if new != text:
        open(path, "w", encoding="utf-8").write(new)
        changed += 1
print("names and texts changed in", changed, "files")

# 2) updates from our repository
checker = root + "/app/src/github/java/io/nekohasekai/sfa/vendor/GitHubUpdateChecker.kt"
s = open(checker, encoding="utf-8").read()
old = 'private const val RELEASES_URL = "https://api.github.com/repos/SagerNet/sing-box/releases"'
assert s.count(old) == 1, "RELEASES_URL not found"
s = s.replace(old, 'private const val RELEASES_URL = "https://api.github.com/repos/figyra42-prog/uralvpn-android/releases"')
old = '!asset.name.contains("play") &&'
assert s.count(old) == 1, "play filter not found"
s = s.replace(old, '!asset.name.contains("play") &&\n                asset.name.contains("universal") &&')
open(checker, "w", encoding="utf-8").write(s)
print("updates come from figyra42-prog/uralvpn-android, universal APK")

# 3) R8 keep rules
rules = root + "/app/proguard-rules.pro"
r = open(rules, encoding="utf-8").read()
if "UralVPN keep" not in r:
    r += """
# UralVPN keep: the core (gomobile, called from native code by class names) and the app classes
-keep class io.nekohasekai.** { *; }
-keep class go.** { *; }
-dontwarn **
"""
    open(rules, "w", encoding="utf-8").write(r)
print("proguard rules added")
