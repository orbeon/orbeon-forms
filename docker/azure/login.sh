#!/bin/bash

login() {
  local microsoft_graph_scope="https://graph.microsoft.com/.default"

  echo "Login using scope $microsoft_graph_scope"
  if ! az login --scope "$microsoft_graph_scope"; then
    return 1
  fi

  return 0
}

check_azure_login() {
  local EXPECTED_USER="$1"

  # Check if we're logged in as the correct user
  local account_output
  account_output=$(az account show --query user.name -o tsv 2>&1)
  if [[ $? -ne 0 || "$account_output" != "$EXPECTED_USER" ]]; then
    echo "Need to login as $EXPECTED_USER..."
    if ! login; then
      return 1
    fi
  fi

  # Check Microsoft Graph API scope
  if ! az rest --method get --url 'https://graph.microsoft.com/v1.0/domains' >/dev/null 2>&1; then
    echo "Need to refresh token with Microsoft Graph API scope..."
    if ! login; then
      return 1
    fi
  fi

  return 0
}

register_provider() {
  local namespace="$1"

  local registration_state
  registration_state=$(az provider show --namespace "$namespace" --query registrationState -o tsv)

  if [[ "$registration_state" != "Registered" ]]; then
   echo "Registering $namespace provider..."
   if ! az provider register --namespace "$namespace"; then
     echo "Failed to start registration for $namespace provider"
     return 1
   fi

   echo "Waiting for $namespace provider to be registered..."
   while [[ $(az provider show --namespace "$namespace" --query registrationState -o tsv) == "Registering" ]]; do
     echo -n "."
     sleep 10
   done
   echo

   registration_state=$(az provider show --namespace "$namespace" --query registrationState -o tsv)
   if [[ "$registration_state" != "Registered" ]]; then
     echo "Error: $namespace provider is in state $registration_state"
     return 1
   fi

   echo "$namespace provider is now registered"
  else
   echo "$namespace provider is already registered"
  fi

  return 0
}

if ! check_azure_login "$AZURE_ACCOUNT_EMAIL"; then
  echo "Login failed"
  return
fi

# Register providers to be able to use Azure Storage, Database for PostgreSQL, Container Registry, and Managed
# Kubernetes Service (AKS)
register_provider "Microsoft.Compute"
register_provider "Microsoft.ContainerRegistry"
register_provider "Microsoft.ContainerService"
register_provider "Microsoft.DBforPostgreSQL"
register_provider "Microsoft.Storage"
