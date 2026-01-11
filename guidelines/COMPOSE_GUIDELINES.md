# Compose UI Guidelines
**Version:** 1.0.0
**Last Updated:** January 10, 2026

**Purpose:** Best practices for Jetpack Compose UI development in Midnight Wallet.

**Related Guidelines:**
- For StateFlow/coroutines patterns → See `KOTLIN_GUIDELINES.md`
- For testing Compose UI → See `TESTING_GUIDELINES.md`
- For architecture patterns → See `ARCHITECTURE_GUIDELINES.md`

---

## State Hoisting

```kotlin
// ✅ GOOD: Stateless composable
@Composable
fun WalletBalanceCard(
    balance: BigInteger,
    onRefresh: () -> Unit
) {
    Card {
        Text("Balance: $balance")
        Button(onClick = onRefresh) { Text("Refresh") }
    }
}

// ✅ GOOD: Stateful wrapper
@Composable
fun WalletBalanceScreen(
    viewModel: WalletViewModel = hiltViewModel()
) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()

    WalletBalanceCard(
        balance = balance,
        onRefresh = viewModel::refreshBalance
    )
}
```

**Pattern:**
- **Stateless composables** receive data and emit events
- **Stateful composables** manage state and delegate to stateless components
- This separation enables:
  - Easy previews (pass mock data)
  - Reusability (stateless can be used anywhere)
  - Testability (test business logic separately from UI)

---

## Side Effects

### LaunchedEffect - One-time operations

```kotlin
// ✅ GOOD: LaunchedEffect for one-time operations
@Composable
fun WalletScreen(viewModel: WalletViewModel) {
    LaunchedEffect(Unit) {
        viewModel.loadInitialData()
    }
}

// ✅ GOOD: LaunchedEffect with key for re-execution
@Composable
fun TransactionScreen(txId: String, viewModel: TxViewModel) {
    LaunchedEffect(txId) {  // Re-runs when txId changes
        viewModel.loadTransaction(txId)
    }
}
```

**Use LaunchedEffect when:**
- Loading data when screen appears
- Starting animations
- Collecting flows
- Key changes and you want to restart the effect

### rememberCoroutineScope - Event handlers

```kotlin
// ✅ GOOD: rememberCoroutineScope for event handlers
@Composable
fun SendButton(onClick: suspend () -> Unit) {
    val scope = rememberCoroutineScope()

    Button(onClick = { scope.launch { onClick() } }) {
        Text("Send")
    }
}
```

**Use rememberCoroutineScope when:**
- Handling user events (button clicks)
- Manual operations triggered by user
- Need to launch coroutines outside of LaunchedEffect

### ❌ Don't: Side effects in composition

```kotlin
// ❌ BAD: Side effects in composition
@Composable
fun WalletScreen(viewModel: WalletViewModel) {
    viewModel.loadData()  // DON'T - runs every recomposition
}

// ❌ BAD: Direct coroutine launch
@Composable
fun SendScreen() {
    Button(onClick = {
        viewModelScope.launch { ... }  // Wrong scope
    })
}
```

---

## State Collection

### Use collectAsStateWithLifecycle

```kotlin
// ✅ GOOD: Lifecycle-aware collection
@Composable
fun WalletScreen(viewModel: WalletViewModel) {
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    WalletContent(balance = balance, transactions = transactions)
}

// ❌ BAD: collectAsState (not lifecycle-aware)
@Composable
fun WalletScreen(viewModel: WalletViewModel) {
    val balance by viewModel.balance.collectAsState()  // Collects even when app backgrounded
}
```

**Why collectAsStateWithLifecycle:**
- Stops collecting when app is in background
- Saves battery and resources
- Prevents unnecessary work

---

## Preview Annotations

```kotlin
// ✅ GOOD: Multiple preview variants
@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large Font", fontScale = 1.5f)
@Composable
private fun WalletCardPreview() {
    MidnightTheme {
        WalletBalanceCard(
            balance = 1000.toBigInteger(),
            onRefresh = {}
        )
    }
}
```

**Preview best practices:**
- Always wrap in theme (`MidnightTheme`)
- Test light and dark modes
- Test large fonts (accessibility)
- Use `private` to avoid exposing preview functions
- Pass mock data that represents realistic scenarios

---

## Performance Optimization

