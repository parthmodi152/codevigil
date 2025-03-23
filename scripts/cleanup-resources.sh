#!/bin/bash

# Script to clean up CodeVigil AWS resources

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${RED}CodeVigil AWS Resource Cleanup Script${NC}"
echo -e "${YELLOW}WARNING: This script will delete all AWS resources created for CodeVigil.${NC}"
echo -e "${YELLOW}         This action is IRREVERSIBLE and will result in DATA LOSS.${NC}"
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

# Confirm cleanup
echo ""
read -p "Are you sure you want to delete all CodeVigil AWS resources? (Type 'yes' to confirm): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
    echo "Cleanup cancelled."
    exit 0
fi

echo ""
read -p "This will delete the database and all application data. Type 'I understand' to confirm: " CONFIRM_DATA
if [ "$CONFIRM_DATA" != "I understand" ]; then
    echo "Cleanup cancelled."
    exit 0
fi

# Check if the CloudFormation stack exists
STACK_EXISTS=$(aws cloudformation describe-stacks --stack-name codevigil 2>/dev/null || echo "not_exists")

if [ "$STACK_EXISTS" != "not_exists" ]; then
    echo ""
    echo "CloudFormation stack 'codevigil' found. Deleting..."
    
    # Get ECR repository name first
    ECR_REPO=$(aws cloudformation describe-stacks --stack-name codevigil --query "Stacks[0].Outputs[?ExportName=='codevigil-repository-url'].OutputValue" --output text)
    ECR_REPO_NAME="codevigil"  # This is hardcoded in our CloudFormation template
    
    # Delete the CloudFormation stack
    aws cloudformation delete-stack --stack-name codevigil
    
    echo "Waiting for stack deletion to complete (this may take several minutes)..."
    aws cloudformation wait stack-delete-complete --stack-name codevigil
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ CloudFormation stack deleted successfully.${NC}"
    else
        echo -e "${RED}Error: CloudFormation stack deletion failed or timed out.${NC}"
        echo "You may need to check the AWS Console and delete remaining resources manually."
    fi
    
    # Clean up ECR repository images if it exists
    if [ -n "$ECR_REPO" ]; then
        echo ""
        echo "Cleaning up ECR repository..."
        
        # List images and delete them
        IMAGE_IDS=$(aws ecr list-images --repository-name "$ECR_REPO_NAME" --query 'imageIds[*]' --output json 2>/dev/null || echo "")
        
        if [ -n "$IMAGE_IDS" ] && [ "$IMAGE_IDS" != "[]" ]; then
            echo "Deleting images from ECR repository..."
            aws ecr batch-delete-image --repository-name "$ECR_REPO_NAME" --image-ids "$IMAGE_IDS" || true
            echo -e "${GREEN}✓ ECR images deleted.${NC}"
        fi
    fi
else
    echo -e "${YELLOW}CloudFormation stack 'codevigil' not found. Skipping stack deletion.${NC}"
    
    # Check if there are any resources with the prefix 'codevigil' that should be deleted manually
    echo ""
    echo "Checking for other CodeVigil resources that may need manual cleanup..."
    
    # Check for ECS cluster
    ECS_CLUSTER=$(aws ecs list-clusters --query "clusterArns[?contains(@, 'codevigil')]" --output text)
    if [ -n "$ECS_CLUSTER" ]; then
        echo -e "${YELLOW}ECS cluster with 'codevigil' in the name found: ${ECS_CLUSTER}${NC}"
        echo "Please delete this manually if needed."
    fi
    
    # Check for RDS instances
    RDS_INSTANCES=$(aws rds describe-db-instances --query "DBInstances[?DBName=='codevigil'].DBInstanceIdentifier" --output text)
    if [ -n "$RDS_INSTANCES" ]; then
        echo -e "${YELLOW}RDS instances for 'codevigil' found: ${RDS_INSTANCES}${NC}"
        echo "Please delete these manually if needed."
    fi
    
    # Check for ECR repositories
    ECR_REPOS=$(aws ecr describe-repositories --query "repositories[?contains(repositoryName, 'codevigil')].repositoryName" --output text)
    if [ -n "$ECR_REPOS" ]; then
        echo -e "${YELLOW}ECR repositories with 'codevigil' in the name found: ${ECR_REPOS}${NC}"
        echo "Please delete these manually if needed."
    fi
    
    # Check for load balancers
    LBS=$(aws elbv2 describe-load-balancers --query "LoadBalancers[?contains(LoadBalancerName, 'codevigil')].LoadBalancerName" --output text)
    if [ -n "$LBS" ]; then
        echo -e "${YELLOW}Load balancers with 'codevigil' in the name found: ${LBS}${NC}"
        echo "Please delete these manually if needed."
    fi
fi

echo ""
echo -e "${GREEN}Cleanup process completed.${NC}"
echo "Note: Some resources may still exist if they were created outside of CloudFormation or have termination protection enabled."
echo "Please check the AWS Console for any remaining resources." 