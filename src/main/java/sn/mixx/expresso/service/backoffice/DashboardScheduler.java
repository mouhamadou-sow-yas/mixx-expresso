package sn.mixx.expresso.service.backoffice;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardScheduler {

    private final DashboardService dashboardService;

    @PostConstruct
    public void warmUp() {
        log.info("[DASHBOARD] Initialisation du cache au démarrage");
        dashboardService.refreshDashboard()
            .doOnSuccess(data -> log.info("[DASHBOARD] Cache initialisé"))
            .doOnError(e -> log.error("[DASHBOARD] Erreur initialisation cache: {}", e.getMessage()))
            .subscribe();
    }

    @Scheduled(fixedDelayString = "${dashboard.cache.refresh-delay-ms:300000}")
    public void refreshCache() {
        log.debug("[DASHBOARD] Rafraîchissement périodique du cache");
        dashboardService.refreshDashboard()
            .doOnError(e -> log.error("[DASHBOARD] Erreur rafraîchissement: {}", e.getMessage()))
            .subscribe();
    }
}