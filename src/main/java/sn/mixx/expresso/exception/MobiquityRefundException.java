package sn.mixx.expresso.exception;

public class MobiquityRefundException extends RuntimeException {
    public MobiquityRefundException(String message) { super(message); }
    public MobiquityRefundException(String message, Throwable cause) { super(message, cause); }
}