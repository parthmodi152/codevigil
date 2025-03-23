#!/bin/bash

# Script to help set up GitHub repository secrets for CI/CD pipeline

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}CodeVigil CI/CD Setup Script${NC}"
echo "This script will help you set up the required GitHub secrets for your CI/CD pipeline."
echo ""

# Check if the gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo -e "${RED}Error: GitHub CLI (gh) is not installed.${NC}"
    echo "Please install the GitHub CLI first: https://github.com/cli/cli#installation"
    exit 1
fi

# Check if logged in to GitHub
echo "Checking GitHub authentication..."
if ! gh auth status &> /dev/null; then
    echo -e "${YELLOW}You need to log in to GitHub CLI.${NC}"
    gh auth login
else
    echo -e "${GREEN}✓ Authenticated with GitHub${NC}"
fi

# Get repository information
echo ""
echo -e "${YELLOW}Please enter your GitHub repository information:${NC}"
read -p "GitHub username or organization: " GITHUB_OWNER
read -p "Repository name: " GITHUB_REPO

# Validate repository access
echo ""
echo "Validating repository access..."
if ! gh repo view "${GITHUB_OWNER}/${GITHUB_REPO}" &> /dev/null; then
    echo -e "${RED}Error: Repository not found or you don't have access.${NC}"
    exit 1
else
    echo -e "${GREEN}✓ Repository access confirmed${NC}"
fi

# AWS Credentials
echo ""
echo -e "${YELLOW}Please enter your AWS credentials:${NC}"
read -p "AWS Access Key ID: " AWS_ACCESS_KEY_ID
read -sp "AWS Secret Access Key: " AWS_SECRET_ACCESS_KEY
echo ""

# Database and GitHub Token
echo ""
echo -e "${YELLOW}Please enter additional required information:${NC}"
read -sp "PostgreSQL database password: " DB_PASSWORD
echo ""
read -sp "GitHub Personal Access Token: " GITHUB_TOKEN
echo ""

# Set up the secrets
echo ""
echo "Setting up GitHub secrets..."

set_secret() {
    local secret_name=$1
    local secret_value=$2
    
    echo "Setting $secret_name..."
    if echo "$secret_value" | gh secret set "$secret_name" -R "${GITHUB_OWNER}/${GITHUB_REPO}" &> /dev/null; then
        echo -e "${GREEN}✓ $secret_name set successfully${NC}"
        return 0
    else
        echo -e "${RED}✗ Failed to set $secret_name${NC}"
        return 1
    fi
}

set_secret "AWS_ACCESS_KEY_ID" "$AWS_ACCESS_KEY_ID"
set_secret "AWS_SECRET_ACCESS_KEY" "$AWS_SECRET_ACCESS_KEY"
set_secret "DB_PASSWORD" "$DB_PASSWORD"
set_secret "GITHUB_TOKEN" "$GITHUB_TOKEN"

echo ""
echo -e "${GREEN}Setup complete!${NC}"
echo "You can now run your GitHub Actions workflow to deploy the application."
echo "Make sure you've pushed the workflow file (.github/workflows/deploy.yml) to your repository." 