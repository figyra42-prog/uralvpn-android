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

# 6) the icons of the subscription card (support, site, ping, sort, collapse) are white and a bit
#    bigger: the default tint looked gray. The sort icon stays lime while sorting by ping is on.
home = root + "/app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/UralHomeScreen.kt"
h = open(home).read()
plain = re.compile(r'Icon\(Icons\.Default\.(SupportAgent|Info|Speed), contentDescription = ("[^"\n]*"), modifier = Modifier\.size\(22\.dp\)\)')
assert len(plain.findall(h)) == 3, "card icons not found"
h = plain.sub(lambda m: 'Icon(Icons.Default.%s, contentDescription = %s, modifier = Modifier.size(24.dp), tint = Color.White)' % (m.group(1), m.group(2)), h)
sort = re.compile(r'(imageVector = Icons\.Default\.SwapVert,\s*contentDescription = "[^"\n]*",\s*modifier = Modifier\.size\()22\.dp(\),\s*tint = if \(sortByPing\) MaterialTheme\.colorScheme\.primary else )MaterialTheme\.colorScheme\.onSurfaceVariant,')
assert len(sort.findall(h)) == 1, "sort icon not found"
h = sort.sub(lambda m: m.group(1) + "24.dp" + m.group(2) + "Color.White,", h)
fold = re.compile(r'(imageVector = if \(collapsed\) Icons\.Default\.ExpandMore else Icons\.Default\.ExpandLess,\s*contentDescription = if \(collapsed\) "[^"\n]*" else "[^"\n]*",\s*modifier = Modifier\.size\()22\.dp\),')
assert len(fold.findall(h)) == 1, "collapse icon not found"
h = fold.sub(lambda m: m.group(1) + "24.dp), tint = Color.White,", h)
open(home, "w").write(h)
print("card icons are white")

# 7) a long press on a location shows its config (JSON of the outbound) with the secrets hidden,
#    to compare the servers with the other apps (Happ shows the same behind the arrow)
h = open(home).read()


def swap(text, old, new, what):
    assert text.count(old) == 1, what + " not found"
    return text.replace(old, new, 1)


h = swap(h, "import androidx.compose.foundation.layout.Box\n",
         "import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.ExperimentalFoundationApi\nimport androidx.compose.foundation.combinedClickable\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\n",
         "Box import")
h = swap(h, "    var toDelete by remember { mutableStateOf<Profile?>(null) }\n",
         "    var toDelete by remember { mutableStateOf<Profile?>(null) }\n    var configTag by remember { mutableStateOf<String?>(null) }\n",
         "toDelete state")
h = swap(h, "    val scaffoldPadding = LocalScaffoldPadding.current\n    LazyColumn(",
         """    configTag?.let { tag ->
        val json = remember(tag, profilePath) { uralOutboundJson(profilePath, tag) }
        AlertDialog(
            onDismissRequest = { configTag = null },
            title = { Text(tag) },
            text = {
                Text(
                    json,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    manager.setPrimaryClip(android.content.ClipData.newPlainText("config", json))
                    android.widget.Toast.makeText(context, "Скопировано (ключи скрыты)", android.widget.Toast.LENGTH_SHORT).show()
                    configTag = null
                }) { Text("Копировать") }
            },
            dismissButton = { TextButton(onClick = { configTag = null }) { Text("Закрыть") } },
        )
    }

    val scaffoldPadding = LocalScaffoldPadding.current
    LazyColumn(""", "scaffold padding")
h = swap(h, "            UralLocationRow(\n                row,\n                enabled = running && group != null,\n            ) {",
         "            UralLocationRow(\n                row,\n                enabled = running && group != null,\n                onLongClick = { configTag = row.tag },\n            ) {",
         "location row call")
h = swap(h, "@Composable\nprivate fun UralLocationRow(row: LocationRow, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {",
         "@OptIn(ExperimentalFoundationApi::class)\n@Composable\nprivate fun UralLocationRow(row: LocationRow, enabled: Boolean, modifier: Modifier = Modifier, onLongClick: () -> Unit = {}, onClick: () -> Unit) {",
         "location row signature")
h = swap(h, ".clickable(enabled = enabled, onClick = onClick)",
         ".combinedClickable(onClick = { if (enabled) onClick() }, onLongClick = onLongClick)",
         "location row click")
h += '''
// Config of one location for the long-press dialog: the outbound JSON of the profile, the secrets
// (uuid, passwords, keys, short id) replaced with ***.
private val uralSecretKeys = setOf(
    "uuid", "password", "private_key", "public_key", "short_id", "pre_shared_key", "psk",
    "auth", "auth_str", "token", "key", "secret", "username",
)

private fun uralMask(value: Any?): Any? = when (value) {
    is org.json.JSONObject -> org.json.JSONObject().also { out ->
        value.keys().forEach { k ->
            val inner = value.opt(k)
            val scalar = inner !is org.json.JSONObject && inner !is org.json.JSONArray
            out.put(k, if (scalar && k.lowercase() in uralSecretKeys) "***" else uralMask(inner))
        }
    }
    is org.json.JSONArray -> org.json.JSONArray().also { out ->
        for (i in 0 until value.length()) out.put(uralMask(value.opt(i)))
    }
    else -> value
}

private fun uralOutboundJson(path: String, tag: String): String = runCatching {
    val outbounds = org.json.JSONObject(java.io.File(path).readText()).getJSONArray("outbounds")
    for (i in 0 until outbounds.length()) {
        val outbound = outbounds.getJSONObject(i)
        if (outbound.optString("tag") == tag) {
            return@runCatching (uralMask(outbound) as org.json.JSONObject).toString(2).replace("\\\\/", "/")
        }
    }
    "Конфигурация не найдена"
}.getOrElse { "Не удалось прочитать конфигурацию: " + (it.message ?: it.javaClass.simpleName) }
'''
open(home, "w").write(h)
print("long press shows the location config")
