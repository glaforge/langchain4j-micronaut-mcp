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

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import io.micronaut.context.annotation.Property;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import mcp.server.SseController; // Import to access POST_ENDPOINT_PATH if needed directly
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the MCP (Multi-Capability Platform) weather client.
 * This test class verifies the interaction between a client application and an MCP server
 * providing weather information. It uses a chat language model (Vertex AI Gemini) to process
 * user requests and retrieve weather forecasts via the MCP.
 */
@MicronautTest // Starts the Micronaut application context and server
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // Allows non-static @BeforeAll/@AfterAll
// Optional: Define properties for the test, like the server port
// @Property(name = "micronaut.server.port", value = "-1") // Use random port
public class McpWeatherClientTest {

    private static final Logger log = LoggerFactory.getLogger(McpWeatherClientTest.class);

    @Inject
    EmbeddedServer server; // Inject the running server instance

    // --- Resources to be managed ---
    private ChatModel model;
    private McpTransport transport;
    private McpClient mcpClient;
    private WeatherAssistant weatherAssistant;

    // Define the AI service interface within the test or import if separate
    interface WeatherAssistant {
        String request(String message);
    }

    @BeforeAll
    void setup() throws IOException {
        log.info("Setting up resources for McpWeatherClientTest...");

        // --- Initialize resources ---
        // Note: Consider mocking VertexAI for true unit tests or if external calls are slow/costly.
        // This setup assumes you want to test the integration including the actual model call.
        model = VertexAiGeminiChatModel.builder()
            .project(System.getenv("GCP_PROJECT_ID")) // Replace with your project ID
            .location("us-central1")    // Replace with your location
            .modelName("gemini-2.0-flash") // Or your desired model
            .listeners(List.of(new ChatModelListener() {
                @Override
                public void onRequest(ChatModelRequestContext requestContext) {
                    log.info("Gemini got request: {}", requestContext.chatRequest());
                }

                @Override
                public void onResponse(ChatModelResponseContext responseContext) {
                    log.info("Gemini sent response: {}", responseContext.chatResponse());
                }
            }))
            .build();

        // Construct the SSE URL based on the running server's port
        String sseUrl = "http://localhost:" + server.getPort() + SseController.SSE_ENDPOINT_PATH;
        log.info("Using SSE URL: {}", sseUrl);

        transport = new HttpMcpTransport.Builder()
            .sseUrl(sseUrl)
            // Add postUrl if your transport requires it explicitly,
            // otherwise it might infer from the 'endpoint' event.
            // .postUrl("http://localhost:" + server.getPort() + SseController.POST_ENDPOINT_PATH)
            .build();

        mcpClient = new DefaultMcpClient.Builder()
            .transport(transport)
            .build();

        ToolProvider toolProvider = McpToolProvider.builder()
            .mcpClients(List.of(mcpClient))
            .build();

        weatherAssistant = AiServices.builder(WeatherAssistant.class)
            .chatModel(model)
            .toolProvider(toolProvider)
            .build();

        log.info("Setup complete.");
    }

    @AfterAll
    void tearDown() throws Throwable {
        log.info("Tearing down resources...");
        // Close resources manually if they implement AutoCloseable and aren't managed beans
        if (transport instanceof AutoCloseable) {
            ((AutoCloseable) transport).close();
        }
        if (model instanceof AutoCloseable) {
            ((AutoCloseable) model).close();
        }
        // mcpClient might need closing depending on its implementation details
        // if (mcpClient instanceof AutoCloseable) { ((AutoCloseable) mcpClient).close(); }
        log.info("Teardown complete.");
    }

    @Test
    void testListTools() {
        log.info("Testing listTools...");
        assertDoesNotThrow(() -> {
            List<dev.langchain4j.agent.tool.ToolSpecification> tools = mcpClient.listTools();
            assertNotNull(tools, "Tool list should not be null");
            assertFalse(tools.isEmpty(), "Tool list should not be empty");
            // Add more specific assertions if needed, e.g., check tool names
            assertTrue(tools.stream().anyMatch(t -> "getWeatherForecast".equals(t.name())),
                "Should find the 'getWeatherForecast' tool");
            log.info("listTools returned: {}", tools);
        }, "Listing tools should not throw an exception");
    }

    @Test
    void testWeatherRequest() {
        log.info("Testing weather request...");
        String question = "What's the weather like in Paris today?";
        String response = assertDoesNotThrow(() -> weatherAssistant.request(question),
            "Weather request should not throw an exception");

        log.info("Question: {}", question);
        log.info("Response: {}", response);

        assertNotNull(response, "Response should not be null");
        assertFalse(response.isBlank(), "Response should not be blank");
        // Check if the response likely contains the mocked forecast
        assertTrue(response.toLowerCase().contains("sunny"),
            "Response should contain the weather information (sunny)");
    }

    @Test
    void testSimpleGreeting() {
        log.info("Testing simple greeting...");
        String question = "Hello!";
        String response = assertDoesNotThrow(() -> weatherAssistant.request(question),
            "Greeting request should not throw an exception");

        log.info("Question: {}", question);
        log.info("Response: {}", response);

        assertNotNull(response, "Response should not be null");
        assertFalse(response.isBlank(), "Response should not be blank");
        // Add assertions based on expected greeting behavior from the LLM
        // For example, it shouldn't contain the weather forecast.
        assertFalse(response.toLowerCase().contains("sunny"),
            "Simple greeting response should not contain weather info");
    }
}