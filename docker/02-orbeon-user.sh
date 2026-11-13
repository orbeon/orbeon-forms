#!/bin/bash
# Create the database user Orbeon Forms connects with. This runs once, when the database is first initialized, after
# the SQL script which creates the tables. POSTGRES_USER remains the administration user, which is a superuser.
#
# - ORBEON_USER:          name of the user (default: orbeon)
# - ORBEON_PASSWORD_FILE: file containing the password of the user, or ORBEON_PASSWORD for the password itself
# - ORBEON_FLAT_VIEWS:    set to true if flat views are enabled, as the user must then be able to create views
set -eo pipefail

orbeon_user="${ORBEON_USER:-orbeon}"

if [ -n "${ORBEON_PASSWORD_FILE:-}" ]; then
  orbeon_password="$(< "$ORBEON_PASSWORD_FILE")"
  # Remove the copy made by docker-entrypoint-postgres.sh
  if [ "$ORBEON_PASSWORD_FILE" = /dev/shm/orbeon_password ]; then
    rm -f /dev/shm/orbeon_password
  fi
else
  orbeon_password="${ORBEON_PASSWORD:-}"
fi

if [ "$orbeon_user" = "$POSTGRES_USER" ]; then
  echo "WARNING: Orbeon Forms will connect to the database as '$POSTGRES_USER', which is a superuser."
  echo "WARNING: To avoid this, use different values for POSTGRES_USER and ORBEON_USER."
  exit 0
fi

if [ -z "$orbeon_password" ]; then
  echo "WARNING: User '$orbeon_user' not created, as neither ORBEON_PASSWORD_FILE nor ORBEON_PASSWORD is set."
  exit 0
fi

# The password is passed in the environment of this psql command only, so it doesn't show on its command line
ORBEON_USER_PASSWORD="$orbeon_password" \
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v orbeon_user="$orbeon_user" -v db="$POSTGRES_DB" -v admin="$POSTGRES_USER" <<'SQL'
\getenv orbeon_password ORBEON_USER_PASSWORD
CREATE ROLE :"orbeon_user" LOGIN PASSWORD :'orbeon_password';
GRANT CONNECT ON DATABASE :"db" TO :"orbeon_user";
GRANT USAGE ON SCHEMA public TO :"orbeon_user";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA public TO :"orbeon_user";
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA public TO :"orbeon_user";
-- Same rights on the tables and sequences the administration user creates later, when upgrading the schema
ALTER DEFAULT PRIVILEGES FOR ROLE :"admin" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES    TO :"orbeon_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"admin" IN SCHEMA public
    GRANT USAGE, SELECT                  ON SEQUENCES TO :"orbeon_user";
SQL

if [ "${ORBEON_FLAT_VIEWS:-false}" = "true" ]; then
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v orbeon_user="$orbeon_user" <<'SQL'
GRANT CREATE ON SCHEMA public TO :"orbeon_user";
SQL
fi

echo "User '$orbeon_user' created for Orbeon Forms."
