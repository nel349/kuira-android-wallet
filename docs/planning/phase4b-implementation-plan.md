# Phase 4B: Real-Time Sync & Event Deserialization

**Date:** 2026-01-15
**Estimated Time:** 25-35 hours
**Prerequisites:** ✅ Phase 4A Complete (118 tests passing)

---

## Mission

Complete the indexer integration by adding:
1. **WebSocket subscriptions** for real-time event streaming
2. **WASM integration** for deserializing raw hex events
3. **TLS certificate pinning** for production security

**Success Criteria:**
- Real-time event streaming from Midnight indexer
- Typed events (not just raw hex)
- Secure production-ready connections
- Comprehensive test coverage

---

## Phase 4B Architecture Overview

### Current State (Phase 4A)

```
┌─────────────────────────────────────────────────────────┐
│                    IndexerClient                        │
│  ┌───────────────────────────────────────────────┐     │
│  │  HTTP Client (Ktor)                           │     │
│  │  - getNetworkState() ✅                        │     │
│  │  - getEventsInRange() ✅                       │     │
│  │  - isHealthy() ✅                              │     │
│  └───────────────────────────────────────────────┘     │
│                                                         │
│  ┌───────────────────────────────────────────────┐     │
│  │  WebSocket Client (Placeholder)               │     │
│  │  - subscribeToZswapEvents() ❌                 │     │
│  │  - subscribeToBlocks() ❌                      │     │
│  └───────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────┘

                    ↓ Stores in

┌─────────────────────────────────────────────────────────┐
│             InMemoryEventCache                          │
│  - Raw hex events (RawLedgerEvent)                      │
│  - LRU eviction ✅                                       │
│  - Thread-safe ✅                                        │
└─────────────────────────────────────────────────────────┘
```

### Target State (Phase 4B)

```
┌──────────────────────────────────────────────────────────┐
│                    IndexerClient                         │
│  ┌────────────────────────────────────────────────┐      │
│  │  HTTP Client (Ktor) ✅                          │      │
│  └────────────────────────────────────────────────┘      │
│                                                          │
│  ┌────────────────────────────────────────────────┐      │
│  │  WebSocket Client (graphql-ws) 🆕               │      │
│  │  - Connection lifecycle management              │      │
│  │  - Auto-reconnect with backoff                  │      │
│  │  - Real-time event streaming                    │      │
│  └────────────────────────────────────────────────┘      │
│                                                          │
│  ┌────────────────────────────────────────────────┐      │
│  │  TLS Configuration 🆕                           │      │
│  │  - Certificate pinning                          │      │
│  │  - Production-grade security                    │      │
│  └────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────┘

                    ↓ Streams to

┌──────────────────────────────────────────────────────────┐
│             EventDeserializer 🆕                          │
│  - WASM bridge to ledger 7.0.0                           │
│  - Hex → Typed events                                    │
└──────────────────────────────────────────────────────────┘

                    ↓ Stores in

┌──────────────────────────────────────────────────────────┐
│             EventCache (Enhanced) 🆕                      │
│  - Typed events (ZswapEvent, DustEvent, etc.)            │
│  - Raw hex fallback                                      │
│  - LRU eviction                                          │
└──────────────────────────────────────────────────────────┘
```

---

## Feature 1: WebSocket Subscriptions (12-15 hours)

### Overview

Implement `graphql-ws` protocol for real-time event streaming from Midnight indexer.

**Why This Matters:**
- Current HTTP polling is inefficient (waste bandwidth, delayed updates)
- WebSocket provides millisecond latency for new events
- Required for production wallet sync

### graphql-ws Protocol Specification

**Reference:** https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md

**Connection Lifecycle:**

```
Client                                    Server
  │                                         │
  ├─── connection_init ──────────────────>│
  │    { "type": "connection_init" }       │
  │                                         │
  │<──────────── connection_ack ──────────┤
  │    { "type": "connection_ack" }        │
  │                                         │
  ├─── subscribe ────────────────────────>│
  │    {                                   │
  │      "id": "1",                        │
  │      "type": "subscribe",              │
  │      "payload": {                      │
  │        "query": "subscription {...}",  │
  │        "variables": {...}              │
  │      }                                 │
  │    }                                   │
  │                                         │
  │<──────────── next ────────────────────┤
  │    {                                   │
  │      "id": "1",                        │
  │      "type": "next",                   │
  │      "payload": {                      │
  │        "data": {...}                   │
  │      }                                 │
  │    }                                   │
  │                                         │
  │<──────────── next ────────────────────┤
  │    (more events...)                    │
  │                                         │
  ├─── complete ──────────────────────────>│
  │    { "id": "1", "type": "complete" }   │
  │                                         │
  ├─── connection_terminate ──────────────>│
  │                                         │
  │<──────────── close ───────────────────┤
```

**Message Types:**

| Type | Direction | Purpose |
|------|-----------|---------|
| `connection_init` | Client → Server | Initialize WebSocket connection |
| `connection_ack` | Server → Client | Acknowledge connection |
| `ping` | Client → Server | Keepalive |
| `pong` | Server → Client | Keepalive response |
| `subscribe` | Client → Server | Start subscription |
| `next` | Server → Client | Subscription data event |
| `error` | Server → Client | Subscription error |
| `complete` | Both | End subscription |

