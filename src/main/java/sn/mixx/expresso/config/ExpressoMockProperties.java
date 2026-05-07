package sn.mixx.expresso.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "expresso.mock")
@Data
public class ExpressoMockProperties {

    private boolean enabled = false;

    /** Délai simulé en ms pour reproduire une latence réaliste */
    private int delayMs = 200;

    private final RequestTopup requestTopup = new RequestTopup();
    private final GetTransactionStatus getTransactionStatus = new GetTransactionStatus();
    private final RequestPrincipalInformation requestPrincipalInformation = new RequestPrincipalInformation();

    @Data
    public static class RequestTopup {
        /** 0 = succès, 10 = transitoire (retry), 20 = MSISDN invalide, 30 = produit indispo */
        private int resultCode = 0;
        private String resultDescription = "Mock Success";
        /** Préfixe du ersTransactionId — suffixé par le clientReference */
        private String ersTransactionIdPrefix = "ERS_MOCK_";
    }

    @Data
    public static class GetTransactionStatus {
        private int resultCode = 0;
        /** COMPLETED | PENDING | FAILED | UNKNOWN */
        private String status = "COMPLETED";
    }

    @Data
    public static class RequestPrincipalInformation {
        private int resultCode = 0;
        private BigDecimal balance = new BigDecimal("5000000");
        private String currency = "XOF";
        private String status = "ACTIVE";
    }
}
