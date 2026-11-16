#!/usr/bin/env python3
# UralVPN: Russian apps go around the VPN by default.
# - the "China apps" scanner becomes a "Russian apps" scanner (banks, Gosuslugi, MAX, Yandex, VK, Sber,
#   marketplaces, taxi, delivery, telecom, maps);
# - on the first start the app turns on "apps bypass the VPN" (exclude mode), scans the installed apps and
#   puts the Russian ones into the list (the user can edit the list later);
# - a newly installed app that matches the list is added to the list automatically; a running VPN is reloaded.
# Usage: patch_rusapps.py <sfa-root>
import sys

root = sys.argv[1]
base = root + "/app/src/main/java/io/nekohasekai/sfa/"


def swap(text, old, new, what):
    assert text.count(old) == 1, "%s not found (%d)" % (what, text.count(old))
    return text.replace(old, new)


# Package prefixes of apps that must see no VPN (banks, state services, messengers that detect VPN,
# marketplaces, taxi, delivery, telecom, maps). A prefix matches "prefix" and "prefix.*".
RUS_PREFIXES = [
    # banks and payments
    "ru.sberbankmobile", "ru.sberbank", "com.idamob.tinkoff.android", "ru.tinkoff", "ru.tbank", "ru.vtb24.mobilebanking",
    "ru.vtb", "ru.alfabank", "ru.gazprombank", "ru.raiffeisennews", "ru.raiffeisen", "ru.rosbank", "ru.sovcombank",
    "ru.psbank", "ru.mkb", "ru.otpbank", "ru.pochtabank", "ru.rshb", "ru.akbars", "ru.uralsib", "ru.homecredit",
    "ru.mtsbank", "ru.ozon.bank", "ru.yoo.money", "ru.yandex.money", "ru.nspk", "ru.sbp", "ru.mir",
    "com.bssys", "ru.cft", "ru.ftc", "ru.bankuralsib", "ru.rsb", "ru.openbank", "ru.open", "ru.bspb", "ru.domrf",
    "ru.zenit", "ru.tochka", "ru.modulbank", "ru.pochta",
    # state
    "ru.rostel", "ru.gosuslugi", "ru.mos", "ru.nalog", "ru.fns", "ru.pfr", "ru.sfr", "ru.gibdd", "ru.mvd",
    "ru.minzdrav", "ru.emias", "ru.gosuslugi.max", "ru.oneme.app", "ru.vk.max",
    # messengers / social that detect VPN
    "com.vkontakte", "com.vk", "ru.ok", "ru.mail", "com.my.mail", "ru.rutube", "ru.kinopoisk", "ru.ivi",
    "ru.okko", "ru.more.tv", "ru.premier", "ru.start",
    # Yandex
    "ru.yandex", "com.yandex",
    # Sber ecosystem
    "ru.sber", "com.sber", "ru.megamarket", "ru.okko", "ru.sberdevices", "ru.sberbankmobile",
    # marketplaces / shops
    "ru.ozon", "ru.wildberries", "com.wildberries", "ru.lamoda", "ru.dns_shop", "ru.mvideo", "ru.eldorado",
    "ru.citilink", "ru.detmir", "ru.letu", "ru.goldapple", "ru.perekrestok", "ru.pyaterochka", "ru.x5",
    "ru.magnit", "ru.lenta", "ru.auchan", "ru.vkusvill", "ru.samokat", "ru.kuper", "ru.sbermarket", "ru.ikea",
    "ru.leroymerlin", "ru.lemanapro", "ru.hoff", "ru.fix-price", "ru.fixprice", "ru.aliexpress",
    # taxi / delivery / transport
    "ru.citymobil", "ru.delivery", "ru.dodopizza", "ru.burgerking", "ru.vkusno", "ru.rzd", "ru.tutu", "ru.aeroflot",
    "ru.s7", "ru.pobeda", "ru.ural", "ru.utair", "ru.whoosh", "ru.urent", "ru.yandex.taxi", "ru.drivee", "ru.inDrive",
    "sinet.startup.inDriver", "ru.troika", "ru.mosmetro", "ru.gorodtroika",
    # telecom
    "ru.mts", "ru.beeline", "ru.megafon", "com.megafon", "ru.tele2", "ru.yota", "ru.rt", "ru.rostelecom",
    "ru.dom", "ru.tricolor",
    # maps / misc
    "ru.dublgis", "ru.twogis", "com.twogis", "ru.avito", "com.avito", "ru.cian", "ru.domclick", "ru.hh",
    "ru.drom", "ru.auto", "ru.autoru", "ru.litres", "ru.mybook", "ru.zvuk", "ru.boosty", "ru.dzen", "com.dzen",
    "ru.vseinstrumenti", "ru.sportmaster", "ru.rendez", "ru.apteka", "ru.eapteka", "ru.zdravcity",
    "ru.tele2", "ru.mail.cloud", "ru.yandex.disk", "ru.rbc", "ru.ria", "ru.tass", "ru.lenta.news", "ru.kp",
    "ru.sportbox", "ru.matchtv", "ru.championat", "ru.sports",
]