### Implementation Plan

#### Step 1: WebSocket Message Models (2 hours)

**Create:** `core/indexer/src/main/kotlin/com/midnight/kuira/core/indexer/ws/`

```kotlin
// GraphQLWsMessage.kt
@Serializable
sealed class GraphQLWsMessage {
    abstract val type: String

    @Serializable
    @SerialName("connection_init")
    data class ConnectionInit(
        override val type: String = "connection_init",
        val payload: JsonObject? = null
    ) : GraphQLWsMessage()

    @Serializable
    @SerialName("connection_ack")
    data class ConnectionAck(
        override val type: String = "connection_ack",
        val payload: JsonObject? = null
    ) : GraphQLWsMessage()

    @Serializable
    @SerialName("ping")
    data class Ping(
        override val type: String = "ping",
        val payload: JsonObject? = null
    ) : GraphQLWsMessage()

    @Serializable
    @SerialName("pong")
    data class Pong(
        override val type: String = "pong",
        val payload: JsonObject? = null
    ) : GraphQLWsMessage()

    @Serializable
    @SerialName("subscribe")
    data class Subscribe(
        override val type: String = "subscribe",
        val id: String,
        val payload: SubscriptionPayload
    ) : GraphQLWsMessage()

    @Serializable
    @SerialName("next")
    data class Next(
        override val type: String = "next",
        val id: String,
        val payload: JsonObject
    ) : GraphQLWsMessage()

    @Serializable
    @SerialName("error")
    data class Error(
        override val type: String = "error",
        val id: String,
        val payload: List<JsonObject>
    ) : GraphQLWsMessage()

    @Serializable
    @SerialName("complete")
    data class Complete(
        override val type: String = "complete",
        val id: String
    ) : GraphQLWsMessage()
}

@Serializable
data class SubscriptionPayload(
    val query: String,
    val variables: Map<String, JsonElement>? = null
)
```

**Tests:**
```kotlin
class GraphQLWsMessageTest {
    @Test
    fun `serialize ConnectionInit`() {
        val message = GraphQLWsMessage.ConnectionInit()
        val json = Json.encodeToString(message)
        assertEquals("""{"type":"connection_init"}""", json)
    }

    @Test
    fun `deserialize ConnectionAck`() {
        val json = """{"type":"connection_ack"}"""
        val message = Json.decodeFromString<GraphQLWsMessage.ConnectionAck>(json)
        assertEquals("connection_ack", message.type)
    }

    @Test
    fun `serialize Subscribe with variables`() {
        val message = GraphQLWsMessage.Subscribe(
            id = "1",
            payload = SubscriptionPayload(
                query = "subscription { zswapLedgerEvents(id: \$id) { id raw maxId } }",
                variables = mapOf("id" to JsonPrimitive(42))
            )
        )
        val json = Json.encodeToString(message)
        assertTrue(json.contains("\"id\":\"1\""))
        assertTrue(json.contains("\"type\":\"subscribe\""))
    }

    @Test
    fun `deserialize Next message`() {
        val json = """{"type":"next","id":"1","payload":{"data":{"zswapLedgerEvents":{"id":1,"raw":"deadbeef","maxId":100}}}}"""
        val message = Json.decodeFromString<GraphQLWsMessage.Next>(json)
        assertEquals("1", message.id)
        assertNotNull(message.payload["data"])
    }
}
```

#### Step 2: WebSocket Client Implementation (6-8 hours)

**Create:** `GraphQLWsClient.kt`

