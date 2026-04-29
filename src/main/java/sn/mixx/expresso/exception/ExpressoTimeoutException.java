package sn.mixx.expresso.exception;

public class ExpressoTimeoutException extends RuntimeException {
    public ExpressoTimeoutException(String message) { super(message); }
    public ExpressoTimeoutException(String message, Throwable cause) { super(message, cause); }
}