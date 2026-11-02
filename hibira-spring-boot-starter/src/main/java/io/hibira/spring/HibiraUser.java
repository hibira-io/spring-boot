package io.hibira.spring;

/**
 * A signed-in user as Hibira sees it: the ID connections sign in with (and
 * presence members, push and inbox use) and optional user info.
 *
 * @param id the user ID: up to 148 of a-z A-Z 0-9 _ - = @ , . ;
 * @param info what other presence members see, or null
 */
public record HibiraUser(String id, Object info) {

    /** A user without info. */
    public static HibiraUser of(String id) {
        return new HibiraUser(id, null);
    }
}
