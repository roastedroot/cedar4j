package io.roastedroot.cedar4j;

public final class CedarException extends RuntimeException {

    public CedarException(String message) {
        super(message);
    }

    public CedarException(String message, Throwable cause) {
        super(message, cause);
    }
}