```kotlin
/**
 * GraphQL WebSocket client implementing graphql-ws protocol.
 *
 * **Protocol:** https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md
 *
 * **Features:**
 * - Automatic connection management
 * - Reconnection with exponential backoff
 * - Subscription multiplexing (multiple subscriptions on one connection)
 * - Keepalive ping/pong
 */
class GraphQLWsClient(
    private val wsUrl: String,
    private val httpClient: HttpClient,
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Subscription ID generator
    private val subscriptionIdCounter = AtomicInteger(0)

    // Active subscriptions: id -> Flow collector
    private val activeSubscriptions = ConcurrentHashMap<String, MutableSharedFlow<JsonObject>>()

    // Connection state
    private var session: WebSocketSession? = null
    private val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val connectionMutex = Mutex()

    /**
     * Connect to WebSocket server.
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            if (connectionState.value == ConnectionState.CONNECTED) {
                return@withContext Result.success(Unit)
            }

            try {
                // Open WebSocket connection
                val newSession = httpClient.webSocketSession {
                    url(wsUrl)
                    // Set subprotocol
                    headers.append("Sec-WebSocket-Protocol", "graphql-transport-ws")
                }

                session = newSession
                connectionState.value = ConnectionState.CONNECTING

                // Send connection_init
                val initMessage = GraphQLWsMessage.ConnectionInit()
                send(initMessage)

                // Wait for connection_ack (with timeout)
                withTimeout(10_000) {
                    for (frame in newSession.incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val message = json.decodeFromString<GraphQLWsMessage>(text)

                            if (message is GraphQLWsMessage.ConnectionAck) {
                                connectionState.value = ConnectionState.CONNECTED

                                // Start message loop
                                launch { messageLoop(newSession) }

                                // Start keepalive
                                launch { keepaliveLoop() }

                                return@withTimeout Result.success(Unit)
                            }
                        }
                    }
                }

                Result.failure(Exception("Connection timeout: no connection_ack received"))
            } catch (e: Exception) {
                connectionState.value = ConnectionState.DISCONNECTED
                session = null
                Result.failure(e)
            }
        }
    }

    /**
     * Subscribe to GraphQL subscription.
     *
     * @param query GraphQL subscription query
     * @param variables Query variables
     * @return Flow of subscription data
     */
    fun <T> subscribe(
        query: String,
        variables: Map<String, JsonElement>? = null,
        parser: (JsonObject) -> T
    ): Flow<T> = flow {
        // Ensure connected
        val connectResult = connect()
        if (connectResult.isFailure) {
            throw connectResult.exceptionOrNull() ?: Exception("Failed to connect")
        }

        // Generate subscription ID
        val id = subscriptionIdCounter.incrementAndGet().toString()

        // Create flow for this subscription
        val subscriptionFlow = MutableSharedFlow<JsonObject>(replay = 0, extraBufferCapacity = 64)
        activeSubscriptions[id] = subscriptionFlow

        try {
            // Send subscribe message
            val subscribeMessage = GraphQLWsMessage.Subscribe(
                id = id,
                payload = SubscriptionPayload(query, variables)
            )
            send(subscribeMessage)

            // Collect events
            subscriptionFlow.collect { data ->
                try {
                    val parsed = parser(data)
                    emit(parsed)
                } catch (e: Exception) {
                    // Log parsing error but don't stop subscription
                    android.util.Log.e("GraphQLWsClient", "Failed to parse event: ${e.message}")
                }
            }
        } finally {
            // Cleanup
            activeSubscriptions.remove(id)

            // Send complete message
            val completeMessage = GraphQLWsMessage.Complete(id)
            send(completeMessage)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Message processing loop.
     */
    private suspend fun messageLoop(session: WebSocketSession) {
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    handleMessage(text)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GraphQLWsClient", "Message loop error: ${e.message}")
            handleDisconnect()
        }
    }

    /**
     * Handle incoming WebSocket message.
     */
    private suspend fun handleMessage(text: String) {
        try {
            val message = json.decodeFromString<GraphQLWsMessage>(text)

            when (message) {
                is GraphQLWsMessage.Next -> {
                    // Route to subscription flow
                    val flow = activeSubscriptions[message.id]
                    flow?.emit(message.payload)
                }

                is GraphQLWsMessage.Error -> {
                    android.util.Log.e("GraphQLWsClient", "Subscription error: ${message.payload}")
                    // TODO: Emit error to subscription flow
                }

                is GraphQLWsMessage.Complete -> {
                    // Subscription completed by server
                    activeSubscriptions.remove(message.id)
                }

                is GraphQLWsMessage.Ping -> {
                    // Respond to ping
                    send(GraphQLWsMessage.Pong())
                }

                is GraphQLWsMessage.Pong -> {
                    // Keepalive response received
                }

                else -> {
                    // Ignore unknown message types
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GraphQLWsClient", "Failed to handle message: ${e.message}")
        }
    }

    /**
     * Keepalive ping loop.
     */
    private suspend fun keepaliveLoop() {
        while (connectionState.value == ConnectionState.CONNECTED) {
            delay(30_000) // Ping every 30 seconds
            try {
                send(GraphQLWsMessage.Ping())
            } catch (e: Exception) {
                android.util.Log.w("GraphQLWsClient", "Keepalive failed: ${e.message}")
                break
            }
        }
    }

    /**
     * Send message to server.
     */
    private suspend fun send(message: GraphQLWsMessage) {
        val currentSession = session ?: throw IllegalStateException("Not connected")
        val text = json.encodeToString(message)
        currentSession.send(Frame.Text(text))
    }

    /**
     * Handle disconnection.
     */
    private suspend fun handleDisconnect() {
        connectionMutex.withLock {
            connectionState.value = ConnectionState.DISCONNECTED
            session = null

            // Clear all subscriptions (they'll need to resubscribe)
            activeSubscriptions.clear()
        }

        // Attempt reconnection
        reconnect()
    }

    /**
     * Reconnect with exponential backoff.
     */
    private suspend fun reconnect() {
        var attempt = 0
        while (attempt < reconnectPolicy.maxAttempts) {
            val delay = reconnectPolicy.delayForAttempt(attempt)
            android.util.Log.i("GraphQLWsClient", "Reconnecting in ${delay}ms (attempt ${attempt + 1})")

            delay(delay)

            val result = connect()
            if (result.isSuccess) {
                android.util.Log.i("GraphQLWsClient", "Reconnected successfully")
                return
            }

            attempt++
        }

        android.util.Log.e("GraphQLWsClient", "Reconnection failed after ${reconnectPolicy.maxAttempts} attempts")
    }

    /**
     * Close connection.
     */
    suspend fun close() {
        connectionMutex.withLock {
            activeSubscriptions.clear()
            session?.close()
            session = null
            connectionState.value = ConnectionState.DISCONNECTED
        }
    }
}

/**
 * WebSocket connection state.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

/**
 * Reconnection policy with exponential backoff.
 */
data class ReconnectPolicy(
    val maxAttempts: Int = 100,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 60_000,
    val backoffMultiplier: Double = 2.0
) {
    fun delayForAttempt(attempt: Int): Long {
        val delay = (initialDelayMs * backoffMultiplier.pow(attempt)).toLong()
        return minOf(delay, maxDelayMs)
    }
}
```

