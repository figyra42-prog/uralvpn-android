#!/usr/bin/env python3
# UralVPN: side menu instead of the bottom tabs. Usage: patch_nav.py <sfa-root>
import re
import sys

root = sys.argv[1]
main = root + "/app/src/main/java/io/nekohasekai/sfa/compose/MainActivity.kt"
s = open(main).read()

# 1) the bottom navigation bar is never shown
bar = re.compile(r"(bottomBar = \{\s*)if \(!isSubScreen\) \{")
assert len(bar.findall(s)) == 1, "bottomBar not found"
s = bar.sub(lambda m: m.group(1) + "if (false) {", s)

# 2) the whole scaffold lives inside the drawer host
head = "        CompositionLocalProvider(LocalTopBarController provides topBarController) {\n"
assert s.count(head) == 1, "topbar provider not found"
s = s.replace(head, """        val uralDrawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
        UralDrawerHost(
            drawerState = uralDrawerState,
            gesturesEnabled = currentRootRoute == Screen.Dashboard.route,
            onNavigate = { route ->
                navController.navigate(route) { launchSingleTop = true }
            },
        ) {
""" + head, 1)
tail = "        }\n\n        LaunchedEffect(dashboardUiState.hasGroups) {"
assert s.count(tail) == 1, "end of scaffold not found"
s = s.replace(tail, "        }\n        }\n\n        LaunchedEffect(dashboardUiState.hasGroups) {", 1)

imp = "import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel\n"
assert imp in s, "import anchor not found"
s = s.replace(imp, imp + "import io.nekohasekai.sfa.compose.screen.dashboard.UralDrawerHost\n", 1)
open(main, "w").write(s)
print("side menu applied")
