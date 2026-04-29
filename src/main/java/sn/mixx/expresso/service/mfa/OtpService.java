package sn.mixx.expresso.service.mfa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.security.OtpExpiredException;
import sn.mixx.expresso.security.OtpInvalidException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Service
public class OtpService {

    private static final Logger LOG = LoggerFactory.getLogger(OtpService.class);

    private static final String OTP_PREFIX = "otp:";
    private static final String MFA_TOKEN_PREFIX = "mfa_token:";
    private static final Duration OTP_EXPIRATION = Duration.ofMinutes(5);
    private static final int OTP_LENGTH = 4;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom;

    public OtpService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.secureRandom = new SecureRandom();
    }

    public String generateOtpCode() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(secureRandom.nextInt(10));
        }
        return otp.toString();
    }

    public String generateMfaToken() {
        return UUID.randomUUID().toString();
    }

    public Mono<String> createOtpSession(String username, String otpCode) {
        String mfaToken = generateMfaToken();
        String otpKey = OTP_PREFIX + mfaToken;
        String tokenKey = MFA_TOKEN_PREFIX + mfaToken;
        String value = username + ":" + otpCode;

        LOG.debug("Création session OTP pour {} avec token {}", username, mfaToken);

        return Mono.zip(
            redisTemplate.opsForValue().set(otpKey, value, OTP_EXPIRATION),
            redisTemplate.opsForValue().set(tokenKey, username, OTP_EXPIRATION)
        ).thenReturn(mfaToken);
    }

    public Mono<String> validateOtp(String mfaToken, String otpCode) {
        String otpKey = OTP_PREFIX + mfaToken;

        return redisTemplate.opsForValue()
            .get(otpKey)
            .switchIfEmpty(Mono.defer(() -> {
                LOG.debug("Session OTP expirée ou invalide pour token {}", mfaToken);
                return Mono.error(new OtpExpiredException("Session OTP expirée ou invalide."));
            }))
            .flatMap(storedValue -> {
                int lastColon = storedValue.lastIndexOf(':');
                if (lastColon <= 0) {
                    return Mono.error(new OtpExpiredException("Format de session OTP invalide."));
                }
                String sessionData = storedValue.substring(0, lastColon);
                String storedOtp = storedValue.substring(lastColon + 1);
                if (storedOtp.equals(otpCode)) {
                    LOG.debug("OTP valide pour {}", sessionData);
                    return deleteOtpSession(mfaToken).thenReturn(sessionData);
                } else {
                    LOG.debug("OTP invalide pour {}", sessionData);
                    return Mono.error(new OtpInvalidException("Code OTP invalide."));
                }
            });
    }

    public Mono<String> getUsernameByMfaToken(String mfaToken) {
        return redisTemplate.opsForValue().get(MFA_TOKEN_PREFIX + mfaToken);
    }

    public Mono<Boolean> deleteOtpSession(String mfaToken) {
        return redisTemplate.delete(OTP_PREFIX + mfaToken, MFA_TOKEN_PREFIX + mfaToken)
            .map(count -> count > 0);
    }
}