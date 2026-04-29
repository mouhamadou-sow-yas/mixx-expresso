package sn.mixx.expresso.web.rest.errors.mfa;

public enum MfaErrorCode {
    OTP_INVALID,
    OTP_EXPIRED,
    MFA_REQUIRED_FOR_ROLE,
    MFA_SESSION_INVALID
}