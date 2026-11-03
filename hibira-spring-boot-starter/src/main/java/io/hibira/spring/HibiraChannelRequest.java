package io.hibira.spring;

import java.security.Principal;
import java.util.Map;

/**
 * A subscription being authorized.
 *
 * @param socketId the connection
 * @param channel the channel
 * @param principal the request's principal, or null
 * @param user the signed-in user, or null
 * @param params the values of the pattern's placeholders
 */
public record HibiraChannelRequest(String socketId, String channel, Principal principal, HibiraUser user, Map<String, String> params) {
}
