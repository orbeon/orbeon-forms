#!/bin/bash

# Commands used by the sub-scripts:
#  - az (Azure CLI)
#  - psql (PostgreSQL)
#  - jq (JSON manipulation)
#  - grep (for Entra ID app existence test)
#  - sed (for web.xml generation from template)
#  - curl (for client public IP retrieval)

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

# TODO
#source create-kubernetes-cluster.sh
