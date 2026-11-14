#!/usr/bin/env python3
# UralVPN: the flag of a location is 10% bigger (font 38.2 -> 42 sp, line 44 -> 48 sp, width 65 -> 72 dp).
# Usage: patch_flag.py <sfa-root>
import sys

root = sys.argv[1]
home = root + "/app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/UralHomeScreen.kt"
h = open(home).read()
old = "                fontSize = 38.2.sp,\n                lineHeight = 44.sp,\n                modifier = Modifier.width(65.dp),\n"
assert h.count(old) == 1, "flag text not found (%d)" % h.count(old)
h = h.replace(old, "                fontSize = 42.sp,\n                lineHeight = 48.sp,\n                modifier = Modifier.width(72.dp),\n")
open(home, "w").write(h)
print("flags are 10% bigger")