# 1) scanner: the "China" prefix list becomes the Russian list, the names of the scanner functions stay
p = base + "compose/screen/profileoverride/PerAppProxyScreen.kt"
s = open(p).read()
s = swap(s, "object PerAppProxyScanner {\n", "object PerAppProxyScanner {\n    // UralVPN: the deep scan (dex classes) is for Chinese SDKs; the package name is enough for Russian apps\n    private const val DEEP_SCAN = false\n\n", "scanner object")
start = s.index("    private val chinaAppPrefixList =\n        listOf(\n")
end = s.index("        )\n", start) + len("        )\n")
new_list = "    private val chinaAppPrefixList =\n        listOf(\n" + "".join('            "%s",\n' % x for x in sorted(set(RUS_PREFIXES))) + "        )\n"
s = s[:start] + new_list + s[end:]
# the deep scan (dex classes) is for Chinese SDKs; for Russian apps the package name is enough and the deep scan
# is slow (hundreds of apps) and gives false positives (ru.* classes inside foreign apps). Return false after the name check.
s = swap(
    s,
    "        if (packageName.matches(chinaAppRegex)) {\n            Log.d(\"PerAppProxyScanner\", \"Match package name: $packageName\")\n            return true\n        }\n        try {",
    "        if (packageName.matches(chinaAppRegex)) {\n            Log.d(\"PerAppProxyScanner\", \"Match package name: $packageName\")\n            return true\n        }\n        if (!DEEP_SCAN) return false\n        try {",
    "deep scan cut",
)
open(p, "w").write(s)
print("scanner: Russian prefixes (%d), deep scan disabled" % len(set(RUS_PREFIXES)))

# 2) the menu item "Китайские приложения" -> "Российские приложения" (all locales: the app is Russian-only)
import glob
for f in glob.glob(root + "/app/src/main/res/values*/strings.xml"):
    t = open(f).read()
    if 'name="per_app_proxy_scan_china_apps"' in t:
        import re
        t = re.sub(r'(<string name="per_app_proxy_scan_china_apps">)[^<]*(</string>)', r"\1Российские приложения\2", t)
        t = re.sub(r'(<string name="per_app_proxy_managed_mode_description">)[^<]*(</string>)', r"\1Российские приложения автоматически идут мимо VPN\2", t)
        open(f, "w").write(t)
print("strings: menu item renamed")

# 3) the first start: exclude mode on, Russian apps scanned into the list (once, flag in the data store)
p = base + "constant/SettingsKey.kt"
s = open(p).read()
s = swap(
    s,
    '    const val PER_APP_PROXY_ENABLED = "per_app_proxy_enabled"\n',
    '    const val PER_APP_PROXY_ENABLED = "per_app_proxy_enabled"\n    const val URALVPN_RUS_APPS_SETUP_DONE = "uralvpn_rus_apps_setup_done"\n',
    "settings key",
)
open(p, "w").write(s)

p = base + "database/Settings.kt"
s = open(p).read()
s = swap(
    s,
    "    var perAppProxyEnabled by dataStore.boolean(SettingsKey.PER_APP_PROXY_ENABLED) { false }\n",
    "    var perAppProxyEnabled by dataStore.boolean(SettingsKey.PER_APP_PROXY_ENABLED) { false }\n    var uralvpnRusAppsSetupDone by dataStore.boolean(SettingsKey.URALVPN_RUS_APPS_SETUP_DONE) { false }\n",
    "settings field",
)
open(p, "w").write(s)

