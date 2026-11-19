#!/usr/bin/env python3
"""Экран «Трафик по приложениям»: маршрут в навигации (вызывается из brand.sh)."""
import sys

root = sys.argv[1]
path = root + "/app/src/main/java/io/nekohasekai/sfa/compose/navigation/Navigation.kt"
s = open(path, encoding="utf-8").read()

if 'composable("ural/stats")' in s:
    print("stats: already patched")
    raise SystemExit(0)

anchor = "        composable(Screen.Settings.route) {"
assert s.count(anchor) == 1, "settings route anchor"
block = """        composable("ural/stats") {
            UralStatsScreen(serviceStatus = serviceStatus)
        }

"""
s = s.replace(anchor, block + anchor, 1)

imp = "import io.nekohasekai.sfa.compose.navigation.Screen\n"
assert s.count(imp) == 1, "import anchor"
s = s.replace(imp, imp + "import io.nekohasekai.sfa.compose.screen.stats.UralStatsScreen\n", 1)
open(path, "w", encoding="utf-8").write(s)
print("stats: route added")
