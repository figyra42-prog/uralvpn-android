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

# 3) no bottom status panel (the big connect button does that job) and no "deprecated option"
#    pop-ups: they come from the panel's sing-box profile, which the core still understands
bar2 = "val showStatusBar = isRemote || serviceRunning || currentServiceStatus == Status.Stopping"
s = open(main).read()
assert s.count(bar2) == 1, "showStatusBar not found"
s = s.replace(bar2, "val showStatusBar = false")
dep = "if (dashboardUiState.showDeprecatedDialog && dashboardUiState.deprecatedNotes.isNotEmpty()) {"
assert s.count(dep) == 1, "deprecated dialog not found"
s = s.replace(dep, "if (false && dashboardUiState.showDeprecatedDialog && dashboardUiState.deprecatedNotes.isNotEmpty()) {")
open(main, "w").write(s)
print("status bar and deprecated dialog hidden")

# 4) adding a subscription: a scanned https link is accepted (SFA takes only sing-box:// links),
#    and after the profile is created we go back to the home screen instead of the profile editor
qr = root + "/app/src/main/java/io/nekohasekai/sfa/compose/screen/qrscan/QRScanViewModel.kt"
q = open(qr).read()
old = """            val uri = Uri.parse(value)
            if (uri.scheme != "sing-box" || uri.host != "import-remote-profile") {"""
new = """            var uri = Uri.parse(value.trim())
            if (uri.scheme == "http" || uri.scheme == "https") {
                uri = Uri.parse("sing-box://import-remote-profile?url=" + Uri.encode(value.trim()) + "#" + Uri.encode("UralVPN"))
            }
            if (uri.scheme != "sing-box" || uri.host != "import-remote-profile") {"""
assert q.count(old) == 1, "QR processQRCode not found"
open(qr, "w").write(q.replace(old, new, 1))

nav = root + "/app/src/main/java/io/nekohasekai/sfa/compose/navigation/Navigation.kt"
n = open(nav).read()
old = """                onProfileCreated = { profileId ->
                    onClearNewProfileArgs()
                    navController.navigate(ProfileRoutes.editProfile(profileId)) {
                        popUpTo(ProfileRoutes.NewProfile) {
                            inclusive = true
                        }
                    }
                },"""
new = """                onProfileCreated = { _ ->
                    onClearNewProfileArgs()
                    navController.navigateUp()
                },"""
assert n.count(old) == 1, "onProfileCreated not found"
open(nav, "w").write(n.replace(old, new, 1))
print("add-subscription flow applied")

# 5) autostart: the device boot starts the VPN when the "start with the device" switch is on
#    (the original starts it only if it was running before the reboot)
boot = root + "/app/src/main/java/io/nekohasekai/sfa/bg/BootReceiver.kt"
b = open(boot).read()
old = "                if (Settings.startedByUser) {"
assert b.count(old) == 1, "BootReceiver condition not found"
open(boot, "w").write(b.replace(old, "                if (UralAutostart.enabled) {", 1))
print("autostart applied")
