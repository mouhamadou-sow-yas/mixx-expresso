package sn.mixx.expresso.exception;

public class TransactionExpiredException extends RuntimeException {
    public TransactionExpiredException(String message) { super(message); }
}