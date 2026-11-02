package io.hibira;

/** A Hibira HTTP API request that failed, with the status and the error message the server returned. */
public class HibiraException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String body;

    /** Creates the exception for an answer with {@code status}. */
    public HibiraException(int status, String message, String body) {
        super("Hibira API " + status + ": " + message);
        this.status = status;
        this.body = body;
    }

    /** Creates the exception for a request that got no answer. */
    public HibiraException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
        this.body = "";
    }

    /** The HTTP status, or 0 when there was no answer (network error, timeout). */
    public int status() {
        return status;
    }

    /** The raw answer body. */
    public String body() {
        return body;
    }
}
