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

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Inject;
import mcp.server.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller(SseController.POST_ENDPOINT_PATH) // Use the path defined in SseController
public class PostController {

    private static final Logger log = LoggerFactory.getLogger(PostController.class);
    private static final String WEATHER_TOOL_NAME = "getWeatherForecast";
    private static final String FAKE_WEATHER_JSON = "{\"forecast\": \"sunny\"}";

    @Inject
    SseBroadcaster broadcaster; // Inject the broadcaster

    @Post(consumes = MediaType.APPLICATION_JSON) // No produces needed, response goes via SSE
    public HttpResponse<?> handleMcpPostRequest(@Body McpRequest request) {
        log.info("Received MCP POST Request: ID={}, Method={}", request.id(), request.method());

        McpResponse mcpResponse = processRequest(request);

        if (mcpResponse != null) {
            // Send the response back over the SSE channel
            broadcaster.broadcastResponse(mcpResponse);
        } else {
            // Handle cases like notifications that might not generate an McpResponse object
            // In our case, 'notifications/initialized' falls here.
            log.debug("No explicit response object generated for method '{}', assuming notification ack.", request.method());
        }

        // Return an immediate HTTP 200 OK or 202 Accepted to acknowledge receipt of the POST.
        // The actual result is sent asynchronously via SSE.
        // 200 OK might be simpler for clients expecting some response body even if empty.
        // 202 Accepted explicitly states processing is happening elsewhere. Let's use 200.
        return HttpResponse.ok();
    }

    private McpResponse processRequest(McpRequest request) {
        // --- Same logic as before to generate the McpResponse object ---
        switch (request.method()) {
            case "initialize":
                log.info("Handling initialize request");
                InitializeResult initResult = new InitializeResult(new ServerCapabilities());
                return new McpResponse(request.id(), initResult);

            case "notifications/initialized":
                log.info("Received initialized notification");
                // This is a notification FROM the client. MCP spec says notifications
                // don't have responses. So we return null here, and the POST handler
                // will just return HTTP OK.
                return null;

            case "tools/list":
                log.info("Handling tools/list request");
                ToolSpecificationData weatherTool = new ToolSpecificationData(
                    WEATHER_TOOL_NAME,
                    "Gets the current weather forecast.",
                    new InputSchema(
                        "object",
                        Map.of("location", Map.of(
                            "type", "string",
                            "description", "Location to get the weather for")
                        ),
                        List.of("location"),
                        false)
                );
                ListToolsResult listResult = new ListToolsResult(List.of(weatherTool));
                return new McpResponse(request.id(), listResult);

            case "tools/call":
                log.info("Handling tools/call request");
                if (request.params() != null && request.params().has("name")) {
                    String toolName = request.params().get("name").asText();
                    if (WEATHER_TOOL_NAME.equals(toolName)) {
                        log.info("Executing tool: {}", toolName);
                        TextContentData textContent = new TextContentData(FAKE_WEATHER_JSON);
                        CallToolResult callResult = new CallToolResult(List.of(textContent));
                        return new McpResponse(request.id(), callResult);
                    } else {
                        log.warn("Unknown tool requested: {}", toolName);
                        return new McpResponse(request.id(), new McpError(-32601, "Method not found: " + toolName));
                    }
                } else {
                    log.error("Invalid tools/call request: Missing 'name' in params");
                    return new McpResponse(request.id(), new McpError(-32602, "Invalid params for tools/call"));
                }

            case "ping":
                log.info("Handling ping request");
                return new McpResponse(request.id(), Collections.emptyMap());

            default:
                log.warn("Unsupported MCP method: {}", request.method());
                return new McpResponse(request.id(), new McpError(-32601, "Method not found: " + request.method()));
        }
    }
}
