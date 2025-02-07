#!/bin/bash

ORBEON_FORMS_DOCKER_TAG="2024.1-pe-wildfly"

#################################################
# Function to generate unique names
#################################################

# The following names must be unique across all of Azure because they are used in endpoint URLs:
#  - storage account
#  - database server
#  - container registry

unique_name() {
  local prefix="$1"
  local file="$2"

  if [[ ! -f "$file" ]]; then
    # Make the name (statistically) unique and persist it to a local file
    echo "${prefix}$(printf "%06d" $((RANDOM % 1000000)))" > "$file"
  fi

  # Always use the same value during multiple script invocations; delete the file to generate a new name
  cat "$file"
}

#################################################
# Azure account
#################################################

AZURE_ACCOUNT_EMAIL='...' # TODO: add your Azure account email here
AZURE_LOCATION='westus'

#################################################
# Resource group
#################################################

RESOURCE_GROUP='orbeon-forms-resource-group'

#################################################
# Storage
#################################################

STORAGE_ACCOUNT=$(unique_name 'orbeonformsstorage' '.storage_account_name')

STORAGE_SHARE='orbeon-forms-share'

#################################################
# Database
#################################################

DATABASE_SERVER=$(unique_name 'orbeonformsdb' '.database_server_name')

DATABASE_ADMIN_USERNAME='database_admin'
DATABASE_ADMIN_PASSWORD='CHANGEME0!'

DATABASE_FIREWALL_RULE_LOCAL='local-ip-allowed'
DATABASE_FIREWALL_RULE_ALL='all-ips-allowed'

DATABASE_NAME='orbeon'
DATABASE_USER='orbeon'
DATABASE_PASSWORD='orbeon'

#################################################
# Container registry (for custom images)
#################################################

CONTAINER_REGISTRY=$(unique_name 'orbeonformsreg' '.container_registry_name')

CONTAINER_CUSTOM_IMAGE_ENABLED='false'
CONTAINER_CUSTOM_IMAGE="orbeon-forms-custom:$ORBEON_FORMS_DOCKER_TAG"

#################################################
# Kubernetes
#################################################

K8S_CLUSTER='orbeon-forms-cluster'
K8S_STORAGE_SECRET='storage-secret'
K8S_PERSISTENCE_VOLUME='orbeon-forms-pv'
K8S_PERSISTENCE_VOLUME_CLAIM='orbeon-forms-pvc'
K8S_APP='orbeon-forms'
K8S_DEPLOYMENT='orbeon-forms-deployment'
K8S_SERVICE='orbeon-forms-service'

#################################################
# Network
#################################################

NETWORK_POSTGRES_DNS_ZONE='private.postgres.database.azure.com'
NETWORK_PRIVATE_ENDPOINT='postgres-pe'
NETWORK_PRIVATE_ENDPOINT_CONNECTION='postgres-connection'

#################################################
# Entra ID
#################################################

# 1st Entra ID test user
ENTRA_ID_TEST_USER_UPN_PREFIX1='testuser1'
ENTRA_ID_TEST_USER_PASSWORD1='CHANGEME0!'
ENTRA_ID_TEST_USER_DISPLAY_NAME1='Test User 1'
ENTRA_ID_TEST_USER_EMAIL1='...' # TODO: add a test email here

# 2nd Entra ID test user
ENTRA_ID_TEST_USER_UPN_PREFIX2='testuser2'
ENTRA_ID_TEST_USER_PASSWORD2='CHANGEME0!'
ENTRA_ID_TEST_USER_DISPLAY_NAME2='Test User 2'
ENTRA_ID_TEST_USER_EMAIL2='...' # TODO: add a test email here

# Orbeon user and admin group names
ENTRA_ID_USER_GROUP='orbeon-user'
ENTRA_ID_ADMIN_GROUP='orbeon-admin'

# Application name
ENTRA_ID_APP='Orbeon Forms'

# Scope name/value
ENTRA_ID_SCOPE_VALUE='groups.access'

#################################################
# Local deployment
#################################################

LOCAL_APP_URL='https://localhost:8443/orbeon'
