package sn.mixx.expresso.exception;

public class AntiFraudException extends RuntimeException {
    private final String reason;

    public AntiFraudException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public String getReason() { return reason; }
}