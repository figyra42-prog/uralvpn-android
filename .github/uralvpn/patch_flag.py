#!/usr/bin/env python3
# UralVPN: flags of the locations are real pictures (not the emoji of the phone font): sharp and the same on
# every phone, rounded corners, a thin outline so that white flags do not melt into the background.
# - the PNG flags (public domain) are taken at build time from googlefonts/noto-emoji, a PINNED commit,
#   folder third_party/region-flags/png, only the two-letter countries -> res/drawable-nodpi/flag_xx.png;
# - the country code is read from the flag emoji in the name of the location; no picture -> the emoji as before
#   (e.g. the autopilot);
# - res/raw/uralkeep.xml keeps the flags from the resource shrinker (they are found by name).
# Usage: patch_flag.py <sfa-root>
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile

root = sys.argv[1]
PIN = "99701e07d3d1fe1bdbcdb14964547f0fd592966f"
URL = "https://github.com/googlefonts/noto-emoji"

# 1) the flags
work = tempfile.mkdtemp(prefix="flags-")


def git(*args):
    subprocess.run(["git", "-C", work] + list(args), check=True, stdout=subprocess.DEVNULL)


subprocess.run(["git", "init", "-q", work], check=True)
git("remote", "add", "origin", URL)
git("fetch", "-q", "--depth", "1", "--filter=blob:none", "origin", PIN)
git("checkout", "-q", "FETCH_HEAD", "--", "third_party/region-flags/png")
src = work + "/third_party/region-flags/png"
dst = root + "/app/src/main/res/drawable-nodpi"
os.makedirs(dst, exist_ok=True)
count = 0
sample = None
for name in sorted(os.listdir(src)):
    m = re.fullmatch(r"([A-Z]{2})\.png", name)
    if not m:
        continue
    shutil.copy(src + "/" + name, dst + "/flag_" + m.group(1).lower() + ".png")
    count += 1
    if m.group(1) == "DE":
        with open(src + "/" + name, "rb") as f:
            head = f.read(24)
        sample = struct.unpack(">II", head[16:24])
assert count > 200, "too few flags: %d" % count
print("flags copied: %d, size of DE.png: %s" % (count, sample))
shutil.rmtree(work, ignore_errors=True)

raw = root + "/app/src/main/res/raw"
os.makedirs(raw, exist_ok=True)
with open(raw + "/uralkeep.xml", "w") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources xmlns:tools="http://schemas.android.com/tools" tools:keep="@drawable/flag_*" />\n')

print("flags are ready (the row draws them from the source)")
