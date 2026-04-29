#!/bin/bash
# Script d'initialisation Elasticsearch en mode single-node
# Force number_of_replicas=0 pour tous les index système Kibana
# Exécuté après le démarrage d'Elasticsearch

ES_URL="${ES_URL:-http://localhost:9200}"
MAX_RETRIES=30
RETRY_INTERVAL=5

echo "[ES-INIT] Attente démarrage Elasticsearch..."
for i in $(seq 1 $MAX_RETRIES); do
    if curl -sf "${ES_URL}/_cluster/health?wait_for_status=yellow&timeout=5s" > /dev/null 2>&1; then
        echo "[ES-INIT] Elasticsearch disponible"
        break
    fi
    echo "[ES-INIT] Tentative $i/$MAX_RETRIES..."
    sleep $RETRY_INTERVAL
done

echo "[ES-INIT] Application index template single-node (replicas=0)..."

curl -sf -X PUT "${ES_URL}/_template/single_node_replicas" \
  -H "Content-Type: application/json" \
  -d '{
    "index_patterns": ["*"],
    "settings": {
      "number_of_replicas": 0
    }
  }' && echo "[ES-INIT] Template replicas=0 appliqué"

# Appliquer sur les index Kibana existants
for index in .kibana .kibana_task_manager .kibana_analytics .kibana_security_solution .kibana_alerting_cases .kibana_ingest .kibana_usage_counters; do
    curl -sf -X PUT "${ES_URL}/${index}*/_settings" \
      -H "Content-Type: application/json" \
      -d '{"index": {"number_of_replicas": 0}}' > /dev/null 2>&1 \
    && echo "[ES-INIT] replicas=0 sur $index" || true
done

echo "[ES-INIT] Initialisation terminée"