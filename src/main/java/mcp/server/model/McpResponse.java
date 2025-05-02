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
package mcp.server.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record McpResponse(
    String jsonrpc,
    Long id,
    Object result, // Can be ListToolsResult, CallToolResult, etc.
    McpError error // Optional error field
) {
    public McpResponse(Long id, Object result) {
        this("2.0", id, result, null);
    }
    public McpResponse(Long id, McpError error) {
        this("2.0", id, null, error);
    }
}
