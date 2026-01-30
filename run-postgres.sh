#!/bin/bash

# Run URL Shortener with PostgreSQL using Docker Compose
# This script starts both PostgreSQL and the application

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  URL Shortener - PostgreSQL Mode${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running!${NC}"
    echo -e "${YELLOW}Please start Docker and try again.${NC}"
    exit 1
fi

# Check if docker-compose exists
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null 2>&1; then
    echo -e "${RED}Error: docker-compose not found!${NC}"
    echo -e "${YELLOW}Please install docker-compose and try again.${NC}"
    exit 1
fi

# Determine docker compose command
if docker compose version &> /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

echo -e "${YELLOW}Starting PostgreSQL and URL Shortener...${NC}"
echo ""

# Start services
$DOCKER_COMPOSE up -d

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Services Started Successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${BLUE}Configuration:${NC}"
echo -e "  • Database:  ${GREEN}PostgreSQL 16${NC}"
echo -e "  • Profile:   ${GREEN}postgres${NC}"
echo -e "  • Port:      ${GREEN}8080${NC}"
echo ""

echo -e "${YELLOW}Endpoints:${NC}"
echo -e "  • Application:  ${GREEN}http://localhost:8080${NC}"
echo -e "  • Health Check: ${GREEN}http://localhost:8080/actuator/health${NC}"
echo -e "  • API Docs:     ${GREEN}http://localhost:8080/swagger-ui.html${NC}"
echo -e "  • Metrics:      ${GREEN}http://localhost:8080/actuator/metrics${NC}"
echo ""

echo -e "${BLUE}Database Connection:${NC}"
echo -e "  • Host:     ${GREEN}localhost:5432${NC}"
echo -e "  • Database: ${GREEN}urlshortener${NC}"
echo -e "  • Username: ${GREEN}postgres${NC}"
echo -e "  • Password: ${GREEN}postgres${NC}"
echo ""

echo -e "${YELLOW}Useful Commands:${NC}"
echo -e "  • View logs:          ${GREEN}$DOCKER_COMPOSE logs -f app${NC}"
echo -e "  • View all logs:      ${GREEN}$DOCKER_COMPOSE logs -f${NC}"
echo -e "  • Stop services:      ${GREEN}$DOCKER_COMPOSE down${NC}"
echo -e "  • Stop & remove data: ${GREEN}$DOCKER_COMPOSE down -v${NC}"
echo -e "  • Restart app:        ${GREEN}$DOCKER_COMPOSE restart app${NC}"
echo -e "  • Check status:       ${GREEN}$DOCKER_COMPOSE ps${NC}"
echo ""

echo -e "${BLUE}Optional Tools:${NC}"
echo -e "  • Start pgAdmin:      ${GREEN}$DOCKER_COMPOSE --profile tools up -d pgadmin${NC}"
echo -e "  • pgAdmin URL:        ${GREEN}http://localhost:5050${NC}"
echo -e "  • pgAdmin Login:      ${GREEN}admin@urlshortener.com / admin${NC}"
echo ""

echo -e "${YELLOW}Waiting for application to be healthy...${NC}"

# Wait for application to be healthy (max 60 seconds)
COUNTER=0
MAX_ATTEMPTS=30

while [ $COUNTER -lt $MAX_ATTEMPTS ]; do
    if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo ""
        echo -e "${GREEN}✓ Application is healthy and ready!${NC}"
        echo ""

        # Display quick test command
        echo -e "${YELLOW}Quick Test:${NC}"
        echo -e "${GREEN}curl -X POST http://localhost:8080/api/urls \\${NC}"
        echo -e "${GREEN}  -H \"Content-Type: application/json\" \\${NC}"
        echo -e "${GREEN}  -d '{\"longUrl\": \"https://www.example.com\"}'${NC}"
        echo ""

        exit 0
    fi

    echo -n "."
    sleep 2
    COUNTER=$((COUNTER + 1))
done

echo ""
echo -e "${RED}Warning: Application did not become healthy within expected time${NC}"
echo -e "${YELLOW}Check logs with: $DOCKER_COMPOSE logs -f app${NC}"
echo ""