### Remember expensive computations

```kotlin
// ✅ GOOD: Remember expensive calculation
@Composable
fun TransactionList(transactions: List<Transaction>) {
    val sortedTransactions = remember(transactions) {
        transactions.sortedByDescending { it.timestamp }
    }

    LazyColumn {
        items(sortedTransactions) { tx ->
            TransactionItem(tx)
        }
    }
}

// ❌ BAD: Recompute on every composition
@Composable
fun TransactionList(transactions: List<Transaction>) {
    val sortedTransactions = transactions.sortedByDescending { it.timestamp }
    // Sorts on EVERY recomposition!
}
```

### Use keys in LazyColumn/LazyRow

```kotlin
// ✅ GOOD: Use stable keys
LazyColumn {
    items(transactions, key = { it.id }) { tx ->
        TransactionItem(tx)
    }
}

// ❌ BAD: No keys (can cause UI bugs on reordering)
LazyColumn {
    items(transactions) { tx ->
        TransactionItem(tx)
    }
}
```

---

## Navigation

### Pass minimal data

```kotlin
// ✅ GOOD: Pass only ID, load in destination
navController.navigate("transaction/${tx.id}")

@Composable
fun TransactionDetailScreen(
    txId: String,
    viewModel: TxDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(txId) {
        viewModel.loadTransaction(txId)
    }
    // ...
}

// ❌ BAD: Pass complex objects (doesn't survive process death)
navController.navigate("transaction", extras = bundleOf("tx" to transaction))
```

---

## Accessibility

### Content descriptions

```kotlin
// ✅ GOOD: Provide content descriptions
Icon(
    imageVector = Icons.Default.Send,
    contentDescription = "Send transaction"
)

Button(onClick = { /* ... */ }) {
    Text("Send")
}  // Button text automatically used as content description

// ❌ BAD: No content description
Icon(
    imageVector = Icons.Default.Send,
    contentDescription = null  // Screen readers can't describe this
)
```

### Semantic properties

```kotlin
// ✅ GOOD: Use semantic properties for custom composables
@Composable
fun BalanceCard(balance: BigInteger) {
    Card(
        modifier = Modifier.semantics {
            contentDescription = "Balance: ${balance.toDisplayString()}"
        }
    ) {
        // ...
    }
}
```

---

## Compose Best Practices Summary

### State Management
- ✅ Hoist state to the appropriate level
- ✅ Use `StateFlow` in ViewModels, `State` in composables
- ✅ Collect with `collectAsStateWithLifecycle()`

### Side Effects
- ✅ Use `LaunchedEffect` for one-time or key-dependent operations
- ✅ Use `rememberCoroutineScope` for event handlers
- ❌ Never put side effects directly in composition

### Performance
- ✅ Use `remember` for expensive computations
- ✅ Use `key` parameter in lazy lists
- ✅ Use `derivedStateOf` for derived state

### Reusability
- ✅ Create stateless composables when possible
- ✅ Pass data down, events up
- ✅ Use modifiers as parameters for customization

### Testing & Previews
- ✅ Multiple preview variants (light/dark, font scales)
- ✅ Test composables with `createComposeRule()`
- ✅ Use preview parameters for different states

---

## Common Mistakes

### ❌ Managing state in the wrong place

```kotlin
// BAD - State in stateless composable
@Composable
fun BalanceCard() {
    var balance by remember { mutableStateOf(BigInteger.ZERO) }
    // Should be in ViewModel!
}

// GOOD - State in ViewModel
@Composable
fun BalanceCard(balance: BigInteger) {
    Card { Text("$balance") }
}
```

### ❌ Conditional composables without keys

```kotlin
// BAD - Can cause state loss
if (showDialog) {
    Dialog()
}

// GOOD - Explicit state management
var showDialog by remember { mutableStateOf(false) }
if (showDialog) {
    Dialog(onDismiss = { showDialog = false })
}
```

### ❌ Recreating objects in composition

```kotlin
// BAD - Creates new list every recomposition
@Composable
fun MyScreen() {
    val items = listOf("Item 1", "Item 2")  // New list every time!
}

// GOOD - Use remember or pass as parameter
@Composable
fun MyScreen(items: List<String> = remember { listOf("Item 1", "Item 2") }) {
    // ...
}
```
