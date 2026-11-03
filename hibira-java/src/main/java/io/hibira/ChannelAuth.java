package io.hibira;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What a channel authorization endpoint returns to the client.
 *
 * @param auth "key:signature"
 * @param channelData the presence member as a JSON string (presence channels only), or null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChannelAuth(
        @JsonProperty("auth") String auth,
        @JsonProperty("channel_data") String channelData) {
}
