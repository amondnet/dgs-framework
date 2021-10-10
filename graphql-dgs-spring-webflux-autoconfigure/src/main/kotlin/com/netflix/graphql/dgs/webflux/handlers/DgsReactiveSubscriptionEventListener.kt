/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs.webflux.handlers

import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono

/**
 * Implementation of Lifecycle Events
 * https://www.apollographql.com/docs/graphql-subscriptions/lifecycle-events/
 */
interface DgsReactiveSubscriptionEventListener {

    /**
     * Allows validation of connectionParams prior to starting the connection.
     * You can reject the connection by throwing an exception.
     */
    fun onConnect(connectionParams: Any?, session: WebSocketSession) = Mono.empty<Map<String, Any>>()

    /**
     * called when the client disconnects.
     */
    fun onDisconnect(session: WebSocketSession) = Mono.empty<Void>()

    /**
     * called when the client executes a GraphQL operation - use this method to create custom params that will be used
     * when resolving the operation.
     */
    fun onOperation(message: OperationMessage, params: Any?, session: WebSocketSession) = Mono.empty<Any?>()

    /**
     * called when client's operation has been done it's execution (for subscriptions called when unsubscribe, and
     * for query/mutation called immediately).
     */
    fun onOperationComplete(session: WebSocketSession, operationId: String) = Mono.empty<Void>()
}

open class DefaultReactiveLifecycleEventListener : DgsReactiveSubscriptionEventListener
