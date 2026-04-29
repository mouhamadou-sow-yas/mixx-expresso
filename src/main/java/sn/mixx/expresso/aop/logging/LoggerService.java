package sn.mixx.expresso.aop.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class LoggerService {

    private static final Logger LOG = LoggerFactory.getLogger(LoggerService.class);

    public Mono<Void> logInfoReactive(String message, Object... args) {
        return Mono.fromRunnable(() -> LOG.info(message, args));
    }

    public void logError(String message, Throwable error, Object... args) {
        LOG.error(message, args, error);
    }

    public void logInfo(String message, Object... args) {
        LOG.info(message, args);
    }

    public void logDebug(String message, Object... args) {
        LOG.debug(message, args);
    }

    public void logWarn(String message, Object... args) {
        LOG.warn(message, args);
    }

    public void logCallback(String action, String txnId, String status, Object data) {
        LOG.info("[CALLBACK] action={}, txnId={}, status={}, data={}", action, txnId, status, data);
    }
}