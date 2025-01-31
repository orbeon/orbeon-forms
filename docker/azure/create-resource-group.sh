#!/bin/bash

if [[ $(az group exists --name "$RESOURCE_GROUP") == false ]]; then
  if ! az group create \
      --name "$RESOURCE_GROUP" \
      --location "$AZURE_LOCATION"; then
    echo "Failed to create resource group $RESOURCE_GROUP"
    exit 1
  fi
  echo "Resource group $RESOURCE_GROUP created"
else
  echo "Resource group $RESOURCE_GROUP already exists"
fi
