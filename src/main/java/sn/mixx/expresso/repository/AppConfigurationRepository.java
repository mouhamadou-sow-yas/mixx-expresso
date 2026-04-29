package sn.mixx.expresso.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import sn.mixx.expresso.domain.AppConfiguration;

@Repository
public interface AppConfigurationRepository extends ReactiveCrudRepository<AppConfiguration, Long> {
    Mono<AppConfiguration> findByConfigKey(String configKey);
}
