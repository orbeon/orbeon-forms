#!/bin/bash

if ! az postgres flexible-server show \
    --name "$DATABASE_SERVER" \
    --resource-group "$RESOURCE_GROUP" >/dev/null 2>&1; then
  if ! az postgres flexible-server create \
      --name "$DATABASE_SERVER" \
      --resource-group "$RESOURCE_GROUP" \
      --location "$AZURE_LOCATION" \
      --admin-user "$DATABASE_ADMIN_USERNAME" \
      --admin-password "$DATABASE_ADMIN_PASSWORD" \
      --sku-name standard_d2ds_v4 \
      --version 16 \
      --public-access None; then
    echo "Failed to create database server $DATABASE_SERVER"
    exit 1
  fi

  echo "Database server $DATABASE_SERVER created"
else
  echo "Database server $DATABASE_SERVER already exists"
fi

create_firewall_rule() {
  local rule_name="$1"
  local start_ip="$2"
  local end_ip="$3"

  if ! [[ $(az postgres flexible-server firewall-rule show \
      --rule-name "$rule_name" \
      --resource-group "$RESOURCE_GROUP" \
      --name "$DATABASE_SERVER" 2>/dev/null) ]]; then
    if ! az postgres flexible-server firewall-rule create \
        --rule-name "$rule_name" \
        --name "$DATABASE_SERVER" \
        --resource-group "$RESOURCE_GROUP" \
        --start-ip-address "$start_ip" \
        --end-ip-address "$end_ip"; then
      echo "Failed to create firewall rule $rule_name (range: $start_ip-$end_ip)"
      return 1
    fi
    echo "Firewall rule $rule_name created successfully (range: $start_ip-$end_ip)"
  else
    echo "Firewall rule $rule_name already exists (range: $start_ip-$end_ip)"
  fi

  return 0
}

DATABASE_PUBLIC_IP=$(curl -s 'https://api.ipify.org')
echo "Local IP: $DATABASE_PUBLIC_IP"

# Explicitly create a firewall rule to allow access from the local machine, so that we can easily disable it later
# (calling 'az postgres flexible-server create' without --public-access None but with --yes will also create that rule
# automatically)
create_firewall_rule "$DATABASE_FIREWALL_RULE_LOCAL" "$DATABASE_PUBLIC_IP" "$DATABASE_PUBLIC_IP"

if ! az postgres flexible-server db show \
    --resource-group "$RESOURCE_GROUP" \
    --server-name "$DATABASE_SERVER" \
    --database-name "$DATABASE_NAME" >/dev/null 2>&1; then
  if ! az postgres flexible-server db create \
     --resource-group "$RESOURCE_GROUP" \
     --server-name "$DATABASE_SERVER" \
     --database-name "$DATABASE_NAME"; then
     echo "Failed to create database $DATABASE_NAME"
     return 1
  fi
  echo "Database $DATABASE_NAME created successfully"
else
  echo "Database $DATABASE_NAME already exists"
fi

export PGPASSWORD="$DATABASE_ADMIN_PASSWORD"

create_db_user() {
  local username="$1"
  local password="$2"

  if ! [[ $(psql \
      --host "$DATABASE_SERVER.postgres.database.azure.com" \
      --username "$DATABASE_ADMIN_USERNAME" \
      --dbname "$DATABASE_NAME" \
      --tuples-only \
      --command "SELECT 1 FROM pg_user WHERE usename = '$username';") ]]; then
    if ! psql \
        --host "$DATABASE_SERVER.postgres.database.azure.com" \
        --username "$DATABASE_ADMIN_USERNAME" \
        --dbname "$DATABASE_NAME" \
        --command "CREATE USER \"$username\" WITH PASSWORD '$password';"; then
      echo "Failed to create user $username"
      return 1
    fi
    echo "User $username created successfully"
  else
    echo "User $username already exists"
  fi

  return 0
}

# TODO: which user do we actually need (the qualified one or both?)
#create_db_user "$DATABASE_USER" "$DATABASE_PASSWORD"
create_db_user "$DATABASE_USER@$DATABASE_SERVER" "$DATABASE_PASSWORD"

grant_privileges() {
 local username="$1"

  # GRANT is idempotent, so we don't need to check if the privileges were already granted
  if ! psql \
      --host "$DATABASE_SERVER.postgres.database.azure.com" \
      --username "$DATABASE_ADMIN_USERNAME" \
      --dbname "$DATABASE_NAME" \
      --command "GRANT ALL PRIVILEGES ON DATABASE $DATABASE_NAME TO \"$username\";" \
      --command "GRANT ALL PRIVILEGES ON SCHEMA public TO \"$username\";" \
      --command "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"$username\";" \
      --command "GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO \"$username\";" \
      --command "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO \"$username\";" \
      --command "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO \"$username\";"; then
    echo "Failed to grant privileges to user $username"
    return 1
  fi

  echo "Privileges granted successfully to user $username"
  return 0
}

#grant_privileges "$DATABASE_USER"
grant_privileges "$DATABASE_USER@$DATABASE_SERVER"

if ! [[ $(psql \
    --host "$DATABASE_SERVER.postgres.database.azure.com" \
    --username "$DATABASE_ADMIN_USERNAME" \
    --dbname "$DATABASE_NAME" \
    --tuples-only \
    --command "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' LIMIT 1;") ]]; then
  if ! psql \
      --host "$DATABASE_SERVER.postgres.database.azure.com" \
      --username "$DATABASE_ADMIN_USERNAME" \
      --dbname "$DATABASE_NAME" \
      --file ./postgresql-2024_1.sql; then
    echo "Failed to create Orbeon Forms tables"
    return 1
  fi
  echo "Orbeon Forms tables created successfully"
else
  echo "Database already contains Orbeon Forms tables"
fi

# TODO: do this in a secure way (using private endpoints?)
create_firewall_rule "$DATABASE_FIREWALL_RULE_ALL" "0.0.0.0" "255.255.255.255"

unset PGPASSWORD

delete_firewall_rule() {
  local rule_name="$1"

  if ! az postgres flexible-server firewall-rule delete \
      --rule-name "$rule_name" \
      --resource-group "$RESOURCE_GROUP" \
      --name "$DATABASE_SERVER" \
      --yes; then
    echo "Failed to delete firewall rule $rule_name"
    return 1
  fi

  echo "Firewall rule $rule_name deleted successfully"
  return 0
}

delete_firewall_rule "$DATABASE_FIREWALL_RULE_LOCAL"
