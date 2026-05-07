package sn.mixx.expresso.service.expresso.soap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sn.mixx.expresso.config.ExpressoMockProperties;

/**
 * Implémentation mock de ErsTopupService — active quand expresso.mock.enabled=true.
 * Toutes les valeurs de retour sont configurables via variables d'environnement.
 *
 * Variables d'environnement disponibles :
 *   EXPRESSO_MOCK_ENABLED=true
 *   EXPRESSO_MOCK_DELAY_MS=200
 *   EXPRESSO_MOCK_TOPUP_RESULT_CODE=0
 *   EXPRESSO_MOCK_TOPUP_DESCRIPTION=Mock Success
 *   EXPRESSO_MOCK_TOPUP_ERS_TXN_ID_PREFIX=ERS_MOCK_
 *   EXPRESSO_MOCK_STATUS_RESULT_CODE=0
 *   EXPRESSO_MOCK_STATUS=COMPLETED
 *   EXPRESSO_MOCK_BALANCE_RESULT_CODE=0
 *   EXPRESSO_MOCK_BALANCE=5000000
 *   EXPRESSO_MOCK_CURRENCY=XOF
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "expresso.mock.enabled", havingValue = "true")
public class ErsTopupServiceMock implements ErsTopupService {

    private final ExpressoMockProperties props;

    @Override
    public RequestTopupResponse requestTopup(RequestTopupRequest request) {
        simulateDelay();

        int resultCode = props.getRequestTopup().getResultCode();
        String ersTransactionId = props.getRequestTopup().getErsTransactionIdPrefix()
            + (request.getClientReference() != null ? request.getClientReference() : "UNKNOWN");

        log.warn("[ERS-MOCK] requestTopup — clientRef={}, msisdn={}, amount={}, product={} → resultCode={}, ersId={}",
            request.getClientReference(),
            maskMsisdn(request.getTopupPrincipalId() != null ? request.getTopupPrincipalId().getId() : null),
            request.getAmount() != null ? request.getAmount().getValue() : null,
            request.getProductId(),
            resultCode,
            ersTransactionId);

        RequestTopupResponse resp = new RequestTopupResponse();
        resp.setResultCode(resultCode);
        resp.setResultDescription(props.getRequestTopup().getResultDescription());
        resp.setErsTransactionId(ersTransactionId);
        return resp;
    }

    @Override
    public GetTransactionStatusResponse getTransactionStatus(GetTransactionStatusRequest request) {
        simulateDelay();

        int resultCode = props.getGetTransactionStatus().getResultCode();
        String status = props.getGetTransactionStatus().getStatus();

        log.warn("[ERS-MOCK] getTransactionStatus — clientRef={} → resultCode={}, status={}",
            request.getClientReference(), resultCode, status);

        GetTransactionStatusResponse resp = new GetTransactionStatusResponse();
        resp.setResultCode(resultCode);
        resp.setStatus(status);
        resp.setErsTransactionId("ERS_MOCK_STATUS_" + request.getClientReference());
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

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 8) return "***";
        return msisdn.substring(0, 3) + "****" + msisdn.substring(msisdn.length() - 2);
    }
}
