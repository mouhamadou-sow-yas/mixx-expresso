package sn.mixx.expresso.security;

import org.springframework.security.core.AuthenticationException;

public class OtpExpiredException extends AuthenticationException {
    public OtpExpiredException(String message) {
        super(message);
    }
}