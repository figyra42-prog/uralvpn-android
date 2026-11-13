#!/usr/bin/env python3
# UralVPN: the text of the location config dialog (long press on a location) can be selected, so a
# part of it can be copied. Usage: patch_select.py <sfa-root>
import sys

root = sys.argv[1]
home = root + "/app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/UralHomeScreen.kt"
h = open(home).read()

old = """            text = {
                Text(
                    json,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },"""
new = """            text = {
                androidx.compose.foundation.text.selection.SelectionContainer(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        json,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            },"""
assert h.count(old) == 1, "config dialog text not found"
open(home, "w").write(h.replace(old, new, 1))
print("config text is selectable")
