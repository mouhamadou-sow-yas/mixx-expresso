package sn.mixx.expresso.exception;

public class ExpressoUnavailableException extends RuntimeException {
    public ExpressoUnavailableException(String message) { super(message); }
    public ExpressoUnavailableException(String message, Throwable cause) { super(message, cause); }
}