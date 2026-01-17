package com.midnight.kuira.core.indexer.websocket

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Integration tests for GraphQLWebSocketClient.
 *
 * **IMPORTANT:** These tests connect to LIVE testnet indexer!
 * - Requires internet connection
 * - Requires Midnight testnet indexer to be online
 * - Tests are @Ignore by default (run manually)
 *
 * **To run:**
 * 1. Remove @Ignore annotation
 * 2. Ensure testnet indexer is accessible
 * 3. Run: `./gradlew :core:indexer:testDebugUnitTest --tests "*GraphQLWebSocketClientTest*"`
 */
class GraphQLWebSocketClientTest {

    private lateinit var httpClient: HttpClient
    private lateinit var wsClient: GraphQLWebSocketClient

    // Note: Using Preview network (testnet-02 is currently down)
    // Preview runs ledger v6, Testnet-02 runs ledger v4
    private val testnetIndexerWsUrl = "wss://indexer.preview.midnight.network/api/v3/graphql/ws"

    @Before
    fun setup() {
        httpClient = HttpClient(CIO) {
            install(WebSockets) {
                pingInterval = 20_000 // 20 seconds
            }
        }
        wsClient = GraphQLWebSocketClient(
            httpClient = httpClient,
            url = testnetIndexerWsUrl,
            connectionTimeout = 10_000
        )
    }

    @After
    fun teardown() = runBlocking<Unit> {
        wsClient.close()
        httpClient.close()
    }

    @Test
    fun `can connect to testnet indexer`() = runBlocking<Unit> {
        // Connect to indexer
        wsClient.connect()

        // If we get here, connection was successful
        assertTrue("Connected to testnet indexer", true)
    }

    @Test
    fun `can subscribe to blocks`() = runBlocking<Unit> {
        wsClient.connect()

        val query = """
            subscription {
                blocks {
                    height
                    hash
                    timestamp
                }
            }
        """.trimIndent()

        // Subscribe and get first block
        withTimeout(30_000) {
            val firstBlock = wsClient.subscribe(query).first()
            assertNotNull("Received first block", firstBlock)
            println("First block: $firstBlock")
        }
    }

    @Test
    fun `can subscribe to unshielded transactions`() = runBlocking<Unit> {
        wsClient.connect()

        // Use a simpler query without variables to test subscription works
        // Just verify we can subscribe, not that we get specific data
        val query = """
            subscription {
                unshieldedTransactions(address: "mn_addr_testnet1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqz8l9n5u") {
                    __typename
                    ... on UnshieldedTransactionsProgress {
                        highestTransactionId
                    }
                }
            }
        """.trimIndent()

        // Subscribe and try to get first event
        // If no events within timeout, that's OK - we just verify subscription doesn't error
        withTimeout(10_000) {
            try {
                val events = wsClient.subscribe(query).take(1).toList()
                // If we get here, subscription worked (may or may not have data)
                println("Unshielded transactions subscription received: ${events.size} events")
                if (events.isNotEmpty()) {
                    println("First event: ${events[0]}")
                }
                assertTrue("Subscription completed without error", true)
            } catch (e: NoSuchElementException) {
                // Subscription completed immediately with no data - that's OK
                // This can happen if the server sends Complete immediately
                println("Subscription completed with no data (OK for test address)")
                assertTrue("Subscription completed", true)
            } catch (e: WebSocketSubscriptionException) {
                // Print error details
                println("GraphQL Error: ${e.message}")
                throw e // Re-throw to fail test
            }
        }
    }

    @Test
    fun `can send ping`() = runBlocking<Unit> {
        wsClient.connect()
        wsClient.ping()
        // If no exception, ping was successful
        assertTrue("Ping sent successfully", true)
    }
}
