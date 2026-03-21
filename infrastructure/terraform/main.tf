# Terraform configuration for ToyShop E-Commerce Backend
# Cloud-agnostic deployment configuration

terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.24"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.12"
    }
  }
  backend "local" {
    path = "terraform.tfstate"
  }
}

# Kubernetes Provider
provider "kubernetes" {
  config_path = "~/.kube/config"
}

# Helm Provider
provider "helm" {
  kubernetes {
    config_path = "~/.kube/config"
  }
}

# PostgreSQL Helm Release
resource "helm_release" "postgresql" {
  name       = "postgresql"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql-ha"
  version    = "12.0.0"
  namespace  = "toyshop"

  values = [
    <<-EOT
    postgresql:
      replicaCount: 1
      pgpool:
        replicaCount: 1
    pgpool:
      replicaCount: 1
    metrics:
      enabled: true
    persistence:
      size: 10Gi
    EOT
  ]
}

# Redis Helm Release
resource "helm_release" "redis" {
  name       = "redis"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "redis"
  version    = "18.0.0"
  namespace  = "toyshop"

  values = [
    <<-EOT
    architecture: standalone
    master:
      persistence:
        size: 8Gi
    metrics:
      enabled: true
    EOT
  ]
}

# Kafka Helm Release
resource "helm_release" "kafka" {
  name       = "kafka"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "kafka"
  version    = "26.0.0"
  namespace  = "toyshop"

  values = [
    <<-EOT
    replicaCount: 1
    persistence:
      size: 20Gi
    metrics:
      enabled: true
    EOT
  ]
}

# MinIO Helm Release
resource "helm_release" "minio" {
  name       = "minio"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "minio"
  version    = "6.0.0"
  namespace  = "toyshop"

  values = [
    <<-EOT
    mode: standalone
    persistence:
      size: 20Gi
    metrics:
      enabled: true
    EOT
  ]
}

# ConfigMap for application configuration
resource "kubernetes_config_map" "toyshop_config" {
  metadata {
    name      = "toyshop-config"
    namespace = "toyshop"
  }

  data = {
    "database-host"    = "postgresql-pgpool.toyshop.svc.cluster.local"
    "redis-host"       = "redis-master.toyshop.svc.cluster.local"
    "kafka-host"       = "kafka-0.kafka-headless.toyshop.svc.cluster.local:9092"
    "minio-endpoint"   = "http://minio.toyshop.svc.cluster.local:9000"
  }
}

# Secret for sensitive data
resource "kubernetes_secret" "toyshop_secrets" {
  metadata {
    name      = "toyshop-secrets"
    namespace = "toyshop"
  }

  data = {
    "database-password" = "toyshop-secure-password"
    "jwt-secret"        = "toyshop-jwt-secret-key-for-signing"
  }

  type = "Opaque"
}