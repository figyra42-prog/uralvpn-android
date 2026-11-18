#!/usr/bin/env python3
# UralVPN: the "Служба/Сервис" (service) screen is not shown any more: its only useful switch
# (battery optimisation) now lives in the settings list next to the theme switch.
# Usage: patch_service.py <sfa-root>
import sys

root = sys.argv[1]
nav = root + "/app/src/main/java/io/nekohasekai/sfa/compose/navigation/Navigation.kt"
s = open(nav).read()

route = '''        composable(
            route = "settings/service",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            ServiceSettingsScreen(navController = navController, serviceStatus = serviceStatus)
        }

'''
assert s.count(route) == 1, "service route not found (%d)" % s.count(route)
s = s.replace(route, "", 1)

imp = "import io.nekohasekai.sfa.compose.screen.settings.ServiceSettingsScreen\n"
assert s.count(imp) == 1, "service screen import not found"
s = s.replace(imp, "", 1)
open(nav, "w").write(s)
print("service screen removed from navigation")
