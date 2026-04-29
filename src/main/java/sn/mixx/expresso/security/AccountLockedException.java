package sn.mixx.expresso.security;

import org.springframework.security.core.AuthenticationException;

public class AccountLockedException extends AuthenticationException {

    private final int remainingMinutes;

    public AccountLockedException(String message, int remainingMinutes) {
        super(message);
        this.remainingMinutes = remainingMinutes;
    }

    public int getRemainingMinutes() {
        return remainingMinutes;
    }
}