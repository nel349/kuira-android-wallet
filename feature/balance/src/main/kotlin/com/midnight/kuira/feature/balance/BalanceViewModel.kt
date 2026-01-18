package com.midnight.kuira.feature.balance

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.midnight.kuira.core.indexer.model.TokenBalance
import com.midnight.kuira.core.indexer.repository.BalanceRepository
import com.midnight.kuira.core.indexer.ui.BalanceFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * ViewModel for balance viewing screens.
 *
 * **Responsibilities:**
 * - Observe balances from BalanceRepository
 * - Transform domain models to UI models
 * - Handle loading/error states
 * - Track last updated timestamp
 * - Support pull-to-refresh
 *
 * **Architecture:**
 * Uses a single Flow collection with a refresh trigger to avoid memory leaks
 * and duplicate subscriptions. The trigger mechanism allows refresh without
 * creating new collections.
 *
 * **Example Usage (Compose):**
 * ```kotlin
 * @Composable
 * fun BalanceScreen(viewModel: BalanceViewModel = hiltViewModel()) {
 *     val state by viewModel.balanceState.collectAsState()
 *
 *     when (state) {
 *         is BalanceUiState.Loading -> LoadingIndicator()
 *         is BalanceUiState.Success -> BalanceList(state.balances)
 *         is BalanceUiState.Error -> ErrorMessage(state.message)
 *     }
 * }
 * ```
 */
