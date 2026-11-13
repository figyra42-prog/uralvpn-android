#!/usr/bin/env python3
# UralVPN: the app opens the links of the bot button "Add to UralVPN" (uralvpn://install-sub?url=...),
# the same link that the previous UralVPN app understood. Usage: patch_link.py <sfa-root>
import re
import sys

root = sys.argv[1]

manifest = root + "/app/src/main/AndroidManifest.xml"
m = open(manifest, encoding="utf-8").read()
anchor = re.compile(r'(android:scheme="sing-box" />\s*</intent-filter>)')
assert len(anchor.findall(m)) == 1, "sing-box intent-filter not found"
m = anchor.sub(
    lambda x: x.group(1)
    + """

            <intent-filter>
                <action android:name="android.intent.action.VIEW" />

                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />

                <data
                    android:host="install-sub"
                    android:scheme="uralvpn" />
            </intent-filter>""",
    m,
    count=1,
)
open(manifest, "w", encoding="utf-8").write(m)

main = root + "/app/src/main/java/io/nekohasekai/sfa/compose/MainActivity.kt"
s = open(main, encoding="utf-8").read()
old = "        val uri = intent.data ?: return\n"
assert s.count(old) == 1, "intent.data not found"
s = s.replace(old, "        val uri = io.nekohasekai.sfa.bg.UralTools.normalizeLink(intent.data ?: return)\n", 1)
open(main, "w", encoding="utf-8").write(s)
print("uralvpn://install-sub links are opened")
