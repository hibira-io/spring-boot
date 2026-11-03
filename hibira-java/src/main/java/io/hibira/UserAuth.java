package io.hibira;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What a user authentication (sign-in) endpoint returns to the client.
 *
 * @param auth "key:signature"
 * @param userData the user data as a JSON string
 */
public record UserAuth(
        @JsonProperty("auth") String auth,
        @JsonProperty("user_data") String userData) {
}
