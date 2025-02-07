#!/bin/bash

if ! az aks show \
    --name "$K8S_CLUSTER" \
    --resource-group "$RESOURCE_GROUP" >/dev/null 2>&1; then
  if ! az aks create \
      --name "$K8S_CLUSTER" \
      --resource-group "$RESOURCE_GROUP" \
      --node-count 1 \
      --network-plugin azure \
      --generate-ssh-keys; then
    echo "Failed to create Kubernetes cluster $K8S_CLUSTER"
    exit 1
  fi
  echo "Kubernetes cluster $K8S_CLUSTER created successfully"
else
  echo "Kubernetes cluster $K8S_CLUSTER already exists"
fi

# Retrieve AKS credentials, save them locally to ~/.kube/config, and set the AKS cluster as the current context
az aks get-credentials \
  --name "$K8S_CLUSTER" \
  --resource-group "$RESOURCE_GROUP" \
  --overwrite-existing

if [ "$CONTAINER_CUSTOM_IMAGE_ENABLED" = 'true' ]; then
  # Retrieve the cluster's client ID
  K8S_CLIENT_ID=$(az aks show \
                  --name "$K8S_CLUSTER" \
                  --resource-group "$RESOURCE_GROUP" \
                  --query "identityProfile.kubeletidentity.clientId" \
                  -o tsv)

  # Grant permission to the cluster to pull images from the Azure Container Registry
  az role assignment create --assignee "$K8S_CLIENT_ID" --role AcrPull --scope "$CONTAINER_REGISTRY_ID"
fi

# Generate the storage account name/key secret file
cat > "$K8S_STORAGE_SECRET.yaml" << EOF
apiVersion: v1
kind: Secret
metadata:
  name: $K8S_STORAGE_SECRET
type: Opaque
data:
  azurestorageaccountname: $(echo -n "$STORAGE_ACCOUNT" | base64)
  azurestorageaccountkey: $(echo -n "$STORAGE_ACCOUNT_SECRET_KEY" | base64)
EOF

# Import the storage account name/key secret
kubectl apply -f "$K8S_STORAGE_SECRET.yaml"

# Generate the persistence volume configuration file
cat > "$K8S_PERSISTENCE_VOLUME.yaml" << EOF
apiVersion: v1
kind: PersistentVolume
metadata:
  name: $K8S_PERSISTENCE_VOLUME
spec:
  capacity:
    storage: 5Gi
  accessModes:
    - ReadWriteMany
  storageClassName: azure-file
  azureFile:
    secretName: $K8S_STORAGE_SECRET
    shareName: $STORAGE_SHARE
    readOnly: false
EOF

# Import the persistence volume configuration
kubectl apply -f "$K8S_PERSISTENCE_VOLUME.yaml"

# Generate the persistence volume claim configuration file
cat > "$K8S_PERSISTENCE_VOLUME_CLAIM.yaml" << EOF
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: $K8S_PERSISTENCE_VOLUME_CLAIM
spec:
  accessModes:
    - ReadWriteMany
  storageClassName: azure-file
  resources:
    requests:
      storage: 5Gi
EOF

# Import the persistence volume claim configuration
kubectl apply -f "$K8S_PERSISTENCE_VOLUME_CLAIM.yaml"

if [ "$CONTAINER_CUSTOM_IMAGE_ENABLED" = 'false' ]; then
  # Unmodified Orbeon Forms image
  K8S_IMAGE="orbeon/orbeon-forms:$ORBEON_FORMS_DOCKER_TAG"
else
  # Custom Orbeon Forms image
  K8S_IMAGE="$CONTAINER_REGISTRY.azurecr.io/$CONTAINER_CUSTOM_IMAGE"
fi

# Generate the deployment configuration file
cat > "$K8S_DEPLOYMENT.yaml" << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: $K8S_DEPLOYMENT
  labels:
    app: $K8S_APP
spec:
  replicas: 1
  selector:
    matchLabels:
      app: $K8S_APP
  template:
    metadata:
      labels:
        app: $K8S_APP
    spec:
      containers:
      - name: orbeon-forms
        image: $K8S_IMAGE
        ports:
        - containerPort: 8443
        volumeMounts:
          - name: azure-volume
            mountPath: /opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/resources/config/license.xml
            subPath: license.xml
          - name: azure-volume
            mountPath: /opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/resources/config/form-builder-permissions.xml
            subPath: form-builder-permissions.xml
          - name: azure-volume
            mountPath: /opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/resources/config/properties-local.xml
            subPath: properties-local.xml
          - name: azure-volume
            mountPath: /opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/jboss-web.xml
            subPath: jboss-web.xml
          - name: azure-volume
            mountPath: /opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/oidc.json
            subPath: oidc.json
          - name: azure-volume
            mountPath: /opt/jboss/wildfly/standalone/deployments/orbeon.war/WEB-INF/web.xml
            subPath: web.xml
          - name: azure-volume
            mountPath: /opt/jboss/wildfly/standalone/configuration/application.keystore
            subPath: application.keystore
          - name: azure-volume
            mountPath: /docker-entrypoint-wildfly.d/standalone.xml
            subPath: standalone.xml
      volumes:
        - name: azure-volume
          persistentVolumeClaim:
            claimName: $K8S_PERSISTENCE_VOLUME_CLAIM
---
apiVersion: v1
kind: Service
metadata:
  name: $K8S_SERVICE
spec:
  type: LoadBalancer
  selector:
    app: $K8S_APP
  ports:
    - protocol: TCP
      port: 443
      targetPort: 8443
EOF

# Import the deployment configuration
kubectl apply -f "$K8S_DEPLOYMENT.yaml"

display_info() {
  echo -e "\nContexts information:\n"
  kubectl config get-contexts

  echo -e "\nCluster information:\n"
  kubectl cluster-info

  echo -e "\nPods information:\n"
  kubectl describe pods

  echo -e "\nNodes information:\n"
  kubectl get nodes -o wide

  echo -e "\nService information:\n"
  kubectl get service "$K8S_SERVICE"
}

# Display various information about the K8S contexts, cluster, nodes, pods, and service
display_info

# Retrieve external/public IP
echo -e "\nWaiting for external IP for service $K8S_SERVICE..."
while K8S_EXTERNAL_IP=$(kubectl get service "$K8S_SERVICE" --output jsonpath='{.status.loadBalancer.ingress[0].ip}') && [[ -z "$K8S_EXTERNAL_IP" ]]; do
  echo -n "."
  sleep 10
done
echo

# Kubernetes node resource group
K8S_NODE_RESOURCE_GROUP="MC_${RESOURCE_GROUP}_${K8S_CLUSTER}_${AZURE_LOCATION}"

# Orbeon Forms app URL
K8S_APP_URL="https://$K8S_EXTERNAL_IP/orbeon"

# Update the Entra ID redirect URIs
az ad app update --id "$ENTRA_ID_APP_ID" --web-redirect-uris "$LOCAL_APP_URL/*" "$K8S_APP_URL/*"

# Retrieve pod name
K8S_POD=$(kubectl get pod -o name | head -1)
echo -e "\nPod name: $K8S_POD\n"

undeploy() {
  kubectl delete -f "$K8S_DEPLOYMENT.yaml"
  kubectl delete -f "$K8S_PERSISTENCE_VOLUME_CLAIM.yaml"
  kubectl delete -f "$K8S_PERSISTENCE_VOLUME.yaml"
  kubectl delete -f "$K8S_STORAGE_SECRET.yaml"
}
