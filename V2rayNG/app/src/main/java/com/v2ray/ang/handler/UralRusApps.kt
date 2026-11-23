package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.AppManagerUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UralVPN: keeps Russian apps outside the VPN tunnel.
 *
 * Many Russian apps (banks, government services, MAX) refuse to work through a VPN, so this
 * fork selects installed packages with a known Russian prefix and stores them in the v2rayNG
 * per-app bypass list. The user can still edit the resulting list on the per-app screen.
 */
object UralRusApps {

    /** Package prefixes of Russian banks, state services, messengers, shops and telecom. */
    private val RUS_PREFIXES = listOf(
        // Banks and payments
        "ru.sberbankmobile", "ru.sberbank", "com.idamob.tinkoff.android", "ru.tinkoff",
        "ru.tbank", "ru.vtb24.mobilebanking", "ru.vtb", "ru.alfabank", "ru.gazprombank",
        "ru.raiffeisennews", "ru.raiffeisen", "ru.rosbank", "ru.sovcombank", "ru.psbank",
        "ru.mkb", "ru.otpbank", "ru.pochtabank", "ru.rshb", "ru.akbars", "ru.uralsib",
        "ru.homecredit", "ru.mtsbank", "ru.ozon.bank", "ru.yoo.money", "ru.yandex.money",
        "ru.nspk", "ru.sbp", "ru.mir", "com.bssys", "ru.cft", "ru.ftc", "ru.bankuralsib",
        "ru.rsb", "ru.openbank", "ru.open", "ru.bspb", "ru.domrf", "ru.zenit", "ru.tochka",
        "ru.modulbank", "ru.pochta",
        // State services
        "ru.rostel", "ru.gosuslugi", "ru.mos", "ru.nalog", "ru.fns", "ru.pfr", "ru.sfr",
        "ru.gibdd", "ru.mvd", "ru.minzdrav", "ru.emias", "ru.gosuslugi.max", "ru.oneme.app",
        "ru.vk.max",
        // Messengers and social networks that detect VPN
        "com.vkontakte", "com.vk", "ru.ok", "ru.mail", "com.my.mail", "ru.rutube",
        "ru.kinopoisk", "ru.ivi", "ru.okko", "ru.more.tv", "ru.premier", "ru.start",
        // Yandex
        "ru.yandex", "com.yandex",
        // Sber ecosystem
        "ru.sber", "com.sber", "ru.megamarket", "ru.sberdevices",
        // Marketplaces and shops
        "ru.ozon", "ru.wildberries", "com.wildberries", "ru.lamoda", "ru.dns_shop",
        "ru.mvideo", "ru.eldorado", "ru.citilink", "ru.detmir", "ru.letu", "ru.goldapple",
        "ru.perekrestok", "ru.pyaterochka", "ru.x5", "ru.magnit", "ru.lenta", "ru.auchan",
        "ru.vkusvill", "ru.samokat", "ru.kuper", "ru.sbermarket", "ru.ikea",
        "ru.leroymerlin", "ru.lemanapro", "ru.hoff", "ru.fix-price", "ru.fixprice",
        "ru.aliexpress",
        // Taxi, delivery and transport
        "ru.citymobil", "ru.delivery", "ru.dodopizza", "ru.burgerking", "ru.vkusno",
        "ru.rzd", "ru.tutu", "ru.aeroflot", "ru.s7", "ru.pobeda", "ru.ural", "ru.utair",
        "ru.whoosh", "ru.urent", "ru.yandex.taxi", "ru.drivee", "ru.inDrive",
        "sinet.startup.inDriver", "ru.troika", "ru.mosmetro", "ru.gorodtroika",
        // Telecom
        "ru.mts", "ru.beeline", "ru.megafon", "com.megafon", "ru.tele2", "ru.yota", "ru.rt",
        "ru.rostelecom", "ru.dom", "ru.tricolor",
        // Maps, news, classifieds and misc
        "ru.dublgis", "ru.twogis", "com.twogis", "ru.avito", "com.avito", "ru.cian",
        "ru.domclick", "ru.hh", "ru.drom", "ru.auto", "ru.autoru", "ru.litres", "ru.mybook",
        "ru.zvuk", "ru.boosty", "ru.dzen", "com.dzen", "ru.vseinstrumenti", "ru.sportmaster",
        "ru.rendez", "ru.apteka", "ru.eapteka", "ru.zdravcity", "ru.mail.cloud",
        "ru.yandex.disk", "ru.rbc", "ru.ria", "ru.tass", "ru.lenta.news", "ru.kp",
        "ru.sportbox", "ru.matchtv", "ru.championat", "ru.sports"
    )

    /** True when [packageName] belongs to a known Russian application. */
    fun isRussianPackage(packageName: String): Boolean =
        RUS_PREFIXES.any { packageName.startsWith(it) }

    /** Whether the automatic Russian-apps bypass is enabled. */
    fun isBypassEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_URALVPN_RUS_APPS, false)

    /**
     * Enables the bypass once after install; later launches keep the user's choice.
     */
    suspend fun setupOnce(context: Context) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_URALVPN_RUS_APPS_SETUP_DONE, false)) {
            return
        }
        setBypass(context, true)
    }

    /**
     * Adds ([enabled] = true) or removes installed Russian apps from the per-app bypass list.
     * Enabling also switches per-app proxy on in bypass mode.
     *
     * @return number of installed Russian packages seen during this call.
     */
    suspend fun setBypass(context: Context, enabled: Boolean): Int = withContext(Dispatchers.IO) {
        val scanned = scanInstalled(context.applicationContext)
        val current = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
            ?.toSet()
            ?: emptySet()
        val updated = mergeSelection(current, scanned, enabled)
        MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, updated.toMutableSet())
        if (enabled) {
            MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY, true)
            MmkvManager.encodeSettings(AppConfig.PREF_BYPASS_APPS, true)
        }
        MmkvManager.encodeSettings(AppConfig.PREF_URALVPN_RUS_APPS, enabled)
        MmkvManager.encodeSettings(AppConfig.PREF_URALVPN_RUS_APPS_SETUP_DONE, true)
        scanned.size
    }

    /**
     * Adds one newly installed package when the automatic bypass is active.
     * Called from the package-added receiver; every storage operation is a synchronous MMKV call.
     *
     * @return true when the package was added and a tunnel restart is useful.
     */
    fun addInstalledPackage(context: Context, packageName: String): Boolean {
        if (!isBypassEnabled()) return false
        if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_BYPASS_APPS, false)) return false
        if (packageName == context.packageName || !isRussianPackage(packageName)) return false
        val current = MmkvManager.decodeSettingsStringSet(AppConfig.PREF_PER_APP_PROXY_SET)
            ?: mutableSetOf()
        if (!current.add(packageName)) return false
        return MmkvManager.encodeSettings(AppConfig.PREF_PER_APP_PROXY_SET, current)
    }

    /** Pure selection merge used by [setBypass] and its unit tests. */
    internal fun mergeSelection(
        current: Set<String>,
        scanned: Set<String>,
        enabled: Boolean
    ): Set<String> = if (enabled) current + scanned else current - scanned

    private suspend fun scanInstalled(context: Context): Set<String> =
        AppManagerUtil.loadNetworkAppList(context)
            .asSequence()
            .map { it.packageName }
            .filter { it != context.packageName && isRussianPackage(it) }
            .toSet()
}
