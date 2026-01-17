package com.midnight.kuira.core.indexer.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * GraphQL WebSocket protocol message types.
 *
 * **Protocol:** graphql-transport-ws
 * **Spec:** https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md
 *
 * All messages are JSON-encoded and sent as text frames over WebSocket.
 */
@Serializable
sealed class GraphQLWebSocketMessage {

    abstract val type: String

    /**
     * Client → Server: Initialize connection.
     *
     * Sent immediately after WebSocket connection established.
     * Server responds with ConnectionAck or closes with 4401.
     */
    @Serializable
    @SerialName("connection_init")
    data class ConnectionInit(
        override val type: String = "connection_init",
        val payload: JsonObject? = null
    ) : GraphQLWebSocketMessage()

    /**
     * Server → Client: Connection acknowledged.
     *
     * Server accepts connection init. Client can now send subscriptions.
     */
    @Serializable
    @SerialName("connection_ack")
    data class ConnectionAck(
        override val type: String = "connection_ack",
        val payload: JsonObject? = null
    ) : GraphQLWebSocketMessage()

    /**
     * Client → Server: Subscribe to GraphQL operation.
     *
     * @param id Unique operation ID (client-generated)
     * @param payload GraphQL operation details
     */
    @Serializable
    @SerialName("subscribe")
    data class Subscribe(
        override val type: String = "subscribe",
        val id: String,
        val payload: SubscribePayload
    ) : GraphQLWebSocketMessage()

    /**
     * Server → Client: Operation result.
     *
     * Sent for each result in subscription stream.
     */
    @Serializable
    @SerialName("next")
    data class Next(
        override val type: String = "next",
        val id: String,
        val payload: JsonElement
    ) : GraphQLWebSocketMessage()

    /**
     * Server → Client: Operation error.
     *
     * Sent when operation fails validation or execution.
     */
    @Serializable
    @SerialName("error")
    data class Error(
        override val type: String = "error",
        val id: String,
        val payload: List<GraphQLError>
    ) : GraphQLWebSocketMessage()

    /**
     * Bidirectional: Operation complete.
     *
     * Server → Client: Subscription ended
     * Client → Server: Cancel subscription
     */
    @Serializable
    @SerialName("complete")
    data class Complete(
        override val type: String = "complete",
        val id: String
    ) : GraphQLWebSocketMessage()

    /**
     * Bidirectional: Ping (keep-alive).
     */
    @Serializable
    @SerialName("ping")
    data class Ping(
        override val type: String = "ping",
        val payload: JsonObject? = null
    ) : GraphQLWebSocketMessage()

    /**
     * Bidirectional: Pong (keep-alive response).
     */
    @Serializable
    @SerialName("pong")
    data class Pong(
        override val type: String = "pong",
        val payload: JsonObject? = null
    ) : GraphQLWebSocketMessage()
}

/**
 * Subscribe message payload.
 */
@Serializable
data class SubscribePayload(
    val query: String,
    val operationName: String? = null,
    val variables: JsonObject? = null,
    val extensions: JsonObject? = null
)

/**
 * GraphQL error object.
 */
@Serializable
data class GraphQLError(
    val message: String,
    val locations: List<ErrorLocation>? = null,
    val path: List<String>? = null,
    val extensions: JsonObject? = null
)

/**
 * Error location in GraphQL document.
 */
@Serializable
data class ErrorLocation(
    val line: Int,
    val column: Int
)
