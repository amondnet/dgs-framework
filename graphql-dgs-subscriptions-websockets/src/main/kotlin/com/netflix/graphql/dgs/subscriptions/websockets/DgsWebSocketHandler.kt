/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.graphql.dgs.subscriptions.websockets

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.types.subscription.*
import graphql.ExecutionResult
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.slf4j.LoggerFactory
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.SubProtocolCapable
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.annotation.PostConstruct

class DgsWebSocketHandler(
    private val dgsQueryExecutor: DgsQueryExecutor,
    private val dgsSubscriptionEventListener: DgsSubscriptionEventListener
) : TextWebSocketHandler(), SubProtocolCapable {

    internal val subscriptions = ConcurrentHashMap<String, MutableMap<String, Subscription>>()
    internal val sessions = CopyOnWriteArrayList<WebSocketSession>()

    @PostConstruct
    fun setupCleanup() {
        val timerTask = object : TimerTask() {
            override fun run() {
                sessions.filter { !it.isOpen }.forEach(this@DgsWebSocketHandler::cleanupSubscriptionsForSession)
            }
        }

        val timer = Timer(true)
        timer.scheduleAtFixedRate(timerTask, 0, 5000)
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val operationMessage = objectMapper.readValue(message.payload, OperationMessage::class.java)
        val (type, payload, id) = operationMessage
        when (type) {
            GQL_CONNECTION_INIT -> {
                logger.info("Initialized connection for {}", session.id)
                try {
                    dgsSubscriptionEventListener.onConnect(payload, session)
                    sessions.add(session)
                    session.sendMessage(
                        TextMessage(
                            objectMapper.writeValueAsBytes(
                                OperationMessage(
                                    GQL_CONNECTION_ACK
                                )
                            )
                        )
                    )
                } catch (e: Throwable) {
                    session.sendMessage(
                        TextMessage(
                            objectMapper.writeValueAsBytes(
                                OperationMessage(
                                    GQL_CONNECTION_ERROR,
                                    payload, id
                                )
                            )
                        )
                    )
                    // Close the connection with an error code
                    session.close(CloseStatus.SERVER_ERROR)
                }
            }
            GQL_START -> {
                // if we already have a subscription with this id, unsubscribe from it first
                if (subscriptions[session.id]?.containsKey(id) == true) {
                    unsubscribe(id!!, session)
                }
                val queryPayload = objectMapper.convertValue(payload, QueryPayload::class.java)
                dgsSubscriptionEventListener.onOperation(operationMessage, session, session)
                handleSubscription(id!!, queryPayload, session)
            }
            GQL_STOP -> {
                unsubscribe(id!!, session)
            }
            GQL_CONNECTION_TERMINATE -> {
                logger.info("Terminated session " + session.id)
                cleanupSubscriptionsForSession(session)
                subscriptions.remove(session.id)
                dgsSubscriptionEventListener.onDisconnect(session)
                session.close()
            }
            else -> session.sendMessage(
                TextMessage(
                    objectMapper.writeValueAsBytes(
                        OperationMessage(
                            "Invalid message type!",
                            null,
                            id
                        )
                    )
                )
            )
        }
    }

    private fun unsubscribe(operationId: String, session: WebSocketSession) {
        subscriptions[session.id]?.get(operationId)?.cancel()
        subscriptions[session.id]?.remove(operationId)
        dgsSubscriptionEventListener.onOperationComplete(session, operationId)
    }

    private fun cleanupSubscriptionsForSession(session: WebSocketSession) {
        logger.info("Cleaning up for session {}", session.id)
        subscriptions[session.id]?.forEach {
            unsubscribe(it.key, session)
        }
        subscriptions.remove(session.id)
        sessions.remove(session)
    }

    private fun handleSubscription(id: String, payload: QueryPayload, session: WebSocketSession) {
        val executionResult: ExecutionResult = dgsQueryExecutor.execute(payload.query, payload.variables)
        val subscriptionStream: Publisher<ExecutionResult> = executionResult.getData()

        subscriptionStream.subscribe(object : Subscriber<ExecutionResult> {
            override fun onSubscribe(s: Subscription) {
                logger.info("Subscription started for {}", id)
                subscriptions.putIfAbsent(session.id, mutableMapOf())
                subscriptions[session.id]?.set(id, s)

                s.request(1)
            }

            override fun onNext(er: ExecutionResult) {
                val message = OperationMessage(GQL_DATA, DataPayload(er.getData()), id)
                val jsonMessage = TextMessage(objectMapper.writeValueAsBytes(message))
                logger.debug("Sending subscription data: {}", jsonMessage)

                if (session.isOpen) {
                    session.sendMessage(jsonMessage)
                    subscriptions[session.id]?.get(id)?.request(1)
                }
            }

            override fun onError(t: Throwable) {
                logger.error("Error on subscription {}", id, t)
                val message = OperationMessage(GQL_ERROR, DataPayload(null, listOf(t.message!!)), id)
                val jsonMessage = TextMessage(objectMapper.writeValueAsBytes(message))
                logger.debug("Sending subscription error: {}", jsonMessage)

                if (session.isOpen) {
                    session.sendMessage(jsonMessage)
                }
            }

            override fun onComplete() {
                logger.info("Subscription completed for {}", id)
                val message = OperationMessage(GQL_COMPLETE, null, id)
                val jsonMessage = TextMessage(objectMapper.writeValueAsBytes(message))

                if (session.isOpen) {
                    session.sendMessage(jsonMessage)
                }

                subscriptions[session.id]?.remove(id)
            }
        })
    }

    private companion object {
        val logger = LoggerFactory.getLogger(DgsWebSocketHandler::class.java)
        val objectMapper = jacksonObjectMapper()
        val protocol = "graphql-ws"
    }

    override fun getSubProtocols(): List<String> = listOf(protocol)
}
