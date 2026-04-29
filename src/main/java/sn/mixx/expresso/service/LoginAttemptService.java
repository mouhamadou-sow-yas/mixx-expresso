package sn.mixx.expresso.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.User;
import sn.mixx.expresso.repository.UserRepository;
import sn.mixx.expresso.security.AccountLockedException;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 30;
    private static final String PREFIX = "login_attempts:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    public Mono<Void> checkAccountLocked(String login) {
        return userRepository.findOneByLogin(login.toLowerCase())
            .flatMap(user -> {
                if (user.getAccountLockedUntil() != null && Instant.now().isBefore(user.getAccountLockedUntil())) {
                    long minutesLeft = Duration.between(Instant.now(), user.getAccountLockedUntil()).toMinutes() + 1;
                    return Mono.error(new AccountLockedException(
                        "Compte verrouillé pour " + minutesLeft + " minutes.", (int) minutesLeft));
                }
                return Mono.empty();
            })
            .then();
    }

    public Mono<User> resetFailedAttemptsAndGet(String login) {
        return userRepository.findOneByLogin(login.toLowerCase())
            .flatMap(user -> {
                user.setFailedLoginAttempts(0);
                user.setAccountLockedUntil(null);
                user.setLastLoginAt(Instant.now());
                return userRepository.save(user);
            });
    }

    public Mono<Void> registerFailedAttempt(String login) {
        return userRepository.findOneByLogin(login.toLowerCase())
            .flatMap(user -> {
                int attempts = user.getFailedLoginAttempts() + 1;
                user.setFailedLoginAttempts(attempts);
                if (attempts >= MAX_ATTEMPTS) {
                    user.setAccountLockedUntil(Instant.now().plus(Duration.ofMinutes(LOCK_MINUTES)));
                    log.warn("[AUTH] Compte verrouillé: login={}", login);
                }
                return userRepository.save(user);
            })
            .then();
    }

    public Mono<Integer> getRemainingAttempts(String login) {
        return userRepository.findOneByLogin(login.toLowerCase())
            .map(user -> Math.max(0, MAX_ATTEMPTS - user.getFailedLoginAttempts()))
            .defaultIfEmpty(MAX_ATTEMPTS);
    }
}