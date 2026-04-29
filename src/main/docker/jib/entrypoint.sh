#!/bin/bash

echo "mixx-expresso démarrage dans ${JHIPSTER_SLEEP:-0}s..."
sleep ${JHIPSTER_SLEEP:-0}

file_env() {
    local var="$1"
    local fileVar="${var}_FILE"
    local def="${2:-}"
    if [[ ${!var:-} && ${!fileVar:-} ]]; then
        echo >&2 "error: both $var and $fileVar are set (but are exclusive)"
        exit 1
    fi
    local val="$def"
    if [[ ${!var:-} ]]; then
        val="${!var}"
    elif [[ ${!fileVar:-} ]]; then
        val="$(< "${!fileVar}")"
    fi
    if [[ -n $val ]]; then
        export "$var"="$val"
    fi
    unset "$fileVar"
}

file_env 'R2DBC_PASSWORD'
file_env 'R2DBC_USERNAME'
file_env 'EXPRESSO_INITIATOR_PASSWORD'
file_env 'MPIN_ENCRYPTION_SECRET'
file_env 'ELASTIC_PASSWORD'
file_env 'REDIS_PASSWORD'
file_env 'JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET'

exec java ${JAVA_OPTS} \
  -noverify \
  -XX:+AlwaysPreTouch \
  -Djava.security.egd=file:/dev/./urandom \
  -cp /app/resources/:/app/classes/:/app/libs/* \
  "sn.mixx.expresso.MixxExpressoApp" "$@"