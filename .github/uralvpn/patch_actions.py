#!/usr/bin/env python3
# UralVPN: the buttons of the home screen.
# - the "+" (add a subscription) is bigger and has the colour of the text (white on the dark theme, dark on the light one)
#   instead of the grey of the top bar actions;
# - the "refresh the subscription" button leaves the top bar and stands in the subscription card next to the ping button.
# Run: patch_actions.py <sfa-root>   (called by brand.sh after patch_card.py and patch_theme.py)
import os
import sys

root = sys.argv[1]
path = os.path.join(root, "app", "src", "main", "java", "io", "nekohasekai", "sfa", "compose", "screen", "dashboard", "UralHomeScreen.kt")
s = open(path).read()


def swap(text, old, new, what):
    assert text.count(old) == 1, "%s not found (%d)" % (what, text.count(old))
    return text.replace(old, new)


if "refreshEnabled" in s:
    print("actions: already patched")
    sys.exit(0)

# 1) the refresh button is removed from the top bar
s = swap(
    s,
    "                if (profile != null && remoteUrl.isNotEmpty()) {\n"
    "                    IconButton(onClick = {\n"
    "                        dashboardViewModel.updateProfile(profile)\n"
    "                        refreshTick++\n"
    '                    }) { Icon(Icons.Default.Refresh, contentDescription = "Обновить подписку") }\n'
    "                }\n",
    "",
    "top bar refresh",
)

# 2) the "+" is bigger and in the colour of the text
s = swap(
    s,
    "                    IconButton(onClick = { addMenuOpen = true }) {\n"
    '                        Icon(Icons.Default.Add, contentDescription = "Добавить подписку")\n'
    "                    }\n",
    "                    IconButton(onClick = { addMenuOpen = true }, modifier = Modifier.size(48.dp)) {\n"
    "                        Icon(\n"
    "                            Icons.Default.Add,\n"
    '                            contentDescription = "Добавить подписку",\n'
    "                            tint = MaterialTheme.colorScheme.onSurface,\n"
    "                            modifier = Modifier.size(34.dp),\n"
    "                        )\n"
    "                    }\n",
    "add button",
)

# 3) the card gets the refresh button (the parameters and the call)
s = swap(
    s,
    "    onPing: () -> Unit,\n",
    "    onPing: () -> Unit,\n    refreshEnabled: Boolean,\n    onRefresh: () -> Unit,\n",
    "card signature",
)
s = swap(
    s,
    "                    onPing = { group?.let { current ->\n"
    "                        pingVisible = true\n"
    "                        groupsViewModel.urlTestGroup(current.tag)\n"
    "                        scope.launch {\n"
    "                            delay(1500)\n"
    "                            pingVisible = false\n"
    "                        }\n"
    "                    } },\n",
    "                    onPing = { group?.let { current ->\n"
    "                        pingVisible = true\n"
    "                        groupsViewModel.urlTestGroup(current.tag)\n"
    "                        scope.launch {\n"
    "                            delay(1500)\n"
    "                            pingVisible = false\n"
    "                        }\n"
    "                    } },\n"
    "                    refreshEnabled = profile != null && remoteUrl.isNotEmpty(),\n"
    "                    onRefresh = {\n"
    "                        if (profile != null) {\n"
    "                            dashboardViewModel.updateProfile(profile)\n"
    "                            refreshTick++\n"
    "                        }\n"
    "                    },\n",
    "card call",
)
s = swap(
    s,
    "                IconButton(onClick = onToggleSort, modifier = Modifier.size(36.dp)) {\n",
    "                IconButton(onClick = onRefresh, enabled = refreshEnabled, modifier = Modifier.size(36.dp)) {\n"
    "                    Icon(\n"
    "                        Icons.Default.Refresh,\n"
    '                        contentDescription = "Обновить подписку",\n'
    "                        modifier = Modifier.size(22.dp),\n"
    "                        tint = MaterialTheme.colorScheme.onSurface,\n"
    "                    )\n"
    "                }\n"
    "                IconButton(onClick = onToggleSort, modifier = Modifier.size(36.dp)) {\n",
    "card refresh button",
)
open(path, "w").write(s)
print("actions: patched")
