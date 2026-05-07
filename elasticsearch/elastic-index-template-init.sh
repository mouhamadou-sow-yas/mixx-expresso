#!/bin/sh
set -e
# ============================================================
# Initialisation des index templates Elasticsearch
# Supports optionally authenticated ES (ES_USERNAME / ES_PASSWORD)
# ============================================================

info()    { printf '[INFO]    %s\n' "$*"; }
success() { printf '[OK]      %s\n' "$*"; }
warn()    { printf '[WARN]    %s\n' "$*"; }

ES_URL="${ES_URL:-http://elasticsearch:9200}"

if [ -n "$ES_USERNAME" ] && [ -n "$ES_PASSWORD" ]; then
  AUTH="-u ${ES_USERNAME}:${ES_PASSWORD}"
else
  AUTH=""
fi

curl_es() {
  # shellcheck disable=SC2086
  curl -sf $AUTH "$@"
}

# ---- Attendre que l'ES soit vraiment prêt ----
info "Vérification de la disponibilité d'Elasticsearch..."
for i in $(seq 1 20); do
  if curl_es "${ES_URL}/_cluster/health?wait_for_status=yellow&timeout=5s" > /dev/null 2>&1; then
    success "Elasticsearch disponible"
    break
  fi
  warn "Tentative $i/20 — ES pas encore prêt, attente 5s..."
  sleep 5
done

# ---- Template logs (dev + prod) ----
info "Création du template d'index pour les logs applicatifs..."
if curl_es -X PUT "${ES_URL}/_index_template/mixx-expresso-logs-template" \
  -H 'Content-Type: application/json' -d'{
  "index_patterns": ["mixxexpresso-logs-*", "mixx-expresso-logs-*"],
  "priority": 200,
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    },
    "mappings": {
      "properties": {
        "@timestamp":      { "type": "date" },
        "Timestamp":       { "type": "date" },
        "Level":           { "type": "keyword" },
        "MessageTemplate": { "type": "text" },
        "trace.id":        { "type": "keyword" },
        "transaction.id":  { "type": "keyword" },
        "environment":     { "type": "keyword" },
        "service":         { "type": "keyword" },
        "Properties": {
          "properties": {
            "RequestId": { "type": "keyword" },
            "Body":      { "type": "text" }
          }
        }
      }
    }
  }
}'; then
  success "Template logs créé"
else
  warn "Template logs non créé (non bloquant)"
fi

# ---- Template traces Jaeger ----
info "Création du template d'index pour les traces Jaeger..."
if curl_es -X PUT "${ES_URL}/_index_template/jaeger-traces-template" \
  -H 'Content-Type: application/json' -d'{
  "index_patterns": ["jaeger-span-*", "jaeger-service-*"],
  "priority": 200,
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    }
  }
}'; then
  success "Template Jaeger créé"
else
  warn "Template Jaeger non créé (non bloquant)"
fi

success "Initialisation Elasticsearch terminée"