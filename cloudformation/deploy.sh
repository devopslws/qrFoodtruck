#!/bin/bash
# FoodTruck - CloudFormation deploy script
# Run from inside the cloudformation/ directory
# no Korean for encoding issue
# only use for first bulid infra.

set -e

PROJECT=foodtruck
REGION=ap-northeast-2
KEY_PAIR_NAME="foodtruck-key"

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

echo "============================================"
echo " FoodTruck CloudFormation Deploy"
echo " Account: $AWS_ACCOUNT_ID / Region: $REGION"
echo "============================================"

echo ""
echo "[1/4] Deploying VPC stack..."
aws cloudformation deploy \
  --template-file 01_vpc.yml \
  --stack-name ${PROJECT}-vpc \
  --parameter-overrides ProjectName=${PROJECT} \
  --region ${REGION} \
  --no-fail-on-empty-changeset
echo "VPC done"

echo ""
echo "[2/4] Deploying Infra EC2 stack..."
aws cloudformation deploy \
  --template-file 02_infra.yml \
  --stack-name ${PROJECT}-infra \
  --parameter-overrides \
      ProjectName=${PROJECT} \
      KeyPairName=${KEY_PAIR_NAME} \
  --capabilities CAPABILITY_NAMED_IAM \
  --region ${REGION} \
  --no-fail-on-empty-changeset

INFRA_IP=$(aws cloudformation describe-stacks \
  --stack-name ${PROJECT}-infra \
  --query 'Stacks[0].Outputs[?OutputKey==`InfraPublicIp`].OutputValue' \
  --output text \
  --region ${REGION})
echo "Infra EC2 done - IP: $INFRA_IP"

echo ""
echo "[3/4] Deploying ECR stack..."
aws cloudformation deploy \
  --template-file 03_ecr.yml \
  --stack-name ${PROJECT}-ecr \
  --parameter-overrides ProjectName=${PROJECT} \
  --region ${REGION} \
  --no-fail-on-empty-changeset
echo "ECR done"

echo ""
echo "[4/4] Deploying ECS + ALB stack..."
aws cloudformation deploy \
  --template-file 04_ecs.yml \
  --stack-name ${PROJECT}-ecs \
  --parameter-overrides \
      ProjectName=${PROJECT} \
      InfraIp=${INFRA_IP} \
  --capabilities CAPABILITY_NAMED_IAM \
  --region ${REGION} \
  --no-fail-on-empty-changeset
echo "ECS done"

ALB_DNS=$(aws cloudformation describe-stacks \
  --stack-name ${PROJECT}-ecs \
  --query 'Stacks[0].Outputs[?OutputKey==`AlbDnsName`].OutputValue' \
  --output text --region ${REGION})

echo ""
echo "============================================"
echo " Deploy complete!"
echo " URL: http://${ALB_DNS}"
echo "============================================"
