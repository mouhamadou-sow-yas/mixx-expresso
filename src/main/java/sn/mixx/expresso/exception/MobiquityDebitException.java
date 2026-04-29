package sn.mixx.expresso.exception;

public class MobiquityDebitException extends RuntimeException {
    public MobiquityDebitException(String message) { super(message); }
    public MobiquityDebitException(String message, Throwable cause) { super(message, cause); }
}