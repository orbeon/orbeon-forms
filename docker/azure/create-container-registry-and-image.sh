#!/bin/bash

if az acr show --name "$CONTAINER_REGISTRY" --resource-group "$RESOURCE_GROUP" >/dev/null 2>&1; then
  echo "Container registry $CONTAINER_REGISTRY already exists"
else
  if ! az acr create --name "$CONTAINER_REGISTRY" --resource-group "$RESOURCE_GROUP" --sku Basic; then
    echo "Failed to create container registry $CONTAINER_REGISTRY"
    exit 1
  fi
  echo "Container registry $CONTAINER_REGISTRY created"
fi

az acr login --name "$CONTAINER_REGISTRY"

# Retrieve the Azure Container Registry ID
CONTAINER_REGISTRY_ID=$(az acr show --name "$CONTAINER_REGISTRY" --resource-group "$RESOURCE_GROUP" --query id -o tsv)

# Create a Dockerfile to customize the Orbeon Forms Docker image
cat > Dockerfile << EOF
FROM orbeon/orbeon-forms:${ORBEON_FORMS_DOCKER_TAG}
# TODO: customize your image here
EOF

# Build the Docker image
docker build --platform "linux/amd64" -t "$CONTAINER_CUSTOM_IMAGE" .

# Tag the Docker image with the full Azure Container Registry URL
docker tag "$CONTAINER_CUSTOM_IMAGE" "$CONTAINER_REGISTRY.azurecr.io/$CONTAINER_CUSTOM_IMAGE"

# Push the Docker image to the Azure Container Registry
docker push "$CONTAINER_REGISTRY.azurecr.io/$CONTAINER_CUSTOM_IMAGE"
