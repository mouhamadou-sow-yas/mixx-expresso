package sn.mixx.expresso.management;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class SecurityMetersService {

    private static final String INVALID_TOKENS_METRIC_NAME = "security.authentication.invalid-tokens";
    private static final String CAUSE_TAG = "cause";
    private static final String EXPIRED_TAG = "expired";
    private static final String INVALID_SIGNATURE_TAG = "invalid-signature";
    private static final String MALFORMED_TAG = "malformed";

    private final Counter tokenExpiredCounter;
    private final Counter tokenInvalidSignatureCounter;
    private final Counter tokenMalformedCounter;

    public SecurityMetersService(MeterRegistry registry) {
        this.tokenExpiredCounter = Counter.builder(INVALID_TOKENS_METRIC_NAME)
            .tag(CAUSE_TAG, EXPIRED_TAG)
            .register(registry);
        this.tokenInvalidSignatureCounter = Counter.builder(INVALID_TOKENS_METRIC_NAME)
            .tag(CAUSE_TAG, INVALID_SIGNATURE_TAG)
            .register(registry);
        this.tokenMalformedCounter = Counter.builder(INVALID_TOKENS_METRIC_NAME)
            .tag(CAUSE_TAG, MALFORMED_TAG)
            .register(registry);
    }

    public void trackTokenExpired() { tokenExpiredCounter.increment(); }
    public void trackTokenInvalidSignature() { tokenInvalidSignatureCounter.increment(); }
    public void trackTokenMalformed() { tokenMalformedCounter.increment(); }
}