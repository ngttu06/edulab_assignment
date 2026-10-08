#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [[ $# -gt 1 || ( $# -eq 1 && "$1" != --existing-mysql ) ]]; then
    printf 'Usage: %s [--existing-mysql]\n' "$0" >&2
    exit 1
fi
for tool in java mvn openssl; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        printf 'Missing required tool: %s\n' "$tool" >&2
        exit 1
    fi
done
if [[ -n "${EDULAB_DB_URL:-}" || -n "${EDULAB_DB_USERNAME:-}" || -n "${EDULAB_DB_PASSWORD:-}" ]]; then
    printf '%s\n' 'Unset EDULAB_DB_URL/USERNAME/PASSWORD before using this local-only script.' >&2
    exit 1
fi
export SPRING_PROFILES_ACTIVE=local
export EDULAB_DB_URL='jdbc:mysql://localhost:3306/edulab_assignment?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export EDULAB_DB_USERNAME=edulab
export EDULAB_DB_PASSWORD=edulab_local_password
if [[ -z "${EDULAB_JWT_SECRET:-}" ]]; then
    export EDULAB_JWT_SECRET="$(openssl rand -base64 48)"
    printf '%s\n' 'Generated an ephemeral JWT key; restarting this script invalidates previous logins.'
fi
if [[ "${1:-}" != --existing-mysql ]]; then
    if ! command -v docker >/dev/null 2>&1; then
        printf '%s\n' 'Missing Docker. Use --existing-mysql if the local database is already configured.' >&2
        exit 1
    fi
    docker compose -f "$PWD/compose.local.yml" up -d --wait mysql
fi
exec mvn spring-boot:run -Dspring-boot.run.profiles=local
