package sn.mixx.expresso.service.expresso;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.elastic.ExpressoCallDocument;
import sn.mixx.expresso.repository.ExpressoCallLogRepository;
import sn.mixx.expresso.domain.ExpressoCallLog;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpressoCallLogService {

    private final ReactiveElasticsearchOperations elasticsearchOperations;
    private final ExpressoCallLogRepository callLogRepository;

    public Mono<Void> indexCall(ExpressoCallDocument document) {
        return elasticsearchOperations.save(document)
            .doOnSuccess(saved -> log.debug("[ES] Appel Expresso indexé: id={}, operation={}", saved.getId(), saved.getOperation()))
            .doOnError(error -> log.error("[ES] Erreur indexation appel Expresso: {}", error.getMessage()))
            .onErrorResume(error -> Mono.empty())
            .then(persistSqlLog(document));
    }

    private Mono<Void> persistSqlLog(ExpressoCallDocument doc) {
        if (doc.getTxnId() == null) return Mono.empty();

        ExpressoCallLog callLog = ExpressoCallLog.builder()
            .correlationId(doc.getCorrelationId())
            .operation(doc.getOperation())
            .direction("OUTBOUND")
            .httpStatus(doc.getHttpStatus())
            .ersResultCode(doc.getErsResultCode())
            .durationMs(doc.getDurationMs())
            .attemptNumber(doc.getAttemptNumber() != null ? doc.getAttemptNumber() : 1)
            .esDocumentId(doc.getId())
            .createdAt(Instant.now())
            .build();

        return callLogRepository.save(callLog)
            .doOnError(e -> log.error("[EXPRESSO-LOG] Erreur persistance SQL: {}", e.getMessage()))
            .onErrorResume(e -> Mono.empty())
            .then();
    }
}