#### Step 3: Update IndexerClientImpl (2-3 hours)

**Modify:** `IndexerClientImpl.kt`

```kotlin
class IndexerClientImpl(
    private val baseUrl: String = "https://indexer.midnight.network/api/v3",
    private val pinnedCertificates: List<String> = emptyList(),
    private val developmentMode: Boolean = false
) : IndexerClient {

    // ... existing HTTP client code ...

    // WebSocket client
    private val wsClient by lazy {
        GraphQLWsClient(
            wsUrl = wsEndpoint,
            httpClient = httpClient,
            reconnectPolicy = ReconnectPolicy()
        )
    }

    override fun subscribeToZswapEvents(fromId: Long?): Flow<RawLedgerEvent> {
        val query = """
            subscription ZswapEvents(${'$'}id: Int) {
                zswapLedgerEvents(id: ${'$'}id) {
                    id
                    raw
                    maxId
                }
            }
        """.trimIndent()

        val variables = fromId?.let {
            mapOf("id" to JsonPrimitive(it.toInt()))
        }

        return wsClient.subscribe(query, variables) { payload ->
            // Parse payload
            val data = payload["data"]?.jsonObject
                ?: throw InvalidResponseException("Missing 'data' in subscription payload")

            val event = data["zswapLedgerEvents"]?.jsonObject
                ?: throw InvalidResponseException("Missing 'zswapLedgerEvents' in payload")

            val id = event["id"]?.jsonPrimitive?.long
                ?: throw InvalidResponseException("Missing 'id' field")
            val rawHex = event["raw"]?.jsonPrimitive?.content
                ?: throw InvalidResponseException("Missing 'raw' field")
            val maxId = event["maxId"]?.jsonPrimitive?.long
                ?: throw InvalidResponseException("Missing 'maxId' field")

            RawLedgerEvent(id = id, rawHex = rawHex, maxId = maxId)
        }
    }

    override fun subscribeToBlocks(): Flow<BlockInfo> {
        val query = """
            subscription Blocks {
                blocks {
                    height
                    hash
                    timestamp
                }
            }
        """.trimIndent()

        return wsClient.subscribe(query, variables = null) { payload ->
            val data = payload["data"]?.jsonObject
                ?: throw InvalidResponseException("Missing 'data' in subscription payload")

            val block = data["blocks"]?.jsonObject
                ?: throw InvalidResponseException("Missing 'blocks' in payload")

            val height = block["height"]?.jsonPrimitive?.long
                ?: throw InvalidResponseException("Missing 'height' field")
            val hash = block["hash"]?.jsonPrimitive?.content
                ?: throw InvalidResponseException("Missing 'hash' field")
            val timestamp = block["timestamp"]?.jsonPrimitive?.long
                ?: throw InvalidResponseException("Missing 'timestamp' field")

            BlockInfo(height = height, hash = hash, timestamp = timestamp, eventCount = 0)
        }
    }

    override fun close() {
        runBlocking {
            wsClient.close()
        }
        httpClient.close()
    }
}
```

#### Step 4: Tests (2 hours)

**Create:** `GraphQLWsClientTest.kt`

```kotlin
class GraphQLWsClientTest {

    private lateinit var mockServer: MockWebServer

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @After
    fun teardown() {
        mockServer.shutdown()
    }

    @Test
    fun `connect sends connection_init and receives connection_ack`() = runTest {
        // Mock server responses
        mockServer.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Send connection_ack
                webSocket.send("""{"type":"connection_ack"}""")
            }
        }))

        val client = GraphQLWsClient(
            wsUrl = mockServer.url("/graphql/ws").toString(),
            httpClient = HttpClient(CIO) { install(WebSockets) }
        )

        val result = client.connect()
        assertTrue(result.isSuccess)

        client.close()
    }

    @Test
    fun `subscribe sends subscribe message and receives next events`() = runTest {
        mockServer.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Send connection_ack
                webSocket.send("""{"type":"connection_ack"}""")

                // Wait for subscribe message, then send events
                delay(100)
                webSocket.send("""{"type":"next","id":"1","payload":{"data":{"zswapLedgerEvents":{"id":1,"raw":"deadbeef","maxId":100}}}}""")
                webSocket.send("""{"type":"next","id":"1","payload":{"data":{"zswapLedgerEvents":{"id":2,"raw":"cafebabe","maxId":100}}}}""")
                webSocket.send("""{"type":"complete","id":"1"}""")
            }
        }))

        val client = GraphQLWsClient(
            wsUrl = mockServer.url("/graphql/ws").toString(),
            httpClient = HttpClient(CIO) { install(WebSockets) }
        )

        val query = "subscription { zswapLedgerEvents { id raw maxId } }"
        val events = mutableListOf<RawLedgerEvent>()

        client.subscribe(query, null) { payload ->
            val event = payload["data"]?.jsonObject?.get("zswapLedgerEvents")?.jsonObject
            RawLedgerEvent(
                id = event?.get("id")?.jsonPrimitive?.long ?: 0,
                rawHex = event?.get("raw")?.jsonPrimitive?.content ?: "",
                maxId = event?.get("maxId")?.jsonPrimitive?.long ?: 0
            )
        }.take(2).toList(events)

        assertEquals(2, events.size)
        assertEquals(1L, events[0].id)
        assertEquals("deadbeef", events[0].rawHex)
        assertEquals(2L, events[1].id)
        assertEquals("cafebabe", events[1].rawHex)

        client.close()
    }

    @Test
    fun `reconnects automatically on disconnect`() = runTest {
        // TODO: Test reconnection logic
    }

    @Test
    fun `keepalive sends ping messages`() = runTest {
        // TODO: Test keepalive ping/pong
    }
}
```

