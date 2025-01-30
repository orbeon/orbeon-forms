#!/bin/bash

if ! az aks show \
    --name "$K8S_CLUSTER_NAME" \
    --resource-group "$RESOURCE_GROUP" >/dev/null 2>&1; then
  if ! az aks create \
      --resource-group "$RESOURCE_GROUP" \
      --name "$K8S_CLUSTER_NAME" \
      --node-count 1 \
      --network-plugin azure \
      --generate-ssh-keys; then
    echo "Failed to create Kubernetes cluster $K8S_CLUSTER_NAME"
    return 1
  fi
  echo "Kubernetes cluster $K8S_CLUSTER_NAME created successfully"
else
  echo "Kubernetes cluster $K8S_CLUSTER_NAME already exists"
fi

# Retrieve AKS credentials, save them locally to ~/.kube/config, and set the AKS cluster as the current context
az aks get-credentials \
  --resource-group "$RESOURCE_GROUP" \
  --name "$K8S_CLUSTER_NAME" \
  --overwrite-existing

# Display all contexts and current context
kubectl config get-contexts

# Display some information about the cluster
kubectl cluster-info

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
   shareName: $STORAGE_SHARE_NAME
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
       image: orbeon/orbeon-forms:$ORBEON_FORMS_DOCKER_TAG
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
           mountPath: /opt/jboss/wildfly/standalone/configuration/standalone.xml
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

display_cluster_info() {
  echo -e "\nPods information:\n"
  kubectl describe pods

  echo -e "\nNodes information:\n"
  kubectl get nodes -o wide

  echo -e "\nService information:\n"
  kubectl get service "$K8S_SERVICE"
}

# Display various information about the K8S nodes, pods, and service
display_cluster_info

# Retrieve external/public IP
echo -e "\nWaiting for external IP for service $K8S_SERVICE..."
while K8S_EXTERNAL_IP=$(kubectl get service "$K8S_SERVICE" --output jsonpath='{.status.loadBalancer.ingress[0].ip}') && [[ -z "$K8S_EXTERNAL_IP" ]]; do
    echo -n "."
    sleep 10
done
echo

# Orbeon Forms app URL
K8S_APP_URL="https://$K8S_EXTERNAL_IP/orbeon"
echo -e "\nOrbeon Forms now available on Azure Kubernetes cluster: $K8S_APP_URL\n"

# Update the Entra ID redirect URIs
az ad app update --id "$ENTRA_ID_APP_ID" --web-redirect-uris "$LOCAL_APP_URL/*" "$K8S_APP_URL/*"

# Retrieve pod name
K8S_POD=$(kubectl get pod -o name | head -1)
echo -e "\nPod name: $K8S_POD\n"

echo "Use this command to display the pod logs: kubectl logs $K8S_POD -f"

undeploy() {
  kubectl delete -f "$K8S_DEPLOYMENT.yaml"
  kubectl delete -f "$K8S_PERSISTENCE_VOLUME_CLAIM.yaml"
  kubectl delete -f "$K8S_PERSISTENCE_VOLUME.yaml"
  kubectl delete -f "$K8S_STORAGE_SECRET.yaml"
}
