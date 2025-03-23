#!/bin/bash

# Script to verify CodeVigil deployment status

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}CodeVigil Deployment Verification Script${NC}"
echo "This script will help you verify the status of your CodeVigil deployment."
echo ""

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not installed.${NC}"
    echo "Please install the AWS CLI first: https://aws.amazon.com/cli/"
    exit 1
fi

# Check AWS credentials
echo "Checking AWS credentials..."
if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}Error: AWS credentials not configured or invalid.${NC}"
    echo "Please run 'aws configure' to set up your credentials."
    exit 1
else
    ACCOUNT_ID=$(aws sts get-caller-identity --query "Account" --output text)
    echo -e "${GREEN}✓ AWS credentials valid for account ${ACCOUNT_ID}${NC}"
fi

# Check the CloudFormation stack
echo ""
echo "Checking CloudFormation stack..."
if ! aws cloudformation describe-stacks --stack-name codevigil &> /dev/null; then
    echo -e "${RED}Error: 'codevigil' CloudFormation stack not found.${NC}"
    echo "Make sure you've deployed the application using the GitHub Actions workflow."
    exit 1
else
    STACK_STATUS=$(aws cloudformation describe-stacks --stack-name codevigil --query "Stacks[0].StackStatus" --output text)
    echo -e "${GREEN}✓ CloudFormation stack status: ${STACK_STATUS}${NC}"
fi

# Get deployment resources
echo ""
echo "Retrieving deployment resources..."

# Get load balancer URL
LB_URL=$(aws cloudformation describe-stacks --stack-name codevigil --query "Stacks[0].Outputs[?ExportName=='codevigil-alb-dns'].OutputValue" --output text)
if [ -z "$LB_URL" ]; then
    echo -e "${RED}Error: Load balancer URL not found in CloudFormation outputs.${NC}"
else
    echo -e "Load Balancer URL: ${YELLOW}http://${LB_URL}${NC}"
fi

# Get ECS cluster and service
CLUSTER_NAME=$(aws cloudformation describe-stacks --stack-name codevigil --query "Stacks[0].Outputs[?ExportName=='codevigil-cluster-name'].OutputValue" --output text)
SERVICE_NAME=$(aws cloudformation describe-stacks --stack-name codevigil --query "Stacks[0].Outputs[?ExportName=='codevigil-service-name'].OutputValue" --output text)

# Check ECS service status
echo ""
echo "Checking ECS service status..."
if [ -z "$CLUSTER_NAME" ] || [ -z "$SERVICE_NAME" ]; then
    echo -e "${RED}Error: ECS cluster or service name not found in CloudFormation outputs.${NC}"
else
    SERVICE_STATUS=$(aws ecs describe-services --cluster "$CLUSTER_NAME" --services "$SERVICE_NAME" --query "services[0].status" --output text)
    DESIRED_COUNT=$(aws ecs describe-services --cluster "$CLUSTER_NAME" --services "$SERVICE_NAME" --query "services[0].desiredCount" --output text)
    RUNNING_COUNT=$(aws ecs describe-services --cluster "$CLUSTER_NAME" --services "$SERVICE_NAME" --query "services[0].runningCount" --output text)
    
    echo -e "ECS Cluster: ${YELLOW}${CLUSTER_NAME}${NC}"
    echo -e "ECS Service: ${YELLOW}${SERVICE_NAME}${NC}"
    echo -e "Service Status: ${YELLOW}${SERVICE_STATUS}${NC}"
    echo -e "Tasks: ${YELLOW}${RUNNING_COUNT}/${DESIRED_COUNT}${NC} running"
    
    if [ "$RUNNING_COUNT" -lt "$DESIRED_COUNT" ]; then
        echo -e "${RED}Warning: Not all tasks are running.${NC}"
    fi
fi

# Check RDS status
echo ""
echo "Checking RDS status..."
DB_INSTANCE=$(aws rds describe-db-instances --query "DBInstances[?DBName=='codevigil'].DBInstanceIdentifier" --output text)
if [ -z "$DB_INSTANCE" ]; then
    echo -e "${RED}Error: RDS instance for 'codevigil' not found.${NC}"
else
    DB_STATUS=$(aws rds describe-db-instances --db-instance-identifier "$DB_INSTANCE" --query "DBInstances[0].DBInstanceStatus" --output text)
    echo -e "RDS Instance: ${YELLOW}${DB_INSTANCE}${NC}"
    echo -e "Status: ${YELLOW}${DB_STATUS}${NC}"
fi

# Check API endpoint
echo ""
echo "Checking API endpoint..."
if [ -n "$LB_URL" ]; then
    echo "Sending request to http://${LB_URL}/api/repositories ..."
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://${LB_URL}/api/repositories")
    
    if [ "$HTTP_STATUS" == "200" ]; then
        echo -e "${GREEN}✓ API endpoint is accessible (HTTP 200)${NC}"
        echo ""
        echo "Full response:"
        curl -s "http://${LB_URL}/api/repositories" | json_pp
    else
        echo -e "${RED}✗ API endpoint returned HTTP ${HTTP_STATUS}${NC}"
    fi
fi

echo ""
echo -e "${GREEN}Verification complete!${NC}" 