p = base + "Application.kt"
s = open(p).read()
s = swap(
    s,
    "            UpdateProfileWork.reconfigureUpdater()\n",
    "            UpdateProfileWork.reconfigureUpdater()\n            runCatching { io.nekohasekai.sfa.bg.AppChangeReceiver.setupRussianAppsOnce() }.onFailure {\n                Log.w(\"Application\", \"rus apps setup: ${it.message}\")\n            }\n",
    "first start setup",
)
open(p, "w").write(s)
print("first start: exclude mode + scan")

# 4) AppChangeReceiver: a newly installed Russian app joins the list (no managed mode needed), VPN reloaded
p = base + "bg/AppChangeReceiver.kt"
s = open(p).read()
s = swap(
    s,
    """                if (!Settings.perAppProxyEnabled) {
                    Log.d(TAG, "per app proxy disabled")
                    return@launch
                }
                if (!Settings.perAppProxyManagedMode) {
                    Log.d(TAG, "managed mode disabled")
                    return@launch
                }
                rescanAllApps()
                Settings.dataStore.flush()
""",
    """                if (!Settings.perAppProxyEnabled) {
                    Log.d(TAG, "per app proxy disabled")
                    return@launch
                }
                if (Settings.perAppProxyMode != Settings.PER_APP_PROXY_EXCLUDE) {
                    Log.d(TAG, "not exclude mode")
                    return@launch
                }
                val packageName = intent.data?.schemeSpecificPart ?: return@launch
                if (addRussianPackage(packageName)) {
                    Settings.dataStore.flush()
                    reloadRunningService()
                }
""",
    "receiver body",
)
s = swap(
    s,
    """    private suspend fun rescanAllApps() {
        Log.d(TAG, "rescanning all apps")
        val chinaApps = PerAppProxyScanner.scanAllChinaApps()
        Settings.perAppProxyManagedList = chinaApps
        Log.d(TAG, "rescan complete, found ${chinaApps.size} china apps")
    }
}""",
    """    private fun addRussianPackage(packageName: String): Boolean {
        val info = try {
            io.nekohasekai.sfa.Application.packageManager.getPackageInfo(packageName, 0)
        } catch (e: Exception) {
            return false
        }
        if (!PerAppProxyScanner.scanChinaPackage(info)) return false
        val list = Settings.perAppProxyList.toMutableSet()
        if (!list.add(packageName)) return false
        Settings.perAppProxyList = list
        Log.d(TAG, "russian app added to bypass list: $packageName")
        return true
    }

    private fun reloadRunningService() {
        runCatching {
            io.nekohasekai.libbox.Libbox.newStandaloneCommandClient().serviceReload()
        }.onFailure { Log.d(TAG, "service reload skipped: ${it.message}") }
    }

    companion object Setup {
        // UralVPN: on the first start the Russian apps go around the VPN (exclude mode), the user can edit the list later.
        suspend fun setupRussianAppsOnce() {
            if (Settings.uralvpnRusAppsSetupDone) return
            val found = PerAppProxyScanner.scanAllChinaApps()
            val list = Settings.perAppProxyList.toMutableSet()
            list.addAll(found)
            Settings.perAppProxyList = list
            Settings.perAppProxyMode = Settings.PER_APP_PROXY_EXCLUDE
            Settings.perAppProxyManagedMode = false
            Settings.perAppProxyEnabled = true
            Settings.uralvpnRusAppsSetupDone = true
            Settings.dataStore.flush()
            Log.d(TAG, "russian apps setup done: ${found.size} apps bypass the VPN")
        }
    }
}""",
    "receiver helpers",
)
# the class already has a companion object with TAG: merge it
s = swap(
    s,
    """    companion object {
        private const val TAG = "AppChangeReceiver"
    }
""",
    "",
    "old companion",
)
s = swap(
    s,
    "    companion object Setup {\n",
    "    companion object {\n        private const val TAG = \"AppChangeReceiver\"\n\n",
    "new companion",
)
open(p, "w").write(s)
print("receiver: new Russian apps auto-added, first start setup")
