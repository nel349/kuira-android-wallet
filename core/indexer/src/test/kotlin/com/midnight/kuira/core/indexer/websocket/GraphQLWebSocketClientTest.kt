package com.midnight.kuira.core.indexer.websocket

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.flow.first
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
    @Ignore("Integration test - requires live indexer. Remove @Ignore to test manually.")
    fun `can connect to testnet indexer`() = runBlocking<Unit> {
        // Connect to indexer
        wsClient.connect()

        // If we get here, connection was successful
        assertTrue("Connected to testnet indexer", true)
    }

    @Test
    @Ignore("Integration test - requires live indexer")
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
    @Ignore("Integration test - requires live indexer and known address with transactions")
    fun `can subscribe to unshielded transactions`() = runBlocking<Unit> {
        wsClient.connect()

        // Use a known testnet address (replace with actual address that has transactions)
        val testAddress = "mn_addr_testnet1..." // TODO: Add real test address

        val query = """
            subscription {
                unshieldedTransactions(address: "$testAddress", transactionId: 0) {
                    ... on UnshieldedTransaction {
                        transaction {
                            id
                            hash
                        }
                        createdUtxos {
                            owner
                            tokenType
                            value
                        }
                        spentUtxos {
                            owner
                            tokenType
                            value
                        }
                    }
                    ... on UnshieldedTransactionsProgress {
                        highestTransactionId
                    }
                }
            }
        """.trimIndent()

        // Subscribe and get first event
        withTimeout(30_000) {
            val firstEvent = wsClient.subscribe(query).first()
            assertNotNull("Received first transaction event", firstEvent)
            println("First transaction event: $firstEvent")
        }
    }

    @Test
    @Ignore("Manual test - ping/pong")
    fun `can send ping`() = runBlocking<Unit> {
        wsClient.connect()
        wsClient.ping()
        // If no exception, ping was successful
        assertTrue("Ping sent successfully", true)
    }
}
