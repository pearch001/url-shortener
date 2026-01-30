#!/bin/bash

# Run URL Shortener with H2 in-memory database
# Useful for development and testing

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  URL Shortener - H2 Mode${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if Maven wrapper exists
if [ ! -f "./mvnw" ]; then
    echo -e "${RED}Error: Maven wrapper (mvnw) not found!${NC}"
    exit 1
fi

# Make Maven wrapper executable
chmod +x ./mvnw

echo -e "${YELLOW}Starting URL Shortener with H2 database...${NC}"
echo ""
echo -e "${BLUE}Configuration:${NC}"
echo -e "  • Database: ${GREEN}H2 (in-memory)${NC}"
echo -e "  • Profile:  ${GREEN}default${NC}"
echo -e "  • Port:     ${GREEN}8080${NC}"
echo ""
echo -e "${YELLOW}Endpoints:${NC}"
echo -e "  • Application:  ${GREEN}http://localhost:8080${NC}"
echo -e "  • Health Check: ${GREEN}http://localhost:8080/actuator/health${NC}"
echo -e "  • API Docs:     ${GREEN}http://localhost:8080/swagger-ui.html${NC}"
echo -e "  • H2 Console:   ${GREEN}http://localhost:8080/h2-console${NC}"
echo ""
echo -e "${BLUE}H2 Console Connection:${NC}"
echo -e "  • JDBC URL:  ${GREEN}jdbc:h2:mem:urlshortenerdb${NC}"
echo -e "  • Username:  ${GREEN}sa${NC}"
echo -e "  • Password:  ${GREEN}(leave empty)${NC}"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop the application${NC}"
echo ""
echo -e "${GREEN}========================================${NC}"
echo ""

# Run the application
./mvnw spring-boot:run
