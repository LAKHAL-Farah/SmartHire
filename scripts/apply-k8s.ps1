param(
    [string]$Namespace = "smarthire"
)

$ErrorActionPreference = "Stop"

Write-Host "Applying SmartHire Kubernetes manifests to namespace '$Namespace'..."

kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/storage/mysql-pvc.yaml
kubectl apply -f k8s/secrets/secrets.example.yaml
kubectl apply -f k8s/configmaps/mysql-config.yaml
kubectl apply -f k8s/configmaps/gateway-config.yaml
kubectl apply -f k8s/configmaps/frontend-config.yaml
kubectl apply -f k8s/statefulsets/mysql-statefulset.yaml
kubectl apply -f k8s/services/mysql-service.yaml
kubectl apply -f k8s/deployments/gateway-deployment.yaml
kubectl apply -f k8s/services/gateway-service.yaml
kubectl apply -f k8s/deployments/frontend-deployment.yaml
kubectl apply -f k8s/ingress/ingress.yaml

Write-Host "Done. Review any placeholder image names and replace them with your registry before production use."