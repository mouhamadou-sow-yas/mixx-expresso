package sn.mixx.expresso.service.elastic;

import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import org.springframework.stereotype.Repository;
import sn.mixx.expresso.domain.elastic.PendingTransactionElastic;

@Repository
public interface ElasticPendingTransactionRepository
    extends ReactiveElasticsearchRepository<PendingTransactionElastic, String> {}