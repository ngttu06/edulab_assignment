#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -f .env.remote ]]; then
    set -a
    # Trusted local configuration only. Never source a file from an untrusted party.
    source .env.remote
    set +a
fi
export SPRING_PROFILES_ACTIVE=remote
: "${EDULAB_DB_PASSWORD:?Configure EDULAB_DB_PASSWORD}"
: "${EDULAB_JWT_SECRET:?Configure EDULAB_JWT_SECRET}"
if [[ "${EDULAB_AI_ENABLED:-false}" == true ]]; then
    : "${EDULAB_AI_API_KEY:?Configure EDULAB_AI_API_KEY}"
fi
if [[ ! -f target/edulab-khtn8.war ]]; then
    printf '%s\n' 'Build the WAR with JDK 21 and mvn clean verify first.' >&2
    exit 1
fi
exec java -jar target/edulab-khtn8.war --spring.profiles.active=remote