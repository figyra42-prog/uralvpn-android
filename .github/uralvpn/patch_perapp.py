#!/usr/bin/env python3
# UralVPN: the app selection screen "Приложения (обход VPN)".
# - the title is "Приложения (обход VPN)" (on both screens);
# - a visible switch of the mode under the title (before it was hidden in the three-dots menu):
#   "Выбранные идут мимо VPN" (exclude) / "Только выбранные идут через VPN" (include);
# - the mode descriptions are in Russian.
# Usage: patch_perapp.py <sfa-root>
import sys

root = sys.argv[1]
base = root + "/app/src/main/java/io/nekohasekai/sfa/compose/screen/"


def swap(text, old, new, what):
    assert text.count(old) == 1, "%s not found (%d)" % (what, text.count(old))
    return text.replace(old, new)


TITLE = "Приложения (обход VPN)"

# 1) screen with the list of apps
p = base + "profileoverride/PerAppProxyScreen.kt"
s = open(p).read()
s = swap(s, "title = { Text(stringResource(R.string.per_app_proxy)) },", 'title = { Text("%s") },' % TITLE, "app list title")
s = swap(
    s,
    "                    stringResource(R.string.per_app_proxy_mode_include_description)\n",
    '                    "Через VPN пойдут только выбранные приложения, всё остальное напрямую"\n',
    "include description",
)
s = swap(
    s,
    "                    stringResource(R.string.per_app_proxy_mode_exclude_description)\n",
    '                    "Выбранные приложения пойдут мимо VPN, всё остальное через VPN"\n',
    "exclude description",
)
anchor = "        AnimatedVisibility(\n            visible = isSearchActive,\n"
s = swap(
    s,
    anchor,
    """        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                Settings.PER_APP_PROXY_EXCLUDE to "Выбранные мимо VPN",
                Settings.PER_APP_PROXY_INCLUDE to "Только выбранные через VPN",
            ).forEach { (value, label) ->
                androidx.compose.material3.FilterChip(
                    selected = proxyMode == value,
                    onClick = {
                        if (proxyMode != value) {
                            proxyMode = value
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    Settings.perAppProxyMode = value
                                }
                                notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Reload)
                            }
                        }
                    },
                    label = { Text(label, maxLines = 1) },
                )
            }
        }

"""
    + anchor,
    "search animated visibility",
)
open(p, "w").write(s)
print("app list: title, descriptions, visible mode switch")

# 2) settings entry screen
p = base + "settings/ProfileOverrideScreen.kt"
s = open(p).read()
s = swap(s, "title = { Text(stringResource(R.string.profile_override)) },", 'title = { Text("%s") },' % TITLE, "override title")
s = swap(s, "            text = stringResource(R.string.per_app_proxy),\n            style = MaterialTheme.typography.labelLarge,", '            text = "Выбор приложений",\n            style = MaterialTheme.typography.labelLarge,', "section label")
open(p, "w").write(s)
print("override screen: title")