---

## Feature 2: WASM Integration (8-10 hours)

### Overview

Integrate Midnight's `@midnight-ntwrk/ledger-v7` WASM module to deserialize raw hex events into typed Kotlin objects.

**Why This Matters:**
- Currently we only store raw hex strings
- Cannot extract transfer amounts, addresses, or event types
- Cannot calculate balances or show transaction history

### WASM Architecture

```
┌────────────────────────────────────────────────────────┐
│                 Android/Kotlin Layer                   │
│                                                        │
│   RawLedgerEvent(id, rawHex, maxId)                   │
│         │                                              │
│         ↓                                              │
│   EventDeserializer.deserialize(rawHex)               │
│         │                                              │
│         ↓                                              │
├────────────────────────────────────────────────────────┤
│                   JNI Bridge                           │
│  - ByteArray → WASM memory                             │
│  - Call WASM function                                  │
│  - WASM memory → ByteArray                             │
├────────────────────────────────────────────────────────┤
│         Midnight Ledger 7.0.0 WASM                     │
│  - deserialize_event(bytes) → Event                    │
│  - Rust/WASM binary                                    │
└────────────────────────────────────────────────────────┘
```

### Challenges

1. **WASM Runtime:** Need WebAssembly runtime for Android
   - Options: Wasmer, Wasmtime, or custom JNI bridge

2. **Binary Size:** WASM modules can be large (1-5 MB)
   - Need to bundle with APK or download on-demand

3. **Performance:** JNI overhead for each deserialization
   - Batch processing may be needed

### Implementation Plan

#### Step 1: Choose WASM Runtime (Research: 2 hours)

**Options:**

**Option A: Wasmer Android**
- Pros: Full-featured WebAssembly runtime, good documentation
- Cons: 5-10 MB library size, JNI overhead
- Verdict: ✅ Recommended

**Option B: Wasmtime**
- Pros: Official from Bytecode Alliance
- Cons: Limited Android support, larger binary
- Verdict: ❌ Skip

**Option C: Custom JNI Bridge to Native WASM**
- Pros: Smaller, more control
- Cons: Complex, maintenance burden
- Verdict: ⚠️ Consider if Wasmer too large

**Decision: Start with Wasmer Android**

#### Step 2: Compile Ledger WASM (3-4 hours)

**Location:** `/Users/norman/Development/midnight/midnight-libraries/midnight-ledger/`

**Build Steps:**
```bash
cd midnight-ledger

# Install wasm-pack
cargo install wasm-pack

# Build WASM module
wasm-pack build --target web --release

# Output: pkg/midnight_ledger_bg.wasm
```

**Extract to Android:**
```bash
cp pkg/midnight_ledger_bg.wasm \
   /Users/norman/Development/android/projects/kuira-android-wallet/core/indexer/src/main/assets/ledger.wasm
```

#### Step 3: WASM Bridge Implementation (4-6 hours)

**Add Dependency:**
```kotlin
// core/indexer/build.gradle.kts
dependencies {
    implementation("io.github.wasmerio:wasmer-jni:0.3.0")
}
```

**Create:** `EventDeserializer.kt`

