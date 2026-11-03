package io.hibira;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A channel's state.
 *
 * @param occupied whether anyone is subscribed
 * @param userCount distinct users on a presence channel (asked for with info "user_count"), or null
 * @param subscriptionCount subscriptions (asked for with info "subscription_count"), or null
 */
public record ChannelInfo(
        @JsonProperty("occupied") boolean occupied,
        @JsonProperty("user_count") Integer userCount,
        @JsonProperty("subscription_count") Integer subscriptionCount) {
}
