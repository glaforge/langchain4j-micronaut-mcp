/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package mcp.server;

import io.micronaut.http.sse.Event;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import mcp.server.model.McpResponse;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;

@Singleton // Manages the SSE sink
public class SseBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SseBroadcaster.class);

    // A "multicast" sink allows multiple subscribers (though we'll likely have one MCP client)
    // and allows publishing from other threads (like the POST request thread).
    private final Sinks.Many<Event<?>> sink = Sinks.many().multicast().onBackpressureBuffer();
    private final JsonMapper jsonMapper; // Inject Micronaut's JsonMapper

    public SseBroadcaster(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Called by the SSE endpoint to get the stream of events.
     *
     * @return Publisher of SSE Events
     */
    public Publisher<Event<?>> getEventsPublisher() {
        // Emit an initial "endpoint" event immediately when a client connects
        // We need to ensure this happens reliably upon subscription.
        // Using Flux.concat ensures the endpoint event is sent first.
        Event<String> endpointEvent = Event.of(SseController.POST_ENDPOINT_PATH).name("endpoint");

        return Flux.concat(
                Flux.just(endpointEvent), // Send endpoint event first
                sink.asFlux() // Then send subsequent messages from the sink
            ).doOnSubscribe(subscription -> log.info("SSE Client subscribed"))
            .doOnCancel(() -> log.info("SSE Client cancelled subscription"))
            .doOnError(error -> log.error("Error on SSE stream: {}", error.getMessage(), error));
        // Consider adding doFinally for cleanup if needed
    }

    /**
     * Called by the POST endpoint handler to send a response back over SSE.
     * @param response The McpResponse object to send.
     */
    public void broadcastResponse(McpResponse response) {
        try {
            // Responses are sent as events of type "message"
            String jsonResponse = jsonMapper.writeValueAsString(response);
            Event<String> messageEvent = Event.of(jsonResponse).name("message");

            Sinks.EmitResult result = sink.tryEmitNext(messageEvent);

            if (result.isFailure()) {
                log.error("Failed to emit SSE message (Response ID: {}): {}", response.id(), result);
                // Handle error - maybe the client disconnected?
            } else {
                log.debug("Successfully emitted SSE message for Response ID: {}", response.id());
            }
        } catch (IOException e) {
            log.error("Failed to serialize McpResponse (ID: {}) to JSON for SSE: {}", response.id(), e.getMessage(), e);
        }
    }

    /**
     * Sends a simple notification (like log messages, tool updates) over SSE.
     * This isn't used in the simple weather example but shows how non-response
     * messages would be sent.
     *
     * @param notification An object representing the notification.
     * @param eventName The SSE event name (e.g., "notifications/message").
     */
    public void broadcastNotification(Object notification, String eventName) {
        try {
            String jsonNotification = jsonMapper.writeValueAsString(notification);
            Event<String> notificationEvent = Event.of(jsonNotification).name(eventName);
            Sinks.EmitResult result = sink.tryEmitNext(notificationEvent);
            if (result.isFailure()) {
                log.error("Failed to emit SSE notification ({}): {}", eventName, result);
            }
        } catch (IOException e) {
            log.error("Failed to serialize notification ({}) to JSON for SSE: {}", eventName, e.getMessage(), e);
        }
    }
}