```kotlin
/**
 * Deserializes raw hex events using Midnight Ledger 7.0.0 WASM.
 *
 * **Architecture:**
 * - Loads ledger.wasm from assets
 * - Calls deserialize_event(bytes) function
 * - Returns typed Kotlin event
 */
class EventDeserializer(
    private val context: Context
) {
    private val wasmModule: Module by lazy {
        // Load WASM binary from assets
        val wasmBytes = context.assets.open("ledger.wasm").readBytes()

        // Compile WASM module
        val store = Store(Engine())
        Module(store, wasmBytes)
    }

    private val instance: Instance by lazy {
        // Instantiate WASM module
        Instance(wasmModule, emptyArray())
    }

    /**
     * Deserialize raw hex event to typed event.
     *
     * @param rawHex Hex-encoded event data
     * @return Typed ledger event
     * @throws DeserializationException if parsing fails
     */
    fun deserialize(rawHex: String): LedgerEvent {
        try {
            // Convert hex to bytes
            val bytes = rawHex.hexToByteArray()

            // Call WASM function: deserialize_event(ptr: i32, len: i32) -> i32
            val memory = instance.exports.getMemory("memory")
            val deserializeFunc = instance.exports.getFunction("deserialize_event")

            // Write bytes to WASM memory
            val ptr = allocateInWasm(memory, bytes.size)
            memory.buffer().put(ptr, bytes)

            // Call deserialize function
            val resultPtr = deserializeFunc.apply(ptr, bytes.size)[0] as Int

            // Read result from WASM memory
            val resultJson = readStringFromWasm(memory, resultPtr)

            // Parse JSON to Kotlin object
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<LedgerEvent>(resultJson)
        } catch (e: Exception) {
            throw DeserializationException("Failed to deserialize event: ${e.message}", e)
        }
    }

    private fun allocateInWasm(memory: Memory, size: Int): Int {
        // Call WASM allocator (if available) or use fixed offset
        // This depends on the WASM module's memory layout
        return 0 // Placeholder
    }

    private fun readStringFromWasm(memory: Memory, ptr: Int): String {
        // Read null-terminated string from WASM memory
        val buffer = memory.buffer()
        val bytes = mutableListOf<Byte>()
        var offset = ptr
        while (true) {
            val byte = buffer.get(offset)
            if (byte == 0.toByte()) break
            bytes.add(byte)
            offset++
        }
        return bytes.toByteArray().decodeToString()
    }

    fun close() {
        // Cleanup WASM instance
        instance.close()
    }
}

/**
 * Base class for all ledger events.
 */
@Serializable
sealed class LedgerEvent {
    abstract val id: Long

    @Serializable
    @SerialName("Transfer")
    data class Transfer(
        override val id: Long,
        val from: String,
        val to: String,
        val amount: String, // BigInt as string
        val tokenType: String
    ) : LedgerEvent()

    @Serializable
    @SerialName("Shield")
    data class Shield(
        override val id: Long,
        val from: String,
        val amount: String,
        val tokenType: String,
        val coinPublicKey: String
    ) : LedgerEvent()

    @Serializable
    @SerialName("Unshield")
    data class Unshield(
        override val id: Long,
        val to: String,
        val amount: String,
        val tokenType: String
    ) : LedgerEvent()

    // ... more event types
}

class DeserializationException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

**Alternative: Fallback to Manual Parsing**

If WASM proves too complex, we can manually parse SCALE-encoded events:

```kotlin
/**
 * Manual SCALE codec parser for Midnight events.
 *
 * **WARNING:** This is error-prone and should only be used if WASM fails.
 */
class ScaleEventParser {
    fun parse(bytes: ByteArray): LedgerEvent {
        // SCALE decoding logic
        // This requires understanding Midnight's event format
        // Reference: midnight-ledger/src/events.rs

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read event discriminant (first byte)
        val discriminant = buffer.get().toInt()

        return when (discriminant) {
            0 -> parseTransfer(buffer)
            1 -> parseShield(buffer)
            2 -> parseUnshield(buffer)
            else -> throw DeserializationException("Unknown event type: $discriminant")
        }
    }

    private fun parseTransfer(buffer: ByteBuffer): LedgerEvent.Transfer {
        // Read SCALE-encoded transfer event
        // Format: discriminant(1) | from(32) | to(32) | amount(u128) | tokenType(u8)

        val from = ByteArray(32).also { buffer.get(it) }.toHexString()
        val to = ByteArray(32).also { buffer.get(it) }.toHexString()
        val amount = buffer.getLong().toString() // Simplified, real u128 needs BigInteger
        val tokenType = buffer.get().toString()

        return LedgerEvent.Transfer(
            id = 0, // ID from outer context
            from = from,
            to = to,
            amount = amount,
            tokenType = tokenType
        )
    }

    // ... more parsers
}
```

**Decision:** Try WASM first, fall back to SCALE parser if needed.

---

## Feature 3: TLS Certificate Pinning (4-6 hours)

### Overview

Implement certificate pinning to prevent man-in-the-middle attacks in production.

**Why This Matters:**
- Public WiFi and compromised networks can intercept HTTPS
- Certificate pinning ensures we only trust Midnight's certificates
- Required for production security audit

### Implementation Plan

#### Step 1: Switch to OkHttp Engine (2 hours)

**Modify:** `core/indexer/build.gradle.kts`

```kotlin
dependencies {
    // Replace CIO engine with OkHttp (better TLS support)
    implementation(libs.ktor.client.okhttp)

    // Certificate pinning
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
}
```

**Modify:** `IndexerClientImpl.kt`

```kotlin
import io.ktor.client.engine.okhttp.*
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

class IndexerClientImpl(
    private val baseUrl: String = "https://indexer.midnight.network/api/v3",
    private val pinnedCertificates: List<String> = emptyList(),
    private val developmentMode: Boolean = false
) : IndexerClient {

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }

        engine {
            // Configure OkHttpClient with certificate pinning
            config {
                if (pinnedCertificates.isNotEmpty()) {
                    certificatePinner(buildCertificatePinner())
                }
            }
        }

        // ... rest of configuration
    }

    private fun buildCertificatePinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()

        // Extract host from baseUrl
        val host = baseUrl.substringAfter("://").substringBefore("/")

        // Add pinned certificates
        pinnedCertificates.forEach { sha256Hash ->
            builder.add(host, "sha256/$sha256Hash")
        }

        return builder.build()
    }
}
```

#### Step 2: Extract Midnight Certificate Fingerprints (1 hour)

**Script to extract SHA-256 fingerprints:**

```bash
#!/bin/bash

