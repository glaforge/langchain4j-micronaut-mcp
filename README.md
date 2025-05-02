# A simple MCP Server implemented with Micronaut

This project implements a simple MCP ([Model Context Protocol](https://modelcontextprotocol.io/introduction)) server, 
with an HTTP SSE transport, using [Micronaut](https://micronaut.io/).

A test class uses [LangChain4j's MCP client](https://docs.langchain4j.dev/tutorials/mcp) support to call and interact with the Micronaut MCP server.

## What it does

The server provides dummy weather information ☀️ for a given city. 

The MCP server implements a subset of the MCP protocol to handle requests for weather data.

In particular, it implements the following operations:
* `initialize`
* `notifications/initialize`
* `tools/list`
* `tools/call`

All the MCP protocol classes can be found in the `mcp.server.model` package.

There are two main controllers working together to implement the server-side of the MCP communication over HTTP/SSE:

### The `SseController` (serving `/mcp/sse`):

This controller acts as the entry point for the Server-Sent Events (SSE) connection. 
When an MCP client wants to connect, it first makes an HTTP GET request to this endpoint (`/mcp/sse`).

How it works:
* It's annotated with `@Controller("/mcp/sse")`.
* It has a single method `connectSse()` annotated with `@Get(produces = MediaType.TEXT_EVENT_STREAM)`. This tells Micronaut that GET requests to `/mcp/sse` should be handled by this method and that the response will be an SSE stream.
* It injects the `SseBroadcaster` singleton bean.
* The `connectSse()` method simply calls `broadcaster.getEventsPublisher()` and returns the result.

What it does: 
* It establishes the persistent SSE connection with the client. 
* It delegates the responsibility of actually sending events over this connection to the SseBroadcaster. The broadcaster ensures the first event sent tells the client where to send POST requests (the endpoint event), and then sends subsequent responses or notifications.

### The `PostController` (`/mcp/post`):

This controller handles the incoming MCP command requests sent by the client after the SSE connection is established. The client learns the path for this controller (`/mcp/post`) from the initial endpoint event received via the SseController.

How it works:

* It's annotated with `@Controller("/mcp/post")`.
* It has a method `handleMcpPostRequest(@Body McpRequest request)` annotated with `@Post(consumes = MediaType.APPLICATION_JSON)`. This means it handles HTTP POST requests to `/mcp/post` where the body contains JSON data conforming to the `McpRequest` structure.
* It also injects the `SseBroadcaster`.

Inside `handleMcpPostRequest`:
* It deserializes the JSON request body into an `McpRequest` object.
* It calls a private helper method `processRequest(request)` to determine the appropriate action based on the `request.method()` (e.g., `initialize`, `tools/list`, `tools/call`).
* `processRequest` generates an `McpResponse` object containing the result (or an error, or `null` for notifications).
* If `processRequest` returns a response object, `handleMcpPostRequest` calls `broadcaster.broadcastResponse(mcpResponse)`. This sends the actual MCP result back to the client over the previously established SSE connection.
* Finally, it returns an immediate `HttpResponse.ok()` to the original POST request. This HTTP response simply acknowledges that the server received the POST request; it does not contain the actual MCP result.

What it does: 
* It receives specific commands from the MCP client (like "list available tools" or "execute the weather tool"). It processes these commands, generates the corresponding MCP response, and uses the `SseBroadcaster` to send that response back asynchronously over the SSE channel managed initially by the `SseController`.

### The `SseBroadcaster`

The `SseBroadcaster` manages the SSE stream, sends the initial configuration (endpoint event), and provides a way for other parts of the server (like the `PostController`) to send JSON-formatted responses and notifications back to the connected client over that stream.

## The `McpWeatherClientTest` client

The `McpWeatherClientTest` class is an integration test that verifies the functionality of the MCP server.

You can run the test class with `./gradlew test`.

What it does:
* It starts a local server (via Micronaut).
* It sets up a LangChain4j AI assistant (`WeatherAssistant`) configured to use Google Cloud Vertex AI's Gemini 2.0 Flash mode.
* It configures this assistant to find and use tools provided by the local server via a specific protocol (MCP over HTTP/SSE).
* It tests if the client can discover the tools correctly.
* It tests if the assistant correctly uses the remote weather tool when asked about weather.
* It tests if the assistant correctly avoids using the weather tool for unrelated questions like simple greeting prompts.

---

>[!NOTE]
> This project is not an official Google project.


