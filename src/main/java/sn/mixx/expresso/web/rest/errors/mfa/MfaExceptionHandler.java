package sn.mixx.expresso.web.rest.errors.mfa;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.security.OtpExpiredException;
import sn.mixx.expresso.security.OtpInvalidException;

import java.util.Map;

@RestControllerAdvice
public class MfaExceptionHandler {

    @ExceptionHandler(OtpInvalidException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleOtpInvalid(OtpInvalidException ex) {
        return Mono.just(ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "OTP_INVALID", "message", ex.getMessage())));
    }

    @ExceptionHandler(OtpExpiredException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleOtpExpired(OtpExpiredException ex) {
        return Mono.just(ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "OTP_EXPIRED", "message", ex.getMessage())));
    }

    @ExceptionHandler(MfaException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleMfaException(MfaException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case MFA_REQUIRED_FOR_ROLE -> HttpStatus.FORBIDDEN;
            case MFA_SESSION_INVALID -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNAUTHORIZED;
        };
        return Mono.just(ResponseEntity
            .status(status)
            .body(Map.of("error", ex.getErrorCode().name(), "message", ex.getMessage())));
    }
}