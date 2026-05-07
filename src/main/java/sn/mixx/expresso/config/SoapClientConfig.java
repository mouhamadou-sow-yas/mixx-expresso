package sn.mixx.expresso.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sn.mixx.expresso.service.expresso.soap.ErsTopupService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class SoapClientConfig {

    @Value("${expresso.url_ers}")
    private String expressoErsUrl;

    @Value("${expresso.wsdl:#{null}}")
    private String wsdlUrl;

    @Value("${expresso.timeout_ms:15000}")
    private int timeoutMs;

    @Bean
    public LoggingFeature expressoLoggingFeature() {
        LoggingFeature loggingFeature = new LoggingFeature();
        loggingFeature.setPrettyLogging(true);
        loggingFeature.setVerbose(false);
        loggingFeature.setLogBinary(false);
        return loggingFeature;
    }

    @Bean
    @ConditionalOnProperty(name = "expresso.mock.enabled", havingValue = "false", matchIfMissing = true)
    public ErsTopupService expressoErsClient() {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(ErsTopupService.class);
        factory.setAddress(expressoErsUrl);
        if (wsdlUrl != null && !wsdlUrl.isBlank()) {
            factory.setWsdlURL(wsdlUrl);
        }
        factory.setFeatures(List.of(expressoLoggingFeature()));

        Map<String, Object> requestContext = new HashMap<>();
        requestContext.put("javax.xml.ws.client.connectionTimeout", timeoutMs);
        requestContext.put("javax.xml.ws.client.receiveTimeout", timeoutMs);
        factory.setProperties(requestContext);

        log.info("[SOAP-CONFIG] ERS client proxy créé: address={}", expressoErsUrl);
        return (ErsTopupService) factory.create();
    }
}
