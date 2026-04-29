package sn.mixx.expresso.exception;

public class ExpressoBusinessFailureException extends RuntimeException {
    private final int resultCode;

    public ExpressoBusinessFailureException(String message, int resultCode) {
        super(message);
        this.resultCode = resultCode;
    }

    public int getResultCode() { return resultCode; }
}