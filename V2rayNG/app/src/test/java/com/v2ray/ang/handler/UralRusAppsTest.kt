package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UralRusAppsTest {

    @Test
    fun matchesRussianPackages() {
        assertTrue(UralRusApps.isRussianPackage("ru.sberbankonline"))
        assertTrue(UralRusApps.isRussianPackage("com.idamob.tinkoff.android"))
        assertTrue(UralRusApps.isRussianPackage("ru.yandex.taxi"))
        assertTrue(UralRusApps.isRussianPackage("com.vkontakte.android"))
        assertTrue(UralRusApps.isRussianPackage("ru.ozon"))
    }

    @Test
    fun ignoresForeignAndOwnPackages() {
        assertFalse(UralRusApps.isRussianPackage(""))
        assertFalse(UralRusApps.isRussianPackage("com.google.android.gm"))
        assertFalse(UralRusApps.isRussianPackage("org.telegram.messenger"))
        assertFalse(UralRusApps.isRussianPackage("app.uralvpn.xray"))
        assertFalse(UralRusApps.isRussianPackage("com.example.ru.yandex"))
    }

    @Test
    fun mergeSelectionAddsScannedPackagesOnEnable() {
        val current = setOf("com.example.manual")
        val scanned = setOf("ru.ozon", "ru.sberbank")

        assertEquals(
            setOf("com.example.manual", "ru.ozon", "ru.sberbank"),
            UralRusApps.mergeSelection(current, scanned, enabled = true)
        )
    }

    @Test
    fun mergeSelectionRemovesOnlyScannedPackagesOnDisable() {
        val current = setOf("com.example.manual", "ru.ozon", "ru.gone")
        val scanned = setOf("ru.ozon", "ru.sberbank")

        assertEquals(
            setOf("com.example.manual", "ru.gone"),
            UralRusApps.mergeSelection(current, scanned, enabled = false)
        )
    }

    @Test
    fun mergeSelectionIsIdempotent() {
        val current = setOf("ru.ozon")

        assertEquals(current, UralRusApps.mergeSelection(current, current, enabled = true))
        assertEquals(emptySet<String>(), UralRusApps.mergeSelection(current, current, enabled = false))
    }
}
