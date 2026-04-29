package sn.mixx.expresso.service.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.exception.AntiFraudException;
import sn.mixx.expresso.exception.InsufficientBalanceException;
import sn.mixx.expresso.repository.ClientRepository;
import sn.mixx.expresso.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AntiFraudService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;

    @Value("${elasticsearch.default.retry.count:5}")
    private int maxRetryCount;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final String DEDUP_PREFIX = "dedup:";

    public Mono<Void> checkAntiDuplicate(String clientReference) {
        String key = DEDUP_PREFIX + clientReference;
        return redisTemplate.opsForValue().get(key)
            .flatMap(existing -> {
                log.warn("[ANTI-FRAUD] Doublon détecté: clientReference={}", clientReference);
                return Mono.<Void>error(new AntiFraudException(
                    "Transaction en double détectée: " + clientReference, "DUPLICATE"));
            })
            .switchIfEmpty(Mono.defer(() ->
                redisTemplate.opsForValue()
                    .set(key, "1", Duration.ofSeconds(300))
                    .then()
            ));
    }

    public Mono<Void> checkDailyLimit(String msisdn, BigDecimal amount) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);

        return clientRepository.findByMsisdn(msisdn)
            .flatMap(client -> transactionRepository.sumDailyAmountByMsisdn(msisdn, startOfDay)
                .flatMap(dailySum -> {
                    BigDecimal newTotal = dailySum.add(amount);
                    if (newTotal.compareTo(client.getDailyLimit()) > 0) {
                        log.warn("[ANTI-FRAUD] Plafond journalier dépassé: msisdn={}, total={}, limit={}",
                            maskMsisdn(msisdn), newTotal, client.getDailyLimit());
                        return Mono.<Void>error(new AntiFraudException(
                            "Plafond journalier dépassé", "DAILY_LIMIT_EXCEEDED"));
                    }
                    return Mono.<Void>empty();
                })
            )
            .switchIfEmpty(Mono.empty());
    }

    public Mono<Void> checkMonthlyLimit(String msisdn, BigDecimal amount) {
        Instant startOfMonth = Instant.now().truncatedTo(ChronoUnit.DAYS)
            .minus(Instant.now().atZone(java.time.ZoneOffset.UTC).getDayOfMonth() - 1, ChronoUnit.DAYS);

        return clientRepository.findByMsisdn(msisdn)
            .flatMap(client -> transactionRepository.sumMonthlyAmountByMsisdn(msisdn, startOfMonth)
                .flatMap(monthlySum -> {
                    BigDecimal newTotal = monthlySum.add(amount);
                    if (newTotal.compareTo(client.getMonthlyLimit()) > 0) {
                        log.warn("[ANTI-FRAUD] Plafond mensuel dépassé: msisdn={}", maskMsisdn(msisdn));
                        return Mono.<Void>error(new AntiFraudException(
                            "Plafond mensuel dépassé", "MONTHLY_LIMIT_EXCEEDED"));
                    }
                    return Mono.<Void>empty();
                })
            )
            .switchIfEmpty(Mono.empty());
    }

    public Mono<Void> checkRateLimit(String msisdn) {
        String key = RATE_LIMIT_PREFIX + msisdn;
        return redisTemplate.opsForValue().increment(key)
            .flatMap(count -> {
                if (count == 1) {
                    return redisTemplate.expire(key, Duration.ofMinutes(1)).then();
                }
                if (count > 10) {
                    log.warn("[ANTI-FRAUD] Rate limit dépassé: msisdn={}, count={}", maskMsisdn(msisdn), count);
                    return Mono.<Void>error(new AntiFraudException(
                        "Trop de requêtes, veuillez réessayer dans 1 minute", "RATE_LIMIT_EXCEEDED"));
                }
                return Mono.<Void>empty();
            });
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 8) return "***";
        return msisdn.substring(0, 3) + "****" + msisdn.substring(msisdn.length() - 2);
    }
}