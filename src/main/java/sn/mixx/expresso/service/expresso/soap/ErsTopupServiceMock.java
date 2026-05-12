package sn.mixx.expresso.service.expresso.soap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sn.mixx.expresso.config.ExpressoMockProperties;

/**
 * Implémentation mock de ErsTopupService — active quand expresso.mock.enabled=true.
 *
 * Variables d'environnement :
 *   EXPRESSO_MOCK_ENABLED=true
 *   EXPRESSO_MOCK_TOPUP_RESULT_CODE=0          (0 = succès)
 *   EXPRESSO_MOCK_STATUS_RESULT_CODE=0
 *   EXPRESSO_MOCK_STATUS=SUCCESS               (valeur du Status: dans resultDescription)
 *   EXPRESSO_MOCK_BALANCE=5000000
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "expresso.mock.enabled", havingValue = "true")
public class ErsTopupServiceMock implements ErsTopupService {

    private final ExpressoMockProperties props;

    @Override
    public RequestTopupResponse requestTopup(RequestTopupRequest request) {
        if (props.isForceTimeout()) {
            log.warn("[ERS-MOCK] forceTimeout=true — blocage du thread pour déclencher le timeout Reactor");
            simulateTimeout();
        }
        simulateDelay();

        String clientRef = request.getContext() != null ? request.getContext().getClientReference() : "UNKNOWN";
        String beneficiaryMsisdn = request.getTopupPrincipalId() != null
            ? maskMsisdn(request.getTopupPrincipalId().getId()) : "***";
        Object amount = request.getAmount() != null ? request.getAmount().getValue() : null;
        String productId = request.getProductId();
        int resultCode = props.getRequestTopup().getResultCode();
        String ersRef = props.getRequestTopup().getErsTransactionIdPrefix() + clientRef;

        log.warn("[ERS-MOCK] requestTopup — clientRef={}, msisdn={}, amount={}, product={} → resultCode={}, ersRef={}",
            clientRef, beneficiaryMsisdn, amount, productId, resultCode, ersRef);

        RequestTopupResponse resp = new RequestTopupResponse();
        resp.setResultCode(resultCode);
        resp.setResultDescription(props.getRequestTopup().getResultDescription());
        resp.setErsReference(ersRef);
        return resp;
    }

    @Override
    public GetTransactionStatusResponse getTransactionStatus(GetTransactionStatusRequest request) {
        simulateDelay();

        String clientRef = request.getContext() != null ? request.getContext().getClientReference() : "UNKNOWN";
        int resultCode = props.getGetTransactionStatus().getResultCode();
        String status = props.getGetTransactionStatus().getStatus();
        String ersRef = "ERS_MOCK_STATUS_" + clientRef;

        log.warn("[ERS-MOCK] getTransactionStatus — clientRef={} → resultCode={}, status={}",
            clientRef, resultCode, status);

        // Format réel : "ERSTransactionId= xxx;Status:SUCCESS"
        String resultDescription = "ERSTransactionId= " + ersRef + ";Status:" + status;

        GetTransactionStatusResponse resp = new GetTransactionStatusResponse();
        resp.setResultCode(resultCode);
        resp.setResultDescription(resultDescription);
        resp.setErsReference(ersRef);
        return resp;
    }

    @Override
    public RequestPrincipalInformationResponse requestPrincipalInformation(RequestPrincipalInformationRequest request) {
        simulateDelay();

        ExpressoMockProperties.RequestPrincipalInformation cfg = props.getRequestPrincipalInformation();

        log.warn("[ERS-MOCK] requestPrincipalInformation → resultCode={}, balance={} {}",
            cfg.getResultCode(), cfg.getBalance(), cfg.getCurrency());

        RequestPrincipalInformationResponse resp = new RequestPrincipalInformationResponse();
        resp.setResultCode(cfg.getResultCode());
        resp.setBalance(cfg.getBalance());
        resp.setCurrency(cfg.getCurrency());
        resp.setStatus(cfg.getStatus());
        return resp;
    }

    private void simulateDelay() {
        int delay = props.getDelayMs();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Bloque le thread jusqu'à ce que le timeout Reactor se déclenche. */
    private void simulateTimeout() {
        try {
            Thread.sleep(60_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 8) return "***";
        return msisdn.substring(0, 3) + "****" + msisdn.substring(msisdn.length() - 2);
    }
}