#!/bin/bash

# Health check script for URL Shortener application
# Waits for the application to start and become healthy

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
HOST="${HOST:-localhost}"
PORT="${PORT:-8080}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-30}"
SLEEP_INTERVAL="${SLEEP_INTERVAL:-2}"
HEALTH_ENDPOINT="http://${HOST}:${PORT}/actuator/health"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  URL Shortener - Health Check${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Configuration:${NC}"
echo -e "  • Endpoint:      ${GREEN}${HEALTH_ENDPOINT}${NC}"
echo -e "  • Max attempts:  ${GREEN}${MAX_ATTEMPTS}${NC}"
echo -e "  • Check interval: ${GREEN}${SLEEP_INTERVAL}s${NC}"
echo ""
echo -e "${YELLOW}Waiting for application to start...${NC}"
echo ""

COUNTER=0

while [ $COUNTER -lt $MAX_ATTEMPTS ]; do
    COUNTER=$((COUNTER + 1))

    # Try to get health status
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" ${HEALTH_ENDPOINT} 2>/dev/null || echo "000")

    if [ "$HTTP_CODE" = "200" ]; then
        # Get detailed health information
        HEALTH_RESPONSE=$(curl -s ${HEALTH_ENDPOINT} 2>/dev/null)
        STATUS=$(echo $HEALTH_RESPONSE | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

        if [ "$STATUS" = "UP" ]; then
            echo ""
            echo -e "${GREEN}========================================${NC}"
            echo -e "${GREEN}  ✓ Application is Healthy!${NC}"
            echo -e "${GREEN}========================================${NC}"
            echo ""
            echo -e "${BLUE}Health Details:${NC}"
            echo "$HEALTH_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$HEALTH_RESPONSE"
            echo ""
            echo -e "${GREEN}Application is ready to accept requests${NC}"
            echo ""
            echo -e "${YELLOW}Available Endpoints:${NC}"
            echo -e "  • API Base:     ${GREEN}http://${HOST}:${PORT}/api/urls${NC}"
            echo -e "  • Health:       ${GREEN}http://${HOST}:${PORT}/actuator/health${NC}"
            echo -e "  • Metrics:      ${GREEN}http://${HOST}:${PORT}/actuator/metrics${NC}"
            echo -e "  • API Docs:     ${GREEN}http://${HOST}:${PORT}/swagger-ui.html${NC}"
            echo ""
            exit 0
        fi
    fi

    # Progress indicator
    echo -ne "${YELLOW}Attempt ${COUNTER}/${MAX_ATTEMPTS}: Waiting for application... ${NC}\r"

    sleep $SLEEP_INTERVAL
done

# Timeout reached
echo ""
echo ""
echo -e "${RED}========================================${NC}"
echo -e "${RED}  ✗ Health Check Failed${NC}"
echo -e "${RED}========================================${NC}"
echo ""
echo -e "${RED}Application did not become healthy within ${MAX_ATTEMPTS} attempts ($(($MAX_ATTEMPTS * $SLEEP_INTERVAL))s)${NC}"
echo ""
echo -e "${YELLOW}Troubleshooting:${NC}"
echo -e "  1. Check if the application is running:"
echo -e "     ${GREEN}docker ps${NC} or ${GREEN}docker-compose ps${NC}"
echo ""
echo -e "  2. Check application logs:"
echo -e "     ${GREEN}docker-compose logs -f app${NC}"
echo ""
echo -e "  3. Verify port is not in use:"
echo -e "     ${GREEN}lsof -i :${PORT}${NC} or ${GREEN}netstat -an | grep ${PORT}${NC}"
echo ""
echo -e "  4. Check database connectivity:"
echo -e "     ${GREEN}docker-compose logs postgres${NC}"
echo ""
echo -e "  5. Try accessing health endpoint manually:"
echo -e "     ${GREEN}curl ${HEALTH_ENDPOINT}${NC}"
echo ""

exit 1
