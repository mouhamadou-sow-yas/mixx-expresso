package sn.mixx.expresso.web.rest.errors.mfa;

public class MfaException extends RuntimeException {

    private final MfaErrorCode errorCode;

    public MfaException(MfaErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MfaErrorCode getErrorCode() { return errorCode; }
}