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

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.sse.Event;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller("/mcp/sse") // Path for initial SSE connection
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    // Define the path for the POST endpoint relative to the server root
    // This must match the @Controller path in PostController
    public static final String POST_ENDPOINT_PATH = "/mcp/post";
    public static final String SSE_ENDPOINT_PATH = "/mcp/sse";

    @Inject
    SseBroadcaster broadcaster;

    @Get(produces = MediaType.TEXT_EVENT_STREAM)
    public Publisher<Event<?>> connectSse() {
        log.info("Client requesting SSE connection...");
        // The broadcaster handles sending the initial 'endpoint' event
        // and subsequent messages pushed via broadcastResponse/broadcastNotification.
        return broadcaster.getEventsPublisher();
    }
}
