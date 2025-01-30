#!/bin/bash

ORBEON_FORMS_DOCKER_TAG="2024.1-pe-wildfly"

#####################################
# Azure account
#####################################

AZURE_ACCOUNT_EMAIL='...' # TODO: add your Azure account email here
AZURE_LOCATION='westus'

#####################################
# Resource group
#####################################

RESOURCE_GROUP='orbeon-forms-resource-group'

#####################################
# Storage
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
# Kubernetes
#####################################

K8S_CLUSTER_NAME='orbeon-forms-cluster'
K8S_STORAGE_SECRET='storage-secret'
K8S_PERSISTENCE_VOLUME='orbeon-forms-pv'
K8S_PERSISTENCE_VOLUME_CLAIM='orbeon-forms-pvc'
K8S_APP='orbeon-forms'
K8S_DEPLOYMENT='orbeon-forms-deployment'
K8S_SERVICE='orbeon-forms-service'

#####################################
# Entra ID
#####################################

# 1st Entra ID test user
ENTRA_ID_TEST_USER_EMAIL1='...' # TODO: add a test email here
ENTRA_ID_TEST_USER_PASSWORD1='CHANGEME!'
ENTRA_ID_TEST_USER_DISPLAY_NAME1='Test User 1'

# 2nd Entra ID test user
ENTRA_ID_TEST_USER_EMAIL2='...' # TODO: add a test email here
ENTRA_ID_TEST_USER_PASSWORD2='CHANGEME!'
ENTRA_ID_TEST_USER_DISPLAY_NAME2='Test User 2'

# Orbeon user and admin group names
ENTRA_ID_USER_GROUP='orbeon-user'
ENTRA_ID_ADMIN_GROUP='orbeon-admin'

# Application name and URL
ENTRA_ID_APP_NAME='Orbeon Forms'

# Scope name/value
ENTRA_ID_SCOPE_VALUE='groups.access'

#####################################
# Local deployment
#####################################

LOCAL_APP_URL='https://localhost:8443/orbeon'
