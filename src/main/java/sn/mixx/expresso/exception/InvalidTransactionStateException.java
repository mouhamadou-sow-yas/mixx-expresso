package sn.mixx.expresso.exception;

public class InvalidTransactionStateException extends RuntimeException {
    public InvalidTransactionStateException(String message) { super(message); }
}