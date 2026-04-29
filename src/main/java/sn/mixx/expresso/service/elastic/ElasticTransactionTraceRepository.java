package sn.mixx.expresso.service.elastic;

import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import org.springframework.stereotype.Repository;
import sn.mixx.expresso.domain.elastic.TransactionTraceElastic;

@Repository
public interface ElasticTransactionTraceRepository
    extends ReactiveElasticsearchRepository<TransactionTraceElastic, String> {}