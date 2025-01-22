#!/bin/bash

if [[ $(az storage account show --name "$STORAGE_ACCOUNT" --resource-group "$RESOURCE_GROUP" 2>&1) == *"ResourceNotFound"* ]]; then
  az storage account create \
    --name "$STORAGE_ACCOUNT" \
    --resource-group "$RESOURCE_GROUP" \
    --location westus \
    --sku Standard_LRS
  echo "Storage account $STORAGE_ACCOUNT created"
else
  echo "Storage account $STORAGE_ACCOUNT already exists"
fi

if [[ $(az storage share exists \
    --name "$STORAGE_SHARE_NAME" \
    --account-name "$STORAGE_ACCOUNT" \
    --query exists) == "false" ]]; then
  az storage share create \
    --name "$STORAGE_SHARE_NAME" \
    --account-name "$STORAGE_ACCOUNT"
  echo "File share $STORAGE_SHARE_NAME created"
else
  echo "File share $STORAGE_SHARE_NAME already exists"
fi

upload_to_share() {
  local source="$1"
  local destination="$2"

  # We're not checking if the file already exists, so the content will always be updated

  if ! az storage file upload \
      --account-name "$STORAGE_ACCOUNT" \
      --share-name "$STORAGE_SHARE_NAME" \
      --source "$source" \
      --path "$destination"; then
    echo "Failed to upload $source to $destination"
    return 1
  fi

  echo "Successfully uploaded $source to $destination"
  return 0
}

# Upload all configuration files to the file share (license, properties, Form Builder permissions, OIDC, etc.)
upload_to_share "$HOME/.orbeon/license.xml" "license.xml"
upload_to_share "./properties-local.postgresql.xml" "properties-local.xml"
upload_to_share "./form-builder-permissions.xml" "form-builder-permissions.xml"
upload_to_share "./oidc.json" "oidc.json"
upload_to_share "./web.xml" "web.xml"
upload_to_share "./application.keystore" "application.keystore"

# TODO: upload PostgreSQL configuration

# Retrieve the storage account access key
STORAGE_ACCOUNT_SECRET_KEY=$(az storage account keys list \
                             --account-name "$STORAGE_ACCOUNT" \
                             --resource-group "$RESOURCE_GROUP" \
                             --query '[0].value' \
                             --output tsv)
