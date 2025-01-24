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

# Create Entra ID configuration (test users, groups, permissions, etc.)
source create-entra-id-configuration.sh

# Create resource group (used by storage, database, and Azure Kubernetes Service (AKS) instances)
source create-resource-group.sh

# Create storage for configuration files, etc. and upload files
source create-storage.sh

# Create PostgreSQL database with Orbeon Forms model
source create-postgresql-database.sh

# Deploy Orbeon Forms as a simple Kubernetes cluster
source create-kubernetes-cluster.sh
