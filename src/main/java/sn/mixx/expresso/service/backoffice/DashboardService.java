package sn.mixx.expresso.service.backoffice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.repository.TransactionRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TransactionRepository transactionRepository;

    private Map<String, Object> cachedDashboard;
    private Instant lastRefresh;

    public Mono<Map<String, Object>> getDashboard() {
        if (cachedDashboard != null && lastRefresh != null &&
            Instant.now().isBefore(lastRefresh.plus(5, ChronoUnit.MINUTES))) {
            return Mono.just(cachedDashboard);
        }
        return buildDashboard().doOnSuccess(data -> {
            cachedDashboard = data;
            lastRefresh = Instant.now();
        });
    }

    public Mono<Map<String, Object>> refreshDashboard() {
        return buildDashboard().doOnSuccess(data -> {
            cachedDashboard = data;
            lastRefresh = Instant.now();
            log.debug("[DASHBOARD] Cache rafraîchi");
        });
    }

    private Mono<Map<String, Object>> buildDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("generatedAt", Instant.now().toString());
        return Mono.just(dashboard);
    }
}