#!/bin/bash

# Build script for URL Shortener application
# This script builds both the JAR and Docker image

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  URL Shortener - Build Script${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if Maven wrapper exists
if [ ! -f "./mvnw" ]; then
    echo -e "${RED}Error: Maven wrapper (mvnw) not found!${NC}"
    exit 1
fi

# Make Maven wrapper executable
chmod +x ./mvnw

# Step 1: Clean and build JAR
echo -e "${YELLOW}[1/3] Cleaning previous build...${NC}"
./mvnw clean

echo ""
echo -e "${YELLOW}[2/3] Building JAR file (skipping tests)...${NC}"
./mvnw package -DskipTests

# Check if JAR was created
if [ ! -f target/*.jar ]; then
    echo -e "${RED}Error: JAR file not found in target directory!${NC}"
    exit 1
fi

echo -e "${GREEN}✓ JAR build successful${NC}"
echo ""

# Step 3: Build Docker image
echo -e "${YELLOW}[3/3] Building Docker image...${NC}"
docker build -t url-shortener:latest .

# Tag with version if provided
if [ -n "$1" ]; then
    VERSION=$1
    echo -e "${YELLOW}Tagging image with version: ${VERSION}${NC}"
    docker tag url-shortener:latest url-shortener:${VERSION}
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Build Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Docker image: ${GREEN}url-shortener:latest${NC}"

# Display image size
IMAGE_SIZE=$(docker images url-shortener:latest --format "{{.Size}}")
echo -e "Image size: ${GREEN}${IMAGE_SIZE}${NC}"
echo ""

echo -e "${YELLOW}Next steps:${NC}"
echo -e "  • Run with H2:         ${GREEN}./run-h2.sh${NC}"
echo -e "  • Run with PostgreSQL: ${GREEN}./run-postgres.sh${NC}"
echo -e "  • Or use:              ${GREEN}make run-postgres${NC}"
echo ""
