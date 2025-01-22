#!/bin/bash

# TODO: cleanup code below

exit 1

# Create PostgreSQL server
az postgres flexible-server create \
  --name mypostgresqlserver \
  --resource-group myResourceGroup \
  --location westus \
  --admin-user myuser \
  --admin-password mypassword \
  --sku-name standard_d2ds_v4 \
  --version 16

# TODO: try with 15???

# Do you want to enable access to client x.x.x.x (y/n): y

## What's my IP?
#wget -qO- http://ifconfig.me
#
## Allow access from my IP
#az postgres flexible-server firewall-rule create \
#  --name allow-azure \
#  --resource-group myResourceGroup \
#  --name mypostgresqlserver \
#  --start-ip-address 144.2.89.194 \
#  --end-ip-address 144.2.89.194

# Create database
az postgres flexible-server db create \
  --resource-group myResourceGroup \
  --server-name mypostgresqlserver \
  --database-name orbeon

# Add user
PGPASSWORD=mypassword psql \
  --host mypostgresqlserver.postgres.database.azure.com \
  --username myuser \
  --dbname orbeon \
  --command "CREATE USER orbeon WITH PASSWORD 'orbeon';" \
  --command "CREATE USER \"orbeon@mypostgresqlserver\" WITH PASSWORD 'orbeon';"

# Grant privileges
PGPASSWORD=mypassword psql \
  --host mypostgresqlserver.postgres.database.azure.com \
  --username myuser \
  --dbname orbeon \
  --command "GRANT ALL PRIVILEGES ON DATABASE orbeon TO orbeon;" \
  --command "GRANT ALL PRIVILEGES ON SCHEMA public TO orbeon;" \
  --command "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO orbeon;" \
  --command "GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO orbeon;"

# Grant privileges (@mypostgresqlserver)
PGPASSWORD=mypassword psql \
  --host mypostgresqlserver.postgres.database.azure.com \
  --username myuser \
  --dbname orbeon \
  --command "GRANT ALL PRIVILEGES ON DATABASE orbeon TO \"orbeon@mypostgresqlserver\";" \
  --command "GRANT ALL PRIVILEGES ON SCHEMA public TO \"orbeon@mypostgresqlserver\";" \
  --command "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO \"orbeon@mypostgresqlserver\";" \
  --command "GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO \"orbeon@mypostgresqlserver\";"

# Import SQL file (replace with your file path)
PGPASSWORD=mypassword psql \
  --host mypostgresqlserver.postgres.database.azure.com \
  --username myuser \
  --dbname orbeon \
  --file ./postgresql-2023_1.sql

# Add 0.0.0.0 - 255.255.255.255 to firewall rules
# => how to do this with Azure CLI?
# => how to do this in a secure way?