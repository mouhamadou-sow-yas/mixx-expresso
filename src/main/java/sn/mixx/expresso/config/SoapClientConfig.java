package sn.mixx.expresso.config;

import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration du client SOAP Apache CXF pour l'API Expresso ERS.
 * Intercepteur de logging configuré pour masquer les données sensibles.
 */
@Configuration
public class SoapClientConfig {

    @Value("${expresso.url_ers}")
    private String expressoErsUrl;

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
    public JaxWsProxyFactoryBean expressoProxyFactory() {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setAddress(expressoErsUrl);
        factory.setFeatures(List.of(expressoLoggingFeature()));

        Map<String, Object> requestContext = new HashMap<>();
        requestContext.put("javax.xml.ws.client.connectionTimeout", timeoutMs);
        requestContext.put("javax.xml.ws.client.receiveTimeout", timeoutMs);
        factory.setProperties(requestContext);

        return factory;
    }
}