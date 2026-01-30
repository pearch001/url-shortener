# Makefile for URL Shortener Service
# Provides convenient commands for building, testing, and running the application

.PHONY: help build test clean run-h2 run-postgres docker-build docker-up docker-down docker-logs health-check stop restart

# Default target - show help
.DEFAULT_GOAL := help

# Colors for output
GREEN  := $(shell tput -Txterm setaf 2)
YELLOW := $(shell tput -Txterm setaf 3)
WHITE  := $(shell tput -Txterm setaf 7)
RESET  := $(shell tput -Txterm sgr0)

# Detect docker compose command
DOCKER_COMPOSE := $(shell if docker compose version > /dev/null 2>&1; then echo "docker compose"; else echo "docker-compose"; fi)

help: ## Show this help message
	@echo '$(GREEN)URL Shortener - Available Commands:$(RESET)'
	@echo ''
	@awk 'BEGIN {FS = ":.*##"; printf "Usage:\n  make $(YELLOW)<target>$(RESET)\n\nTargets:\n"} /^[a-zA-Z_-]+:.*?##/ { printf "  $(YELLOW)%-20s$(RESET) %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

build: ## Build the application (JAR + Docker image)
	@echo "$(GREEN)Building application...$(RESET)"
	@chmod +x build.sh
	@./build.sh

build-jar: ## Build only the JAR file
	@echo "$(GREEN)Building JAR file...$(RESET)"
	@./mvnw clean package -DskipTests

test: ## Run all tests
	@echo "$(GREEN)Running tests...$(RESET)"
	@./mvnw test

test-unit: ## Run unit tests only
	@echo "$(GREEN)Running unit tests...$(RESET)"
	@./mvnw test -Dtest="!**/*IntegrationTest"

test-integration: ## Run integration tests only
	@echo "$(GREEN)Running integration tests...$(RESET)"
	@./mvnw test -Dtest="**/*IntegrationTest"

test-coverage: ## Run tests with coverage report
	@echo "$(GREEN)Running tests with coverage...$(RESET)"
	@./mvnw clean test jacoco:report
	@echo "$(YELLOW)Coverage report: target/site/jacoco/index.html$(RESET)"

clean: ## Clean build artifacts and Docker volumes
	@echo "$(GREEN)Cleaning build artifacts...$(RESET)"
	@./mvnw clean
	@echo "$(GREEN)Stopping Docker containers...$(RESET)"
	@$(DOCKER_COMPOSE) down -v 2>/dev/null || true
	@echo "$(GREEN)Clean complete!$(RESET)"

run-h2: ## Run application with H2 in-memory database
	@chmod +x run-h2.sh
	@./run-h2.sh

run-postgres: docker-up ## Run application with PostgreSQL (alias for docker-up)

docker-build: ## Build Docker image
	@echo "$(GREEN)Building Docker image...$(RESET)"
	@docker build -t url-shortener:latest .
	@echo "$(GREEN)Docker image built successfully!$(RESET)"

docker-up: ## Start all services with Docker Compose
	@chmod +x run-postgres.sh
	@./run-postgres.sh

docker-down: ## Stop all Docker services
	@echo "$(GREEN)Stopping Docker services...$(RESET)"
	@$(DOCKER_COMPOSE) down

docker-down-volumes: ## Stop Docker services and remove volumes
	@echo "$(GREEN)Stopping Docker services and removing volumes...$(RESET)"
	@$(DOCKER_COMPOSE) down -v

docker-logs: ## Show logs from all Docker services
	@$(DOCKER_COMPOSE) logs -f

docker-logs-app: ## Show logs from application service only
	@$(DOCKER_COMPOSE) logs -f app

docker-logs-db: ## Show logs from database service only
	@$(DOCKER_COMPOSE) logs -f postgres

docker-ps: ## Show status of Docker services
	@$(DOCKER_COMPOSE) ps

restart: ## Restart the application service
	@echo "$(GREEN)Restarting application...$(RESET)"
	@$(DOCKER_COMPOSE) restart app
	@echo "$(GREEN)Application restarted!$(RESET)"

stop: docker-down ## Stop all services (alias for docker-down)

health-check: ## Check if application is healthy
	@chmod +x health-check.sh
	@./health-check.sh

format: ## Format code using Spring Java Format
	@echo "$(GREEN)Formatting code...$(RESET)"
	@./mvnw spring-javaformat:apply

verify: ## Verify code formatting
	@echo "$(GREEN)Verifying code format...$(RESET)"
	@./mvnw spring-javaformat:validate

package: ## Package application for distribution
	@echo "$(GREEN)Packaging application...$(RESET)"
	@./mvnw clean package -DskipTests
	@echo "$(YELLOW)JAR file: target/url-shortener-1.0.0.jar$(RESET)"

install: ## Install application to local Maven repository
	@echo "$(GREEN)Installing to local repository...$(RESET)"
	@./mvnw clean install -DskipTests

pgadmin: ## Start pgAdmin for database management
	@echo "$(GREEN)Starting pgAdmin...$(RESET)"
	@$(DOCKER_COMPOSE) --profile tools up -d pgadmin
	@echo "$(YELLOW)pgAdmin available at: http://localhost:5050$(RESET)"
	@echo "$(YELLOW)Login: admin@urlshortener.com / admin$(RESET)"

dev: run-h2 ## Start development mode with H2 (alias for run-h2)

prod: run-postgres ## Start production mode with PostgreSQL (alias for run-postgres)

# Quick test endpoint
test-create-url: ## Quick test: Create a short URL
	@echo "$(GREEN)Creating short URL...$(RESET)"
	@curl -X POST http://localhost:8080/api/urls \
	  -H "Content-Type: application/json" \
	  -d '{"longUrl": "https://www.example.com"}' \
	  | jq '.' || echo "$(YELLOW)Note: Install jq for formatted output$(RESET)"

test-health: ## Quick test: Check health endpoint
	@echo "$(GREEN)Checking application health...$(RESET)"
	@curl -s http://localhost:8080/actuator/health | jq '.' || curl http://localhost:8080/actuator/health

# Help target to display descriptions
show-config: ## Show current configuration
	@echo "$(GREEN)Current Configuration:$(RESET)"
	@echo "  Docker Compose Command: $(YELLOW)$(DOCKER_COMPOSE)$(RESET)"
	@echo "  Maven Command: $(YELLOW)./mvnw$(RESET)"
	@echo ""
	@echo "$(GREEN)Docker Images:$(RESET)"
	@docker images url-shortener 2>/dev/null || echo "  No images found"
	@echo ""
	@echo "$(GREEN)Running Containers:$(RESET)"
	@$(DOCKER_COMPOSE) ps 2>/dev/null || echo "  No containers running"
