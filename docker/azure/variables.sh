#!/bin/bash

#####################################
# Azure account
#####################################

ACCOUNT_EMAIL='...' # TODO: add your Azure account email here

#####################################
# Resource group
#####################################

RESOURCE_GROUP='orbeon-forms-resource-group'
AZURE_LOCATION='westus'

#####################################
# Storage / configuration files
#####################################

STORAGE_ACCOUNT_PREFIX='orbeonformsstorage'
STORAGE_ACCOUNT_FILE='.storage_account_name'

# The storage account name must be unique across all of Azure since it forms part of the storage endpoint URL

if [[ ! -f "$STORAGE_ACCOUNT_FILE" ]]; then
  # Make the storage account name (statistically) unique and persist it to a local file
  echo "${STORAGE_ACCOUNT_PREFIX}$(printf "%06d" $((RANDOM % 1000000)))" > "$STORAGE_ACCOUNT_FILE"
fi

# Always use the same value during multiple script invocations; delete the file to generate a new storage account name
STORAGE_ACCOUNT=$(cat "$STORAGE_ACCOUNT_FILE")

STORAGE_SHARE_NAME='orbeon-forms-share'

#####################################
# Database
#####################################

DATABASE_SERVER_PREFIX='orbeonformsdb'
DATABASE_SERVER_FILE='.database_server_name'

# The database server name must be unique across all of Azure since it forms part of the database endpoint URL

if [[ ! -f "$DATABASE_SERVER_FILE" ]]; then
  # Make the database server name (statistically) unique and persist it to a local file
  echo "${DATABASE_SERVER_PREFIX}$(printf "%06d" $((RANDOM % 1000000)))" > "$DATABASE_SERVER_FILE"
fi

# Always use the same value during multiple script invocations; delete the file to generate a new database server name
DATABASE_SERVER=$(cat "$DATABASE_SERVER_FILE")

DATABASE_ADMIN_USERNAME='database_admin'
DATABASE_ADMIN_PASSWORD='CHANGEME0!'

DATABASE_FIREWALL_RULE_LOCAL='local-ip-allowed'
DATABASE_FIREWALL_RULE_ALL='all-ips-allowed'

DATABASE_NAME='orbeon'
DATABASE_USER='orbeon'
DATABASE_PASSWORD='orbeon'

#####################################
# Entra ID
#####################################

# 1st Entra ID test user
TEST_USER_EMAIL1='...' # TODO: add a test email here
TEST_USER_PASSWORD1='CHANGEME!'
TEST_USER_DISPLAY_NAME1='Test User 1'

# 2nd Entra ID test user
TEST_USER_EMAIL2='...' # TODO: add a test email here
TEST_USER_PASSWORD2='CHANGEME!'
TEST_USER_DISPLAY_NAME2='Test User 2'

# Orbeon user and admin group names
USER_GROUP='orbeon-user'
ADMIN_GROUP='orbeon-admin'

# Application name and URL
APP_NAME='Orbeon Forms'
APP_URL='https://localhost:8443/orbeon'

# Scope name/value
SCOPE_VALUE='groups.access'
