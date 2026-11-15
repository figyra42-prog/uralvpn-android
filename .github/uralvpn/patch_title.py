#!/usr/bin/env python3
# UralVPN: the top bar of the home screen. The logo is centered, and the burger button has the word "Меню" next to it
# (a user does not always guess that three lines open the menu).
# Run: patch_title.py <sfa-root>   (called by brand.sh)
import os
import sys

root = sys.argv[1]
path = os.path.join(root, "app", "src", "main", "java", "io", "nekohasekai", "sfa", "compose", "screen", "dashboard", "UralHomeScreen.kt")
s = open(path).read()


def swap(text, old, new, what):
    assert text.count(old) == 1, "%s not found (%d)" % (what, text.count(old))
    return text.replace(old, new)


if "CenterAlignedTopAppBar(" in s:
    print("title: already patched")
    sys.exit(0)

s = swap(
    s,
    "import androidx.compose.material3.TopAppBar\n",
    "import androidx.compose.material3.CenterAlignedTopAppBar\n",
    "import",
)
# the bar itself (the title text stays as it is)
s = swap(s, "        TopAppBar(\n            title = {", "        CenterAlignedTopAppBar(\n            title = {", "bar")
# the burger button with the word
s = swap(
    s,
    "            navigationIcon = {\n"
    "                IconButton(onClick = { drawer?.let { scope.launch { it.open() } } }) {\n"
    '                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Меню")\n'
    "                }\n"
    "            },\n",
    "            navigationIcon = {\n"
    "                Row(\n"
    "                    modifier = Modifier\n"
    "                        .padding(start = 4.dp)\n"
    "                        .clip(RoundedCornerShape(24.dp))\n"
    "                        .clickable { drawer?.let { scope.launch { it.open() } } }\n"
    "                        .padding(horizontal = 10.dp, vertical = 8.dp),\n"
    "                    verticalAlignment = Alignment.CenterVertically,\n"
    "                ) {\n"
    '                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Меню")\n'
    "                    Spacer(Modifier.width(6.dp))\n"
    '                    Text("Меню", style = MaterialTheme.typography.labelLarge)\n'
    "                }\n"
    "            },\n",
    "burger",
)
open(path, "w").write(s)
print("title: patched")
