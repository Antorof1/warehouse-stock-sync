#!/bin/bash
set -euo pipefail

create_service_database() {
  local service="$1"
  local prefix db user password

  prefix="$(printf '%s' "$service" | tr '[:lower:]' '[:upper:]' | tr '-' '_')"

  for var in "${prefix}_DB_NAME" "${prefix}_DB_USER" "${prefix}_DB_PASSWORD"; do
    if [ -z "${!var:-}" ]; then
      echo "init: $var is not set, cannot create database for service '$service'" >&2
      exit 1
    fi
  done

  db="$(printenv "${prefix}_DB_NAME")"
  user="$(printenv "${prefix}_DB_USER")"
  password="$(printenv "${prefix}_DB_PASSWORD")"

  echo "init: creating database '$db' owned by role '$user' for service '$service'"

  psql -v ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v db="$db" -v user="$user" -v password="$password" <<'EOSQL'
CREATE ROLE :"user" LOGIN PASSWORD :'password';
CREATE DATABASE :"db" OWNER :"user";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
EOSQL
}

for service in ${SERVICE_DATABASES:-}; do
  create_service_database "$service"
done