# Extract certificate from Midnight indexer
echo | openssl s_client -servername indexer.midnight.network \
  -connect indexer.midnight.network:443 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | openssl enc -base64

# Output: SHA-256 fingerprint (base64)
# Example: 47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=
```

**Store in configuration:**

```kotlin
// core/indexer/src/main/kotlin/com/midnight/kuira/core/indexer/config/TlsConfig.kt

object TlsConfig {
    /**
     * Production certificate fingerprints for Midnight indexer.
     *
     * **How to update:**
     * 1. Run: `./scripts/extract-cert-fingerprint.sh indexer.midnight.network`
     * 2. Add new fingerprint to this list
     * 3. Keep old fingerprints for certificate rotation period
     *
     * **Rotation:** Certificates typically valid for 1 year.
     * Check expiry: `openssl s_client -connect indexer.midnight.network:443 | openssl x509 -noout -dates`
     */
    val PRODUCTION_CERTIFICATES = listOf(
        "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=", // Expires: 2026-12-31
        // Add backup certificates here for rotation
    )

    /**
     * Testnet certificate fingerprints.
     */
    val TESTNET_CERTIFICATES = listOf(
        "..." // Extract from testnet indexer
    )
}
```

#### Step 3: Tests (1-2 hours)

```kotlin
class TlsCertificatePinningTest {

    @Test
    fun `production client uses certificate pinning`() {
        val client = IndexerClientImpl(
            baseUrl = "https://indexer.midnight.network/api/v3",
            pinnedCertificates = TlsConfig.PRODUCTION_CERTIFICATES,
            developmentMode = false
        )

        // This should succeed (valid certificate)
        runBlocking {
            client.isHealthy()
        }
    }

    @Test
    fun `rejects invalid certificate`() {
        val client = IndexerClientImpl(
            baseUrl = "https://example.com",
            pinnedCertificates = listOf("INVALID_FINGERPRINT"),
            developmentMode = false
        )

        // This should fail (certificate mismatch)
        assertThrows<SSLPeerUnverifiedException> {
            runBlocking {
                client.getNetworkState()
            }
        }
    }

    @Test
    fun `development mode allows any certificate`() {
        val client = IndexerClientImpl(
            baseUrl = "http://localhost:8088/api/v3",
            pinnedCertificates = emptyList(),
            developmentMode = true
        )

        // This should work (development mode)
        runBlocking {
            client.isHealthy()
        }
    }
}
```

---

## Testing Strategy

### Unit Tests (Incremental, during implementation)

**Target:** 30-40 new tests for Phase 4B features

1. **WebSocket Protocol Tests:**
   - Message serialization/deserialization (6 tests)
   - Connection lifecycle (4 tests)
   - Subscription management (4 tests)
   - Reconnection logic (3 tests)
   - Keepalive ping/pong (2 tests)

2. **Event Deserialization Tests:**
   - WASM loading (2 tests)
   - Hex → Event parsing (5 tests)
   - Error handling (3 tests)
   - Event type discrimination (4 tests)

3. **TLS Tests:**
   - Certificate pinning enforcement (3 tests)
   - Invalid certificate rejection (2 tests)
   - Development mode bypass (2 tests)

### Integration Tests (End of Phase 4B)

**Test Environment:** Local Midnight indexer (Docker)

```bash
# Start local indexer for testing
docker run -p 8088:8088 midnight/indexer:latest
```

**Integration Test Plan:**

```kotlin
@IntegrationTest
class IndexerIntegrationTest {

    private lateinit var client: IndexerClient

    @Before
    fun setup() {
        client = IndexerClientImpl(
            baseUrl = "http://localhost:8088/api/v3",
            developmentMode = true
        )
    }

    @Test
    fun `subscribe to events and receive real-time updates`() = runTest {
        val events = mutableListOf<RawLedgerEvent>()

        // Subscribe to events
        val job = launch {
            client.subscribeToZswapEvents(fromId = 0)
                .take(5)
                .collect { events.add(it) }
        }

        // Wait for events
        job.join()

        // Verify received events
        assertTrue(events.isNotEmpty())
        events.forEach { event ->
            assertTrue(event.id >= 0)
            assertTrue(event.rawHex.isNotBlank())
        }
    }

    @Test
    fun `reconnects automatically after disconnect`() = runTest {
        // Subscribe
        val events = mutableListOf<RawLedgerEvent>()
        val job = launch {
            client.subscribeToZswapEvents()
                .take(10)
                .collect { events.add(it) }
        }

        // Receive first event
        delay(1000)
        assertTrue(events.isNotEmpty())

        // Simulate disconnect (restart indexer)
        // ...

        // Verify reconnection and continued streaming
        job.join()
        assertTrue(events.size >= 10)
    }

