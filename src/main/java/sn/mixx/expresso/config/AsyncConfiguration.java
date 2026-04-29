package sn.mixx.expresso.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncConfiguration.class);

    @Bean(name = "elasticSearchScheduler")
    public Scheduler elasticSearchScheduler() {
        LOG.debug("Creating Elasticsearch boundedElastic Scheduler");
        return Schedulers.boundedElastic();
    }
}