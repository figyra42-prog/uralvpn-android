#!/usr/bin/env python3
# UralVPN: the subscription card shows when the subscription was last updated (also by the background
# refresh), so that the background update can be seen: "Обновлено: сегодня 14:32".
# Usage: patch_card.py <sfa-root>
import sys

root = sys.argv[1]
home = root + "/app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/UralHomeScreen.kt"
h = open(home).read()

sig = "    info: SubInfo,\n    fallbackTitle: String,\n    collapsed: Boolean,\n"
assert h.count(sig) == 1, "card signature not found"
h = h.replace(sig, "    info: SubInfo,\n    fallbackTitle: String,\n    lastUpdated: Long,\n    collapsed: Boolean,\n", 1)

call = "                    info = value,\n                    fallbackTitle = profile?.name.orEmpty(),\n"
assert h.count(call) == 1, "card call not found"
h = h.replace(call, call + "                    lastUpdated = profile?.typed?.lastUpdated?.time ?: 0L,\n", 1)

anchor = "                info.fraction?.let { fraction ->\n"
assert h.count(anchor) == 1, "fraction anchor not found"
h = h.replace(anchor, """                if (lastUpdated > 0L) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Обновлено: " + uralUpdatedAt(lastUpdated),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
""" + anchor, 1)

h += '''
// "сегодня 14:32" / "вчера 09:05" / "12.11 21:40" in the phone's time zone
private fun uralUpdatedAt(millis: Long): String {
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
    val then = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val now = java.util.Calendar.getInstance()
    val days = (now.get(java.util.Calendar.YEAR) * 366 + now.get(java.util.Calendar.DAY_OF_YEAR)) -
        (then.get(java.util.Calendar.YEAR) * 366 + then.get(java.util.Calendar.DAY_OF_YEAR))
    return when (days) {
        0 -> "сегодня $time"
        1 -> "вчера $time"
        else -> java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault()).format(java.util.Date(millis)) + " " + time
    }
}
'''
open(home, "w").write(h)
print("last update time in the card")
