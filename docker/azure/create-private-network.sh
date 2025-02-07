#!/bin/bash

# Create private DNS zone
az network private-dns zone create \
  --resource-group "$RESOURCE_GROUP" \
  --name "$NETWORK_POSTGRES_DNS_ZONE"

az network private-dns zone wait \
  --resource-group "$RESOURCE_GROUP" \
  --name "$NETWORK_POSTGRES_DNS_ZONE" \
  --created

# Get VNET name
VNET_NAME=$(az network vnet list \
            --resource-group "$K8S_NODE_RESOURCE_GROUP" \
            --query '[0].name' \
            --output tsv)

# Get VNET ID
VNET_ID=$(az network vnet show \
          --resource-group "$K8S_NODE_RESOURCE_GROUP" \
          --name "$VNET_NAME" \
          --query 'id' \
          --output tsv)

# Link private DNS zone to AKS VNET
az network private-dns link vnet create \
  --resource-group "$RESOURCE_GROUP" \
  --zone-name "$NETWORK_POSTGRES_DNS_ZONE" \
  --name 'MyDNSLink' \
  --virtual-network "$VNET_ID" \
  --registration-enabled false

az network private-dns link vnet wait \
  --resource-group "$RESOURCE_GROUP" \
  --zone-name "$NETWORK_POSTGRES_DNS_ZONE" \
  --name "MyDNSLink" \
  --created

# Get subnet name
SUBNET_NAME=$(az network vnet subnet list \
  --resource-group "$K8S_NODE_RESOURCE_GROUP" \
  --vnet-name "$VNET_NAME" \
  --query '[0].name' \
  --output tsv)

# Get subnet ID
SUBNET_ID=$(az network vnet subnet show \
  --resource-group "$K8S_NODE_RESOURCE_GROUP" \
  --vnet-name "$VNET_NAME" \
  --name "$SUBNET_NAME" \
  --query 'id' \
  --output tsv)

# Get PostgreSQL server ID
POSTGRES_ID=$(az postgres flexible-server show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$DATABASE_SERVER" \
  --query 'id' \
  --output tsv)

# Create private endpoint
az network private-endpoint create \
  --resource-group "$RESOURCE_GROUP" \
  --name "$NETWORK_PRIVATE_ENDPOINT" \
  --subnet "$SUBNET_ID" \
  --private-connection-resource-id "$POSTGRES_ID" \
  --connection-name "$NETWORK_PRIVATE_ENDPOINT_CONNECTION" \
  --group-id 'postgresqlServer'

az network private-endpoint wait \
  --name "$NETWORK_PRIVATE_ENDPOINT" \
  --resource-group "$RESOURCE_GROUP" \
  --created

# Get private IP
PRIVATE_IP=$(az network private-endpoint show \
             --resource-group "$RESOURCE_GROUP" \
             --name "$NETWORK_PRIVATE_ENDPOINT" \
             --query 'customDnsConfigs[0].ipAddresses[0]' \
             --output tsv)

# Create private DNS record (keep DATABASE_SERVER unique across all of Azure for consistency, although it's not mandatory)
az network private-dns record-set a add-record \
  --resource-group "$RESOURCE_GROUP" \
  --zone-name "$NETWORK_POSTGRES_DNS_ZONE" \
  --record-set-name "$DATABASE_SERVER" \
  --ipv4-address "$PRIVATE_IP"
