# ToyShop Infrastructure - Cloud Agnostic Terraform Configuration

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.0"
    }
  }
  
  backend "s3" {
    bucket = "toyshop-terraform-state"
    key    = "infrastructure/terraform.tfstate"
    region = "us-east-1"
  }
}

provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = {
      Project     = "toyshop"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# Variables
variable "aws_region" {
  default = "us-east-1"
}

variable "environment" {
  default = "development"
}

variable "cluster_name" {
  default = "toyshop-cluster"
}

# VPC Configuration
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"
  
  name = "toyshop-vpc-${var.environment}"
  cidr = "10.0.0.0/16"
  
  azs             = ["${var.aws_region}a", "${var.aws_region}b", "${var.aws_region}c"]
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
  
  enable_nat_gateway     = true
  single_nat_gateway     = var.environment == "development"
  enable_dns_hostnames   = true
  enable_dns_support     = true
  
  tags = {
    Name = "toyshop-vpc-${var.environment}"
  }
}

# EKS Cluster
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"
  
  cluster_name    = var.cluster_name
  cluster_version = "1.29"
  
  vpc_id                         = module.vpc.vpc_id
  subnet_ids                     = module.vpc.private_subnets
  control_plane_subnet_ids       = module.vpc.private_subnets
  
  cluster_endpoint_public_access = true
  
  eks_managed_node_groups = {
    general = {
      instance_types = ["t3.medium"]
      min_size       = 2
      max_size       = 10
      desired_size   = 3
      
      labels = {
        node-group = "general"
      }
    }
    
    workers = {
      instance_types = ["t3.large"]
      min_size       = 1
      max_size       = 5
      desired_size   = 2
      
      labels = {
        node-group = "workers"
      }
    }
  }
  
  enable_efa_support = false
  
  tags = {
    Environment = var.environment
  }
}

# RDS PostgreSQL
resource "aws_db_instance" "toyshop" {
  identifier        = "toyshop-db-${var.environment}"
  engine            = "postgres"
  engine_version    = "15.4"
  instance_class    = var.environment == "production" ? "db.t3.medium" : "db.t3.micro"
  allocated_storage = 20
  storage_encrypted = true
  
  db_name  = "toyshop"
  username = "toyshop_admin"
  password = random_password.db_password.result
  
  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.toyshop.name
  
  backup_retention_period = var.environment == "production" ? 7 : 1
  skip_final_snapshot     = var.environment != "production"
  deletion_protection     = var.environment == "production"
  
  tags = {
    Name = "toyshop-db-${var.environment}"
  }
}

resource "aws_db_subnet_group" "toyshop" {
  name       = "toyshop-db-subnet-${var.environment}"
  subnet_ids = module.vpc.private_subnets
}

resource "random_password" "db_password" {
  length  = 32
  special = false
}

# ElastiCache Redis
resource "aws_elasticache_cluster" "toyshop" {
  cluster_id           = "toyshop-redis-${var.environment}"
  engine               = "redis"
  node_type            = var.environment == "production" ? "cache.t3.medium" : "cache.t3.micro"
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  engine_version       = "7.0"
  port                 = 6379
  
  subnet_group_name  = aws_elasticache_subnet_group.toyshop.name
  security_group_ids = [aws_security_group.redis.id]
  
  tags = {
    Name = "toyshop-redis-${var.environment}"
  }
}

resource "aws_elasticache_subnet_group" "toyshop" {
  name       = "toyshop-redis-subnet-${var.environment}"
  subnet_ids = module.vpc.private_subnets
}

# MSK Kafka
resource "aws_msk_cluster" "toyshop" {
  cluster_name           = "toyshop-kafka-${var.environment}"
  kafka_version          = "3.5.1"
  number_of_broker_nodes = var.environment == "production" ? 6 : 3
  
  broker_node_group_info {
    instance_type   = var.environment == "production" ? "kafka.m5.2xlarge" : "kafka.m5.large"
    client_subnets  = module.vpc.private_subnets
    storage_info {
      ebs_storage_info {
        volume_size = 100
      }
    }
    security_groups = [aws_security_group.kafka.id]
  }
  
  encryption_info {
    encryption_at_rest_kms_key_arn = aws_kms_key.kafka.arn
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }
  
  configuration_info {
    arn      = aws_msk_configuration.toyshop.arn
    revision = aws_msk_configuration.toyshop.latest_revision
  }
  
  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.kafka.name
      }
    }
  }
}

resource "aws_msk_configuration" "toyshop" {
  name = "toyshop-kafka-config-${var.environment}"
  
  kafka_versions = ["3.5.1"]
  
  server_properties = <<EOF
auto.create.topics.enable = true
delete.topic.enable = true
EOF
}

resource "aws_kms_key" "kafka" {
  description = "KMS key for Kafka encryption"
  key_usage   = "ENCRYPT_DECRYPT"
  key_policy  = <<EOF
{
  "Version": "2012-10-17",
  "Id": "toyshop-kafka-key",
  "Statement": [
    {
      "Sid": "Enable IAM policies",
      "Effect": "Allow",
      "Principal": {"AWS": "*"},
      "Action": "kms:*",
      "Resource": "*",
      "Condition": {"StringEquals": {"aws:SourceAccount": "${data.aws_caller.current.account_id}"}}
    }
  ]
}
EOF
}

resource "aws_cloudwatch_log_group" "kafka" {
  name = "/aws/msk/toyshop-${var.environment}"
  retention_in_days = 30
}

# Security Groups
resource "aws_security_group" "rds" {
  name        = "toyshop-rds-sg-${var.environment}"
  description = "Security group for RDS PostgreSQL"
  vpc_id      = module.vpc.vpc_id
  
  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = module.vpc.private_subnet_cidr_blocks
  }
}

resource "aws_security_group" "redis" {
  name        = "toyshop-redis-sg-${var.environment}"
  description = "Security group for ElastiCache Redis"
  vpc_id      = module.vpc.vpc_id
  
  ingress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = module.vpc.private_subnet_cidr_blocks
  }
}

resource "aws_security_group" "kafka" {
  name        = "toyshop-kafka-sg-${var.environment}"
  description = "Security group for MSK Kafka"
  vpc_id      = module.vpc.vpc_id
  
  ingress {
    from_port   = 9092
    to_port     = 9092
    protocol    = "tcp"
    cidr_blocks = module.vpc.private_subnet_cidr_blocks
  }
  
  ingress {
    from_port   = 9094
    to_port     = 9094
    protocol    = "tcp"
    cidr_blocks = module.vpc.private_subnet_cidr_blocks
  }
}

# S3 Bucket for product images
resource "aws_s3_bucket" "toyshop_images" {
  bucket = "toyshop-product-images-${var.environment}"
  
  versioning {
    enabled = true
  }
  
  server_side_encryption_configuration {
    rule {
      apply_server_side_encryption_by_default {
        sse_algorithm = "AES256"
      }
    }
  }
  
  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT", "POST"]
    allowed_origins = ["*"]
    max_age         = 3600
  }
}

# Outputs
output "vpc_id" {
  value = module.vpc.vpc_id
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "rds_endpoint" {
  value = aws_db_instance.toyshop.endpoint
}

output "redis_endpoint" {
  value = aws_elasticache_cluster.toyshop.cache_nodes[0].address
}

output "kafka_bootstrap_brokers" {
  value = aws_msk_cluster.toyshop.bootstrap_brokers
}

output "s3_bucket_name" {
  value = aws_s3_bucket.toyshop_images.bucket
}

output "kubeconfig" {
  value     = module.eks.kubeconfig
  sensitive = true
}

data "aws_caller_identity" "current" {}