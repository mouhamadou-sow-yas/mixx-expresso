#!/bin/sh
set -e
# ============================================================
# Script de creation de templates pour les logs mixxpay
# puis demarrage de l'application
# ============================================================

info()    { printf '[INFO]    %s\n' "$*"; }
success() { printf '[OK]      %s\n' "$*"; }
warn()    { printf '[WARN]    %s\n' "$*"; }

# ---- Index template Elasticsearch ----
info "Création de l'index template Elasticsearch..."
if curl  -sf -X PUT "http://elasticsearch:9200/_index_template/mixxexpresso-logs-template" \
  -H 'Content-Type: application/json' -d'
{
  "index_patterns": ["mixxexpresso-logs-*"],
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
        "apm_trace_id":    { "type": "keyword" },
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
  success "Index template créé"
else
  warn "Impossible de créer l'index template (non bloquant)"
fi