package sn.mixx.expresso.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.Notification;
import sn.mixx.expresso.domain.Transaction;
import sn.mixx.expresso.domain.enums.AuditAction;
import sn.mixx.expresso.repository.NotificationRepository;
import sn.mixx.expresso.service.audit.AuditService;
import sn.mixx.expresso.service.mfa.SmsService;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SmsService smsService;
    private final NotificationRepository notificationRepository;
    private final AuditService auditService;

    public Mono<Void> sendSuccessNotification(Transaction transaction) {
        String message = buildSuccessMessage(transaction);
        log.debug("[NOTIF] Envoi notification succès: msisdn={}", maskMsisdn(transaction.getClientMsisdn()));

        return smsService.sendSms(transaction.getClientMsisdn(), message)
            .flatMap(sent -> persistNotification(transaction, message, "AIRTIME".equals(transaction.getType()) ? "AIRTIME_SUCCESS" : "BUNDLE_SUCCESS", sent))
            .doOnError(e -> log.error("[NOTIF] Erreur notification succès: {}", e.getMessage()))
            .onErrorResume(e -> Mono.empty());
    }

    public Mono<Void> sendFailureNotification(Transaction transaction) {
        String message = buildFailureMessage(transaction);
        log.debug("[NOTIF] Envoi notification échec: msisdn={}", maskMsisdn(transaction.getClientMsisdn()));

        return smsService.sendSms(transaction.getClientMsisdn(), message)
            .flatMap(sent -> persistNotification(transaction, message, "AIRTIME".equals(transaction.getType()) ? "AIRTIME_FAILED" : "BUNDLE_FAILED", sent))
            .doOnError(e -> log.error("[NOTIF] Erreur notification échec: {}", e.getMessage()))
            .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> persistNotification(Transaction transaction, String message, String templateCode, Boolean sent) {
        Notification notification = Notification.builder()
            .transactionId(transaction.getId())
            .type("SMS")
            .recipientMsisdn(transaction.getClientMsisdn())
            .templateCode(templateCode)
            .message(message)
            .status(Boolean.TRUE.equals(sent) ? "SENT" : "FAILED")
            .attempts(1)
            .sentAt(Boolean.TRUE.equals(sent) ? Instant.now() : null)
            .build();

        auditService.log(
            Boolean.TRUE.equals(sent) ? AuditAction.NOTIFICATION_SENT : AuditAction.NOTIFICATION_FAILED,
            "TRANSACTION", transaction.getTxnId(), "SYSTEM",
            Map.of("template", templateCode, "msisdn", maskMsisdn(transaction.getClientMsisdn())),
            Map.of("status", notification.getStatus()),
            Boolean.TRUE.equals(sent) ? "SUCCESS" : "FAILURE",
            transaction.getCorrelationId());

        return notificationRepository.save(notification).then();
    }

    private String buildSuccessMessage(Transaction transaction) {
        if ("AIRTIME".equals(transaction.getType())) {
            return String.format("Votre achat de credit Expresso de %s FCFA a ete effectue avec succes. Ref: %s",
                transaction.getAmount(), transaction.getTxnId());
        }
        return String.format("Votre achat de forfait Expresso de %s FCFA a ete effectue avec succes. Ref: %s",
            transaction.getAmount(), transaction.getTxnId());
    }

    private String buildFailureMessage(Transaction transaction) {
        return String.format("Votre achat Expresso de %s FCFA n'a pas pu etre effectue. Veuillez contacter le service client. Ref: %s",
            transaction.getAmount(), transaction.getTxnId());
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 8) return "***";
        return msisdn.substring(0, 3) + "****" + msisdn.substring(msisdn.length() - 2);
    }
}