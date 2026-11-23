package com.v2ray.ang.ui.main

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelStartOnBootTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `start on boot action updates state and data source`() {
        runTest {
            val application = mock<Application>()
            val dataSource = mock<MainDataSource> {
                on { mainServiceEvent } doReturn emptyFlow<MainServiceEvent>()
                on { getSelectedSubscriptionId() } doReturn "__default_subscription__"
                on { getSelectServer() } doReturn null
                on { getConfirmRemove() } doReturn false
                on { getDoubleColumnDisplay() } doReturn false
                on { getKillSwitch() } doReturn false
                on { getStartOnBoot() } doReturn false
                on { isSortByPingEnabled() } doReturn false
                on { getSubscriptions() } doReturn emptyList()
            }

            val viewModel = MainViewModel(application, dataSource)
            assertFalse(viewModel.uiState.value.startOnBoot)

            viewModel.onAction(MainAction.SetStartOnBoot(true))

            assertTrue(viewModel.uiState.value.startOnBoot)
            verify(dataSource).setStartOnBoot(true)
        }
    }
}
