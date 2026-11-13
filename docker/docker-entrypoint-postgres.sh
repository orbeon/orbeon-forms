#!/bin/bash
# Make the password file of the Orbeon Forms user available to 02-orbeon-user.sh, then run the entrypoint of the
# PostgreSQL image. That script runs as the PostgreSQL server user, which often can't read the password file. So we
# copy the file here, as root, to an in-memory file only that user can read. The script removes the copy after use.
# The password is never put in an environment variable.
set -e

if [ -n "${ORBEON_PASSWORD_FILE:-}" ]; then
  if [ -n "${ORBEON_PASSWORD:-}" ]; then
    echo "ERROR: ORBEON_PASSWORD and ORBEON_PASSWORD_FILE are both set, use only one of them." >&2
    exit 1
  fi
  if [ ! -r "$ORBEON_PASSWORD_FILE" ]; then
    echo "ERROR: Cannot read the file set by ORBEON_PASSWORD_FILE: $ORBEON_PASSWORD_FILE" >&2
    exit 1
  fi
  # Initialization scripts only run when the database doesn't exist yet
  if [ "$(id -u)" = '0' ] && [ ! -s "$PGDATA/PG_VERSION" ]; then
    install -o postgres -g postgres -m 600 "$ORBEON_PASSWORD_FILE" /dev/shm/orbeon_password
    export ORBEON_PASSWORD_FILE=/dev/shm/orbeon_password
  fi
fi

exec docker-entrypoint.sh "$@"
