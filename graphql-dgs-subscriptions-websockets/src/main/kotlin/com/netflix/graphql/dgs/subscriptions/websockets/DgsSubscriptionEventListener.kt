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

package com.netflix.graphql.dgs.subscriptions.websockets

import com.netflix.graphql.types.subscription.OperationMessage
import org.springframework.web.socket.WebSocketSession

/**
 * Implementation of Lifecycle Events
 * https://www.apollographql.com/docs/graphql-subscriptions/lifecycle-events/
 */
interface DgsSubscriptionEventListener {

    /**
     * Allows validation of connectionParams prior to starting the connection.
     * You can reject the connection by throwing an exception.
     */
    fun onConnect(connectionParams: Any?, session: WebSocketSession) = Unit

    /**
     * called when the client disconnects.
     */
    fun onDisconnect(session: WebSocketSession) = Unit

    /**
     * called when the client executes a GraphQL operation - use this method to create custom params that will be used
     * when resolving the operation. You can use this method to override the GraphQL schema that will be used in the
     * operation.
     */
    fun onOperation(message: OperationMessage, params: Any?, session: WebSocketSession) = Unit

    fun onOperationComplete(session: WebSocketSession, operationId: String) = Unit
}

open class DefaultLifecycleEventListener : DgsSubscriptionEventListener
