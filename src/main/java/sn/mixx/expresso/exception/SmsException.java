package sn.mixx.expresso.exception;

public class SmsException extends RuntimeException {
    private final String errorCode;

    public SmsException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}