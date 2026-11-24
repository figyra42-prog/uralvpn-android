package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainUpdateBannerTest {

    @Test
    fun firstUpdateRecordsCountAndBumpsEvent() {
        val state = MainUiState().withUpdatedLocations(5)
        assertEquals(5, state.updatedLocationsCount)
        assertEquals(1L, state.updatedLocationsEvent)
    }

    @Test
    fun everyUpdateBumpsEvent() {
        val state = MainUiState()
            .withUpdatedLocations(2)
            .withUpdatedLocations(7)
        assertEquals(7, state.updatedLocationsCount)
        assertEquals(2L, state.updatedLocationsEvent)
    }
}
