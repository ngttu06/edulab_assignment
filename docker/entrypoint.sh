#!/bin/sh
set -eu

# Runtime-only secrets. Do not print environment values or enable shell tracing.
: "${EDULAB_DB_URL:?Configure EDULAB_DB_URL}"
: "${EDULAB_DB_USERNAME:?Configure EDULAB_DB_USERNAME}"
: "${EDULAB_DB_PASSWORD:?Configure EDULAB_DB_PASSWORD}"

mode="${1:-app}"
if [ "$#" -gt 0 ]; then shift; fi
case "$mode" in
    app)
        : "${EDULAB_JWT_SECRET:?Configure EDULAB_JWT_SECRET}"
        exec java -jar /opt/edulab/app.war "$@"
        ;;
    migrate)
        exec java -cp '/opt/edulab/migration/WEB-INF/classes:/opt/edulab/migration/WEB-INF/lib/*' \
            vn.edu.khtn8.deployment.DatabaseMigration "$@"
        ;;
    *)
        printf '%s\n' 'Supported commands: app, migrate' >&2
        exit 2
        ;;
esac