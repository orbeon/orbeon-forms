#!/bin/bash

exit 1

# TODO: cleanup code below

# --enable-addons monitoring \ ???
az aks create \
  --resource-group myResourceGroup \
  --name orbeonTestCluster \
  --node-count 1 \
  --generate-ssh-keys

az aks get-credentials \
  --resource-group myResourceGroup \
  --name orbeonTestCluster

# Import storage account access keys as K8s secret
kubectl apply -f azurestorage-secret.yaml

kubectl apply -f orbeon-pv.yaml
kubectl apply -f orbeon-pvc.yaml

# Check active context
kubectl config get-contexts

kubectl apply -f orbeon.yaml

# Display various information
#kubectl describe pods
#kubectl get pod
#kubectl get nodes -o wide
kubectl get pod

# Display logs
kubectl logs  $(kubectl get pods | grep -v NAME | cut -d' ' -f1) -f

# Retrieve external/public IP
kubectl get service orbeon-forms-service

# Access http://40.83.239.27/orbeon

# ...

kubectl delete -f orbeon.yaml
kubectl delete -f orbeon-pvc.yaml
kubectl delete -f orbeon-pv.yaml
kubectl delete -f azurestorage-secret.yaml