@HiltViewModel
class BalanceViewModel @Inject constructor(
    private val repository: BalanceRepository,
    private val formatter: BalanceFormatter,
    private val clock: Clock = Clock.systemDefaultZone()  // Injected for testability
) : ViewModel() {

    private val _balanceState = MutableStateFlow<BalanceUiState>(BalanceUiState.Loading())
    val balanceState: StateFlow<BalanceUiState> = _balanceState.asStateFlow()

    // Trigger for refreshing without creating new subscriptions
    private val refreshTrigger = MutableSharedFlow<String>(replay = 1)

    // Job tracking for proper cancellation
    private var collectionJob: Job? = null

    // Track when user last triggered a load/refresh (NOT when database emitted)
    // This timestamp is set ONLY on explicit user actions (loadBalances/refresh)
    //
    // Important: This stays constant across automatic database emissions.
    // formatLastUpdated() compares this stored timestamp to Instant.now() on every
    // map emission, so "2 minutes ago" becomes "3 minutes ago" as time passes.
    private var lastLoadTimestamp: Instant? = null

    /**
     * Load balances for a specific address.
     *
     * Cancels any previous collection and starts observing balance changes.
     * Uses a single collection pattern with refresh trigger to prevent memory leaks.
     *
     * @param address The unshielded address to track (must be valid Midnight address)
     * @throws IllegalArgumentException if address is invalid
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalCoroutinesApi::class)
    fun loadBalances(address: String) {
        // Validate address format
        require(address.isNotBlank()) { "Address cannot be blank" }
        require(address.startsWith("mn_")) {
            "Invalid Midnight address format. Must start with 'mn_'"
        }

        // Cancel previous collection to prevent memory leaks
        collectionJob?.cancel()

        _balanceState.value = BalanceUiState.Loading(isRefreshing = false)

        // Start new collection with refresh trigger
        collectionJob = viewModelScope.launch {
            // Emit initial address to trigger
            refreshTrigger.emit(address)

            // Single collection that responds to refresh triggers
            refreshTrigger
                .flatMapLatest { addr ->
                    // Capture timestamp when user triggers load/refresh
                    // This timestamp stays the same for all subsequent database emissions
                    // until next explicit user action
                    lastLoadTimestamp = clock.instant()

                    repository.observeBalances(addr)
                        .map<List<TokenBalance>, BalanceUiState> { balances ->
                            // Transform to UI models
                            val displayBalances = balances.map { it.toDisplay(formatter) }

                            // Calculate total balance safely (no overflow with BigInteger)
                            val totalBalance = balances.fold(BigInteger.ZERO) { acc, balance ->
                                acc.add(balance.balance)
                            }

                            // Format last updated timestamp
                            // Uses stored timestamp from when user triggered load/refresh
                            // Compares to Instant.now() on each emission, so string updates
                            // as time passes (e.g., "2 min ago" → "3 min ago")
                            val lastUpdatedString = formatLastUpdated(lastLoadTimestamp!!)

                            BalanceUiState.Success(
                                balances = displayBalances,
                                lastUpdated = lastUpdatedString,
                                totalBalance = totalBalance
                            )
                        }
                        .catch { throwable ->
                            emit(
                                BalanceUiState.Error(
                                    message = getUserFriendlyError(throwable),
                                    throwable = throwable
                                )
                            )
                        }
                }
                .collect { uiState ->
                    _balanceState.value = uiState
                }
        }
    }

    /**
     * Refresh balances (pull-to-refresh).
     *
     * Triggers a refresh without creating a new Flow collection.
     * Sets isRefreshing = true while keeping current data visible.
     *
     * @param address The address to refresh (should match the currently loaded address)
     */
    fun refresh(address: String) {
        viewModelScope.launch {
            // Show refreshing indicator while keeping current data
            val currentState = _balanceState.value
            if (currentState is BalanceUiState.Success) {
                _balanceState.value = BalanceUiState.Loading(isRefreshing = true)
            }

            // Trigger refresh via shared flow (reuses existing collection)
            refreshTrigger.emit(address)
        }
    }

    /**
     * Format last updated timestamp for display.
     *
     * Compares the stored timestamp (when user triggered load/refresh)
     * to current time to calculate duration.
     *
     * Called on every Flow emission, so the string updates as time passes
     * even if the stored timestamp stays constant.
     *
     * Examples:
     * - "Just now" (< 1 minute)
     * - "2 minutes ago"
     * - "1 hour ago"
     * - "Yesterday at 3:45 PM"
     * - "Jan 15 at 3:45 PM"
     *
     * @param timestamp When user last triggered load/refresh (NOT when database emitted)
     * @return Human-readable "time ago" string
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun formatLastUpdated(timestamp: Instant): String {
        val now = clock.instant()  // Use injected clock (testable)
        val duration = Duration.between(timestamp, now)

        return when {
            duration.toMinutes() < ONE_MINUTE_THRESHOLD -> "Just now"
            duration.toMinutes() < MINUTES_IN_HOUR -> "${duration.toMinutes()} minutes ago"
            duration.toHours() < HOURS_IN_DAY -> {
                if (duration.toHours() == 1L) "1 hour ago" else "${duration.toHours()} hours ago"
            }
            duration.toDays() == 1L -> {
                val formatter = DateTimeFormatter.ofPattern(TIME_PATTERN)
                    .withZone(ZoneId.systemDefault())
                "Yesterday at ${formatter.format(timestamp)}"
            }
            else -> {
                val formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN)
                    .withZone(ZoneId.systemDefault())
                formatter.format(timestamp)
            }
        }
    }

    /**
     * Convert exceptions to user-friendly error messages.
     */
    private fun getUserFriendlyError(throwable: Throwable): String {
        return when {
            throwable.message?.contains("network", ignoreCase = true) == true ->
                "Network error. Please check your connection."

            throwable.message?.contains("timeout", ignoreCase = true) == true ->
                "Request timed out. Please try again."

            throwable.message?.contains("database", ignoreCase = true) == true ->
                "Database error. Please restart the app."

            throwable is IllegalArgumentException ->
                "Invalid input: ${throwable.message}"

            else ->
                "Failed to load balances: ${throwable.message}"
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel collection when ViewModel is cleared
        collectionJob?.cancel()
    }

    private companion object {
        // Time threshold constants
        const val ONE_MINUTE_THRESHOLD = 1L
        const val MINUTES_IN_HOUR = 60L
        const val HOURS_IN_DAY = 24L

        // Date formatting patterns
        const val TIME_PATTERN = "h:mm a"
        const val DATE_TIME_PATTERN = "MMM d 'at' h:mm a"
    }
}
