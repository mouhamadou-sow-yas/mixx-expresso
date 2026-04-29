package sn.mixx.expresso.security;

import org.springframework.security.core.AuthenticationException;

public class OtpInvalidException extends AuthenticationException {
    public OtpInvalidException(String message) {
        super(message);
    }
}