#!/usr/bin/env python3
# UralVPN: the rows of the locations are a bit higher (vertical padding 4 -> 8 dp) and the dividers are a bit
# wider: thicker (1 -> 1.5 dp) and longer (indent from the edge 16 -> 10 dp).
# Usage: patch_rows.py <sfa-root>
import sys

root = sys.argv[1]
home = root + "/app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/UralHomeScreen.kt"
h = open(home).read()


def swap(text, old, new, what):
    assert text.count(old) == 1, "%s not found (%d)" % (what, text.count(old))
    return text.replace(old, new)


h = swap(h, "            .padding(horizontal = 14.dp, vertical = 4.dp),\n", "            .padding(horizontal = 14.dp, vertical = 8.dp),\n", "row padding")
h = swap(
    h,
    "drawLine(divider, androidx.compose.ui.geometry.Offset(16.dp.toPx(), size.height - 0.5.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width - 16.dp.toPx(), size.height - 0.5.dp.toPx()), 1.dp.toPx())",
    "drawLine(divider, androidx.compose.ui.geometry.Offset(10.dp.toPx(), size.height - 0.75.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width - 10.dp.toPx(), size.height - 0.75.dp.toPx()), 1.5.dp.toPx())",
    "divider line",
)
open(home, "w").write(h)
print("rows are higher, dividers are wider")
