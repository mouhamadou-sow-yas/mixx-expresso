package sn.mixx.expresso.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import sn.mixx.expresso.domain.RetryConfig;

@Repository
public interface RetryConfigRepository extends ReactiveCrudRepository<RetryConfig, Long> {}
