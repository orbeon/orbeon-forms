#!/bin/bash

# Commands used by the sub-scripts:
#  - az (Azure CLI)
#  - psql (PostgreSQL)
#  - kubectl (Kubernetes)
#  - jq (JSON manipulation)
#  - base64 (K8S passwords encoding)
#  - cat, curl, cut, echo, grep, sed

source variables.sh

# Login if needed
source login.sh

# Generate self-signed certificate
source generate-self-signed-certificate.sh

# Create Entra ID configuration (test users, groups, permissions, etc.)
source create-entra-id-configuration.sh

# Generate configuration files (OIDC, Form Builder permissions, WildFly, etc.)
source generate-configuration-files.sh

# Create resource group (used by storage, database, and Azure Kubernetes Service (AKS) instances)
source create-resource-group.sh

# Create storage for configuration files, etc. and upload files
source create-storage.sh

# Create PostgreSQL database with Orbeon Forms model
source create-postgresql-database.sh

if [ "$CONTAINER_CUSTOM_IMAGE_ENABLED" = 'true' ]; then
  # Create container registry and custom container image
  source create-container-registry-and-image.sh
fi

# Deploy Orbeon Forms as a simple Kubernetes cluster
source create-kubernetes-cluster.sh