    @Test
    fun `deserializes events to typed objects`() = runTest {
        val deserializer = EventDeserializer(context)

        client.subscribeToZswapEvents()
            .take(5)
            .collect { rawEvent ->
                // Deserialize
                val typedEvent = deserializer.deserialize(rawEvent.rawHex)

                // Verify typed event
                assertNotNull(typedEvent)
                when (typedEvent) {
                    is LedgerEvent.Transfer -> {
                        assertNotNull(typedEvent.from)
                        assertNotNull(typedEvent.to)
                        assertTrue(typedEvent.amount.toBigInteger() > BigInteger.ZERO)
                    }
                    // ... other event types
                }
            }
    }
}
```

---

## Deliverables

### Code

1. ✅ **WebSocket Client**
   - `GraphQLWsMessage.kt` - Protocol message models
   - `GraphQLWsClient.kt` - WebSocket client implementation
   - `ReconnectPolicy.kt` - Exponential backoff

2. ✅ **Event Deserialization**
   - `EventDeserializer.kt` - WASM bridge
   - `LedgerEvent.kt` - Typed event models
   - `assets/ledger.wasm` - Compiled WASM module

3. ✅ **TLS Security**
   - `TlsConfig.kt` - Certificate fingerprints
   - Updated `IndexerClientImpl.kt` - OkHttp with pinning

4. ✅ **Tests**
   - `GraphQLWsClientTest.kt` - WebSocket protocol tests
   - `EventDeserializerTest.kt` - WASM parsing tests
   - `TlsCertificatePinningTest.kt` - Security tests
   - `IndexerIntegrationTest.kt` - End-to-end tests

### Documentation

1. ✅ **Architecture Doc**
   - WebSocket protocol flow diagram
   - WASM integration architecture
   - TLS certificate rotation procedure

2. ✅ **Test Report**
   - Test coverage summary
   - Integration test results
   - Performance benchmarks

---

## Timeline

### Week 1: WebSocket Subscriptions (12-15 hours)

**Day 1-2: Protocol Implementation (6-8 hours)**
- Message models
- Connection lifecycle
- Subscribe/unsubscribe logic

**Day 3: Integration (3-4 hours)**
- Update IndexerClientImpl
- Wire up to existing cache
- Basic error handling

**Day 4: Tests (3 hours)**
- Unit tests for protocol
- Mock WebSocket server tests

### Week 2: WASM & TLS (13-20 hours)

**Day 1-2: WASM Setup (5-7 hours)**
- Compile ledger WASM
- Choose runtime (Wasmer vs manual)
- Basic deserialization test

**Day 3: Event Parsing (4-6 hours)**
- Implement deserializer
- Create typed event models
- Handle all event types

**Day 4: TLS Pinning (2-3 hours)**
- Extract certificates
- Configure OkHttp
- Test pinning enforcement

**Day 5: Integration & Testing (2-4 hours)**
- End-to-end integration test
- Performance benchmarks
- Documentation

---

## Success Criteria

### Functional

- ✅ WebSocket subscriptions receive real-time events
- ✅ Automatic reconnection after disconnect
- ✅ Events deserialized to typed Kotlin objects
- ✅ TLS certificate pinning enforced in production

### Non-Functional

- ✅ 30+ new tests (all passing)
- ✅ <100ms latency for event streaming
- ✅ <50ms deserialization per event
- ✅ Reconnection within 10 seconds after disconnect

### Documentation

- ✅ WebSocket protocol documented
- ✅ WASM integration guide written
- ✅ Certificate rotation procedure documented

---

## Risk Mitigation

### Risk 1: WASM Integration Complexity 🔴

**Probability:** High
**Impact:** High (blocks typed events)

**Mitigation:**
1. Start with Wasmer (simplest option)
2. Have fallback to manual SCALE parsing
3. Worst case: Keep raw hex in Phase 4B, defer parsing to Phase 5

### Risk 2: WebSocket Stability 🟡

**Probability:** Medium
**Impact:** Medium (reconnection issues)

**Mitigation:**
1. Robust reconnection logic with exponential backoff
2. Extensive integration testing with network interruptions
3. Fallback to HTTP polling if WebSocket fails

### Risk 3: Certificate Pinning Breakage 🟡

**Probability:** Low
**Impact:** High (production outage)

**Mitigation:**
1. Pin multiple certificates (rotation support)
2. Monitor certificate expiry (automated alerts)
3. Development mode bypass for testing

---

## Dependencies

### External Libraries

```kotlin
// WebSocket
implementation("io.ktor:ktor-client-okhttp:2.3.7")

// WASM Runtime
implementation("io.github.wasmerio:wasmer-jni:0.3.0")

// TLS
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:okhttp-tls:4.12.0")

// Testing
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

### Midnight Components

- `@midnight-ntwrk/ledger-v7` WASM binary
- Midnight indexer GraphQL endpoint (testnet/mainnet)

---

## Next Steps After Phase 4B

**Phase 4C: Wallet State Management**
- Room database for persistent storage
- Sync coordinator (HTTP + WebSocket)
- Background sync worker

**Phase 5: Transaction Building**
- Unshielded transaction creation
- UTXO management
- Fee calculation

---

## Questions for User

1. **WASM Priority:** Should we prioritize WASM integration, or is it OK to keep raw hex for now and defer typed events to Phase 5?

2. **Certificate Access:** Can you provide Midnight's production certificate fingerprints, or should I extract them from the testnet indexer?

3. **Timeline:** Is 3-4 weeks acceptable for Phase 4B (25-35 hours), or do you need it faster?

4. **Local Testing:** Do you have access to a local Midnight indexer for integration testing?

---

**Status:** Ready to begin Phase 4B implementation
**Next Action:** Get user approval and start with WebSocket subscriptions
