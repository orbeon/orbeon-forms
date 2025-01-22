#!/bin/bash

source variables.sh

# Login if needed
source login.sh

# Create Entra ID configuration (test users, groups, permissions, etc.)
source create-entra-id-configuration.sh

# Create resource group (used by storage, database, and Azure Kubernetes Service (AKS) instances)
source create-resource-group.sh

# Create storage for configuration files, etc. and upload files
source create-storage.sh

# TODO
#source create-postgresql-database.sh

# TODO
#source create-kubernetes-cluster.sh
