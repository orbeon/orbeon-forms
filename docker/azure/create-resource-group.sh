#!/bin/bash

if [[ $(az group exists --name "$RESOURCE_GROUP") == false ]]; then
  az group create \
    --name "$RESOURCE_GROUP" \
    --location "$AZURE_LOCATION"
  echo "Resource group $RESOURCE_GROUP created"
else
  echo "Resource group $RESOURCE_GROUP already exists"
fi
