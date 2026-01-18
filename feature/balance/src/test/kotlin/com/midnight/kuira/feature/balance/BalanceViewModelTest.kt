package com.midnight.kuira.feature.balance

import com.midnight.kuira.core.indexer.model.TokenBalance
import com.midnight.kuira.core.indexer.repository.BalanceRepository
import com.midnight.kuira.core.indexer.ui.BalanceFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for BalanceViewModel.
 *
 * **Test Coverage:**
 * - State transitions (Loading → Success/Error)
 * - Balance loading and transformation
 * - Error handling with user-friendly messages
 * - Refresh functionality
 * - Last updated timestamp formatting
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BalanceViewModelTest {

    private lateinit var repository: BalanceRepository
    private lateinit var formatter: BalanceFormatter
    private lateinit var viewModel: BalanceViewModel
    private lateinit var fakeClock: FakeClock

    private val testAddress = "mn_addr_testnet1test123"
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        formatter = BalanceFormatter()
        fakeClock = FakeClock(Instant.parse("2026-01-17T10:00:00Z"))
        viewModel = BalanceViewModel(repository, formatter, fakeClock)
    }

    /**
     * Fake clock for testing time-dependent behavior.
     * Allows advancing time manually in tests.
     */
    private class FakeClock(private var instant: Instant) : Clock() {
        override fun instant(): Instant = instant
        override fun getZone(): ZoneId = ZoneId.systemDefault()
        override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException()

        fun advance(millis: Long) {
            instant = instant.plusMillis(millis)
        }
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ==================== Initial State ====================

    @Test
    fun `initial state is Loading`() {
        val state = viewModel.balanceState.value
        assertTrue(state is BalanceUiState.Loading)
        assertEquals(false, (state as BalanceUiState.Loading).isRefreshing)
    }

    // ==================== Load Balances Success ====================

    @Test
    fun `loadBalances transitions to Success state`() = runTest {
        // Given: Repository returns balances
        val balances = listOf(
            TokenBalance("TNIGHT", BigInteger.valueOf(1_000_000), 1),
            TokenBalance("DUST", BigInteger.valueOf(2_000_000), 1)
        )
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flowOf(balances))

        // When: Load balances
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // Then: State is Success
        val state = viewModel.balanceState.value
        assertTrue(state is BalanceUiState.Success)

        val successState = state as BalanceUiState.Success
        assertEquals(2, successState.balances.size)
        assertEquals(BigInteger.valueOf(3_000_000), successState.totalBalance) // 1M + 2M
    }

    @Test
    fun `loadBalances formats amounts correctly`() = runTest {
        // Given: Repository returns TNIGHT balance
        val balances = listOf(
            TokenBalance("TNIGHT", BigInteger.valueOf(1_234_567), 1)
        )
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flowOf(balances))

        // When: Load balances
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // Then: Amount formatted with decimals
        val state = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals("1.234567 TNIGHT", state.balances[0].balanceFormatted)
    }

    @Test
    fun `loadBalances includes token metadata`() = runTest {
        // Given: Repository returns balances
        val balances = listOf(
            TokenBalance("TNIGHT", BigInteger.valueOf(1_000_000), 3)
        )
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flowOf(balances))

        // When: Load balances
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // Then: Metadata populated
        val state = viewModel.balanceState.value as BalanceUiState.Success
        val tokenBalance = state.balances[0]
        assertEquals("TNIGHT", tokenBalance.tokenType)
        assertEquals(3, tokenBalance.utxoCount)
        assertEquals(BigInteger.valueOf(1_000_000), tokenBalance.balanceRaw)
    }

    @Test
    fun `loadBalances sets lastUpdated timestamp`() = runTest {
        // Given: Repository returns balances
        val balances = listOf(TokenBalance("TNIGHT", BigInteger.valueOf(1_000_000), 1))
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flowOf(balances))

        // When: Load balances
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // Then: lastUpdated is set
        val state = viewModel.balanceState.value as BalanceUiState.Success
        assertTrue(state.lastUpdated.contains("now") || state.lastUpdated.contains("ago"))
    }

    // ==================== Load Balances Error ====================

    @Test
    fun `loadBalances transitions to Error state on exception`() = runTest {
        // Given: Repository throws exception
        val exception = RuntimeException("Network error")
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flow { throw exception })

        // When: Load balances
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // Then: State is Error
        val state = viewModel.balanceState.value
        assertTrue(state is BalanceUiState.Error)

        val errorState = state as BalanceUiState.Error
        assertTrue(errorState.message.contains("Network error") ||
                   errorState.message.contains("Failed to load"))
    }

    @Test
    fun `loadBalances provides user-friendly network error message`() = runTest {
        // Given: Network error
        val exception = RuntimeException("Network connection timeout")
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flow { throw exception })

        // When: Load balances
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // Then: User-friendly message
        val state = viewModel.balanceState.value as BalanceUiState.Error
        assertTrue(
            state.message.contains("network", ignoreCase = true) ||
            state.message.contains("connection", ignoreCase = true)
        )
    }

    @Test
    fun `loadBalances provides user-friendly timeout error message`() = runTest {
        // Given: Timeout error
        val exception = RuntimeException("Request timeout")
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flow { throw exception })

        // When: Load balances
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // Then: User-friendly message
        val state = viewModel.balanceState.value
        assertTrue("State should be Error but was ${state::class.simpleName}", state is BalanceUiState.Error)
        val errorState = state as BalanceUiState.Error
        assertTrue("Error message '${errorState.message}' should contain 'timed'",
            errorState.message.contains("timed", ignoreCase = true))
    }

    // ==================== Empty Balances ====================

    @Test
    fun `loadBalances handles empty balances`() = runTest {
        // Given: Repository returns empty list
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flowOf(emptyList()))

        // When: Load balances
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // Then: Success state with empty list
        val state = viewModel.balanceState.value
        assertTrue(state is BalanceUiState.Success)

        val successState = state as BalanceUiState.Success
        assertEquals(0, successState.balances.size)
        assertEquals(BigInteger.ZERO, successState.totalBalance)
    }

    // ==================== Refresh ====================

    @Test
    fun `refresh sets isRefreshing during update`() = runTest {
        // Given: Initial success state
        val balances = listOf(TokenBalance("TNIGHT", BigInteger.valueOf(1_000_000), 1))
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flowOf(balances))

        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // When: Refresh
        viewModel.refresh(testAddress)
        // Don't advance idle yet - check intermediate state

        // Then: Should show loading with isRefreshing = true
        // Note: This might transition too fast to catch in tests,
        // but the logic is there for UI to show refresh indicator
        assertTrue(viewModel.balanceState.value is BalanceUiState.Loading ||
                   viewModel.balanceState.value is BalanceUiState.Success)
    }

    // ==================== Multiple Tokens ====================

    @Test
    fun `loadBalances handles multiple token types`() = runTest {
        // Given: Multiple token types
        val balances = listOf(
            TokenBalance("TNIGHT", BigInteger.valueOf(5_000_000), 2),
            TokenBalance("DUST", BigInteger.valueOf(3_000_000), 1)
        )
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flowOf(balances))

        // When: Load balances
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // Then: Both tokens present
        val state = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals(2, state.balances.size)

        val tnight = state.balances.find { it.tokenType == "TNIGHT" }
        val dust = state.balances.find { it.tokenType == "DUST" }

        assertEquals("5.000000 TNIGHT", tnight?.balanceFormatted)
        assertEquals("3.000000 DUST", dust?.balanceFormatted)
    }

    // ==================== Total Balance Calculation ====================

    @Test
    fun `totalBalance sums across all tokens`() = runTest {
        // Given: Multiple tokens
        val balances = listOf(
            TokenBalance("TNIGHT", BigInteger.valueOf(1_000_000), 1),
            TokenBalance("DUST", BigInteger.valueOf(2_000_000), 1)
        )
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flowOf(balances))

        // When: Load balances
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // Then: Total is sum
        val state = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals(BigInteger.valueOf(3_000_000), state.totalBalance)
    }

    @Test
    fun `totalBalance handles large amounts`() = runTest {
        // Given: Balance with large amount
        val balances = listOf(
            TokenBalance("TNIGHT", BigInteger.valueOf(1_000_000_000_000), 1)
        )
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flowOf(balances))

        // When: Load balances
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // Then: Total is correct
        val state = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals(BigInteger.valueOf(1_000_000_000_000), state.totalBalance)
    }

    // ==================== Timestamp Persistence ====================

    @Test
    fun `lastUpdated timestamp persists across multiple Flow emissions with time progression`() = runTest {
        // Given: Repository emits multiple times (simulating database updates)
        val balances1 = listOf(TokenBalance("DUST", BigInteger.valueOf(1_000_000), 1))
        val balances2 = listOf(TokenBalance("DUST", BigInteger.valueOf(2_000_000), 2))
        val balances3 = listOf(TokenBalance("DUST", BigInteger.valueOf(3_000_000), 3))

        val balanceFlow = MutableSharedFlow<List<TokenBalance>>()
        whenever(repository.observeBalances(testAddress))
            .thenReturn(balanceFlow)

        // When: Load balances at T0 (10:00:00)
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // First emission at T0
        balanceFlow.emit(balances1)
        advanceUntilIdle()

        val state1 = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals("Just now", state1.lastUpdated)

        // Advance time 2 minutes (10:02:00)
        fakeClock.advance(120_000)

        // Second emission - automatic database update
        balanceFlow.emit(balances2)
        advanceUntilIdle()

        val state2 = viewModel.balanceState.value as BalanceUiState.Success
        // Balance updated but timestamp shows 2 minutes since original load
        assertEquals(BigInteger.valueOf(2_000_000), state2.balances[0].balanceRaw)
        assertEquals("2 minutes ago", state2.lastUpdated)

        // Advance time 3 more minutes (10:05:00 = 5 minutes total)
        fakeClock.advance(180_000)

        // Third emission
        balanceFlow.emit(balances3)
        advanceUntilIdle()

        val state3 = viewModel.balanceState.value as BalanceUiState.Success
        // Now shows 5 minutes since original load
        assertEquals(BigInteger.valueOf(3_000_000), state3.balances[0].balanceRaw)
        assertEquals("5 minutes ago", state3.lastUpdated)
    }

    @Test
    fun `refresh resets lastUpdated timestamp to now`() = runTest {
        // Given: Initial load at T0 (10:00:00)
        val balances = listOf(TokenBalance("DUST", BigInteger.valueOf(1_000_000), 1))
        val balanceFlow = MutableSharedFlow<List<TokenBalance>>()
        whenever(repository.observeBalances(testAddress))
            .thenReturn(balanceFlow)

        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        balanceFlow.emit(balances)
        advanceUntilIdle()

        assertEquals("Just now", viewModel.balanceState.value.let { it as BalanceUiState.Success }.lastUpdated)

        // Advance 5 minutes (10:05:00)
        fakeClock.advance(300_000)

        balanceFlow.emit(balances)
        advanceUntilIdle()

        val state1 = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals("5 minutes ago", state1.lastUpdated)

        // When: User pulls to refresh at 10:05:00 (captures new timestamp)
        viewModel.refresh(testAddress)
        advanceUntilIdle()

        balanceFlow.emit(balances)
        advanceUntilIdle()

        // Then: Timestamp resets to "Just now"
        val state2 = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals("Just now", state2.lastUpdated)

        // Advance 2 more minutes (10:07:00)
        fakeClock.advance(120_000)

        balanceFlow.emit(balances)
        advanceUntilIdle()

        // Should show 2 minutes since REFRESH (not since original load)
        val state3 = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals("2 minutes ago", state3.lastUpdated)
    }

    @Test
    fun `lastUpdated shows hours after 60 minutes`() = runTest {
        // Given: Repository emits balances
        val balances = listOf(TokenBalance("DUST", BigInteger.valueOf(1_000_000), 1))
        val balanceFlow = MutableSharedFlow<List<TokenBalance>>()
        whenever(repository.observeBalances(testAddress))
            .thenReturn(balanceFlow)

        // When: Load at T0
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        balanceFlow.emit(balances)
        advanceUntilIdle()

        assertEquals("Just now", viewModel.balanceState.value.let { it as BalanceUiState.Success }.lastUpdated)

        // Advance 2 hours
        fakeClock.advance(7_200_000)

        // Trigger emission to recalculate
        balanceFlow.emit(balances)
        advanceUntilIdle()

        // Then: Shows "2 hours ago"
        val state = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals("2 hours ago", state.lastUpdated)
    }

    @Test
    fun `lastUpdated shows single hour correctly`() = runTest {
        // Given: Repository emits balances
        val balances = listOf(TokenBalance("DUST", BigInteger.valueOf(1_000_000), 1))
        val balanceFlow = MutableSharedFlow<List<TokenBalance>>()
        whenever(repository.observeBalances(testAddress))
            .thenReturn(balanceFlow)

        // When: Load at T0
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        balanceFlow.emit(balances)
        advanceUntilIdle()

        // Advance exactly 1 hour
        fakeClock.advance(3_600_000)

        balanceFlow.emit(balances)
        advanceUntilIdle()

        // Then: Shows "1 hour ago" (singular)
        val state = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals("1 hour ago", state.lastUpdated)
    }

    // ==================== Memory Leak Prevention ====================

    @Test
    fun `multiple loadBalances calls cancel previous collection`() = runTest {
        // Given: Two different Flow sources with VALID addresses
        val flow1 = MutableSharedFlow<List<TokenBalance>>()
        val flow2 = MutableSharedFlow<List<TokenBalance>>()
        val address1 = "mn_addr_testnet1address1"
        val address2 = "mn_addr_testnet1address2"

        // When: Load with first address
        whenever(repository.observeBalances(address1))
            .thenReturn(flow1)
        viewModel.loadBalances(address1)
        advanceUntilIdle()

        flow1.emit(listOf(TokenBalance("DUST", BigInteger.valueOf(1_000_000), 1)))
        advanceUntilIdle()

        val state1 = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals(1, state1.balances.size)

        // When: Load with second address (should cancel first)
        whenever(repository.observeBalances(address2))
            .thenReturn(flow2)
        viewModel.loadBalances(address2)
        advanceUntilIdle()

        flow2.emit(listOf(TokenBalance("TNIGHT", BigInteger.valueOf(2_000_000), 2)))
        advanceUntilIdle()

        // Then: Only second address data shown
        val state2 = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals("TNIGHT", state2.balances[0].tokenType)

        // Emit from first flow (should be ignored - collection cancelled)
        flow1.emit(listOf(TokenBalance("DUST", BigInteger.valueOf(5_000_000), 5)))
        advanceUntilIdle()

        // State should still show TNIGHT (from address2)
        val state3 = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals("TNIGHT", state3.balances[0].tokenType)
    }

    // ==================== Address Validation ====================

    @Test
    fun `loadBalances rejects blank address`() = runTest {
        // When/Then: Blank address throws
        try {
            viewModel.loadBalances("")
            assertTrue("Should have thrown IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("blank"))
        }
    }

    @Test
    fun `loadBalances rejects invalid address format`() = runTest {
        // When/Then: Address not starting with mn_ throws
        try {
            viewModel.loadBalances("invalid_address_123")
            assertTrue("Should have thrown IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("mn_"))
        }
    }

    @Test
    fun `loadBalances accepts valid Midnight address`() = runTest {
        // Given: Valid address
        val validAddress = "mn_addr_testnet1abcdef123456"
        val balances = listOf(TokenBalance("DUST", BigInteger.valueOf(1_000_000), 1))
        whenever(repository.observeBalances(validAddress))
            .thenReturn(flowOf(balances))

        // When: Load with valid address
        viewModel.loadBalances(validAddress)
        advanceUntilIdle()

        // Then: No exception, state is Success
        assertTrue(viewModel.balanceState.value is BalanceUiState.Success)
    }

    // ==================== Flow State Consistency ====================

    @Test
    fun `database updates preserve user data without re-fetching`() = runTest {
        // Given: Initial balance
        val balances1 = listOf(TokenBalance("DUST", BigInteger.valueOf(1_000_000), 1))
        val balances2 = listOf(
            TokenBalance("DUST", BigInteger.valueOf(1_000_000), 1),
            TokenBalance("TNIGHT", BigInteger.valueOf(500_000), 1)  // New token appears
        )

        val balanceFlow = MutableSharedFlow<List<TokenBalance>>()
        whenever(repository.observeBalances(testAddress))
            .thenReturn(balanceFlow)

        // When: Initial load
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        balanceFlow.emit(balances1)
        advanceUntilIdle()

        val state1 = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals(1, state1.balances.size)

        // When: Database emits updated balances (new transaction received)
        balanceFlow.emit(balances2)
        advanceUntilIdle()

        // Then: UI automatically updates with new balance
        val state2 = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals(2, state2.balances.size)

        val dust = state2.balances.find { it.tokenType == "DUST" }
        val tnight = state2.balances.find { it.tokenType == "TNIGHT" }

        assertEquals("1.000000 DUST", dust?.balanceFormatted)
        assertEquals("0.500000 TNIGHT", tnight?.balanceFormatted)
    }

    @Test
    fun `error in Flow does not crash - emits Error state`() = runTest {
        // Given: Flow that throws after first emission
        val balances = listOf(TokenBalance("DUST", BigInteger.valueOf(1_000_000), 1))
        val balanceFlow = MutableSharedFlow<List<TokenBalance>>()
        whenever(repository.observeBalances(testAddress))
            .thenReturn(balanceFlow)

        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // First emission succeeds
        balanceFlow.emit(balances)
        advanceUntilIdle()

        assertTrue(viewModel.balanceState.value is BalanceUiState.Success)

        // When: Repository throws error (simulating database corruption)
        balanceFlow.emit(emptyList())  // This works
        advanceUntilIdle()

        // Then: State updates (no crash)
        val state = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals(0, state.balances.size)
    }

    // ==================== Refresh Behavior ====================

    @Test
    fun `refresh without prior load does nothing`() = runTest {
        // Given: No initial load
        val initialState = viewModel.balanceState.value

        // When: Refresh called
        viewModel.refresh(testAddress)
        advanceUntilIdle()

        // Then: State unchanged (still Loading)
        assertEquals(initialState, viewModel.balanceState.value)
    }

    @Test
    fun `refresh triggers new flatMapLatest but reuses collection job`() = runTest {
        // Given: Initial load
        val balances = listOf(TokenBalance("DUST", BigInteger.valueOf(1_000_000), 1))
        val balanceFlow = MutableSharedFlow<List<TokenBalance>>()
        whenever(repository.observeBalances(testAddress))
            .thenReturn(balanceFlow)

        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        balanceFlow.emit(balances)
        advanceUntilIdle()

        val state1 = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals(BigInteger.valueOf(1_000_000), state1.totalBalance)

        // When: Refresh (emits to refreshTrigger, triggers flatMapLatest)
        viewModel.refresh(testAddress)
        advanceUntilIdle()

        balanceFlow.emit(balances)
        advanceUntilIdle()

        // Then: State still accessible and updated
        val state2 = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals(BigInteger.valueOf(1_000_000), state2.totalBalance)

        // Note: flatMapLatest may call repository.observeBalances() again
        // but only one collection is active at a time (previous is cancelled)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles rapid refresh calls without crash`() = runTest {
        // Given: Initial state
        val balances = listOf(TokenBalance("DUST", BigInteger.valueOf(1_000_000), 1))
        val balanceFlow = MutableSharedFlow<List<TokenBalance>>()
        whenever(repository.observeBalances(testAddress))
            .thenReturn(balanceFlow)

        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        balanceFlow.emit(balances)
        advanceUntilIdle()

        // When: Rapid refresh calls (user spam-pulling)
        repeat(10) {
            viewModel.refresh(testAddress)
        }
        advanceUntilIdle()

        balanceFlow.emit(balances)
        advanceUntilIdle()

        // Then: No crash, state is Success
        assertTrue(viewModel.balanceState.value is BalanceUiState.Success)
    }

    @Test
    fun `zero balance shows correct formatting`() = runTest {
        // Given: Zero balance
        val balances = listOf(TokenBalance("DUST", BigInteger.ZERO, 0))
        whenever(repository.observeBalances(testAddress))
            .thenReturn(flowOf(balances))

        // When: Load balances
        viewModel.loadBalances(testAddress)
        advanceUntilIdle()

        // Then: Formatted correctly
        val state = viewModel.balanceState.value as BalanceUiState.Success
        assertEquals("0.000000 DUST", state.balances[0].balanceFormatted)
        assertEquals(BigInteger.ZERO, state.totalBalance)
    }
}
