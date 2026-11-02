package io.hibira.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class EndpointParams {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EndpointParams() {
    }

    static Map<String, String> of(String socketId, String channel) {
        Map<String, String> m = new HashMap<>();
        m.put("socket_id", socketId);
        m.put("channel_name", channel);
        return m;
    }

    static Map<String, String> fromJson(byte[] body) {
        try {
            JsonNode n = MAPPER.readTree(body);
            if (n == null || !n.isObject()) {
                return of(null, null);
            }
            return of(text(n, "socket_id"), text(n, "channel_name"));
        } catch (IOException e) {
            return of(null, null);
        }
    }

    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
