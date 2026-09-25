package net.momirealms.sparrow.feature.head;

public final class HeadFetchException extends RuntimeException {
    private final Reason reason;

    public HeadFetchException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() { return this.reason; }

    public enum Reason {
        THROTTLED, INVALID_RESPONSE, INVALID_INPUT, SERVICE_ERROR
    }
}
