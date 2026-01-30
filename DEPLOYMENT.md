# Deployment Guide - URL Shortener Service

This guide provides instructions for building, deploying, and running the URL Shortener service in various environments.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Development Deployment](#development-deployment)
- [Production Deployment](#production-deployment)
- [Docker Deployment](#docker-deployment)
- [Environment Configuration](#environment-configuration)
- [Health Checks](#health-checks)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Software

- **Java 24** (or compatible JDK)
- **Maven 3.9+** (or use included Maven wrapper `./mvnw`)
- **Docker** (for containerized deployment)
- **Docker Compose** (for multi-container deployment)

### Optional Tools

- **PostgreSQL 16** (if running without Docker)
- **Make** (for using Makefile commands)
- **curl** (for API testing)
- **jq** (for formatted JSON output)

## Quick Start

### Option 1: Using Makefile (Recommended)

```bash
# Show all available commands
make help

# Build everything
make build

# Run with H2 (development)
make run-h2

# Run with PostgreSQL (production-like)
make run-postgres
```

### Option 2: Using Shell Scripts

```bash
# Make scripts executable
chmod +x *.sh

# Build application and Docker image
./build.sh

# Run with H2 database
./run-h2.sh

# Run with PostgreSQL using Docker
./run-postgres.sh
```

## Development Deployment

### H2 In-Memory Database (Development Mode)

**Using Makefile:**
```bash
make run-h2
```

**Using Script:**
```bash
./run-h2.sh
```

**Manual:**
```bash
./mvnw spring-boot:run
```

**Access Points:**
- Application: http://localhost:8080
- H2 Console: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:urlshortenerdb`
  - Username: `sa`
  - Password: _(leave empty)_

### Features in Development Mode

- ✅ Fast startup
- ✅ No external dependencies
- ✅ H2 web console enabled
- ✅ SQL logging enabled
- ✅ Auto-reload with Spring Boot DevTools

## Production Deployment

### PostgreSQL Database (Production Mode)

**Using Docker Compose (Recommended):**
```bash
# Start all services
make run-postgres
# or
./run-postgres.sh

# View logs
make docker-logs

# Stop services
make docker-down
```

**Manual Setup:**

1. **Start PostgreSQL:**
```bash
# Install PostgreSQL 16
# Create database
createdb urlshortener

# Or using psql
psql -U postgres -c "CREATE DATABASE urlshortener;"
```

2. **Configure application:**
```bash
# Copy environment template
cp .env.example .env

# Edit .env with your settings
vim .env
```

3. **Run application:**
```bash
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
```

## Docker Deployment

### Building Docker Image

**Using Makefile:**
```bash
make docker-build
```

**Using Script:**
```bash
./build.sh
```

**Manual:**
```bash
docker build -t url-shortener:latest .
```

### Multi-Stage Build Benefits

- ✅ Optimized for layer caching
- ✅ Small production image (~200MB)
- ✅ Security: runs as non-root user
- ✅ Health checks included
- ✅ JVM tuned for containers

### Running with Docker Compose

**Start all services:**
```bash
docker-compose up -d
```

**View logs:**
```bash
docker-compose logs -f app
```

**Stop services:**
```bash
docker-compose down
```

**Stop and remove volumes:**
```bash
docker-compose down -v
```

### Docker Compose Services

1. **postgres** - PostgreSQL 16 database
2. **app** - URL Shortener application
3. **pgadmin** (optional) - Database management UI

**Start with pgAdmin:**
```bash
docker-compose --profile tools up -d
# Access at http://localhost:5050
# Login: admin@urlshortener.com / admin
```

## Environment Configuration

### Environment Variables

Create `.env` from template:
```bash
cp .env.example .env
```

### Key Configuration Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `postgres` |
| `SPRING_DATASOURCE_URL` | Database connection URL | See .env.example |
| `URL_SHORTENER_BASE_URL` | Base URL for short links | `http://localhost:8080` |
| `URL_SHORTENER_CODE_LENGTH` | Length of short codes | `7` |
| `URL_SHORTENER_RATE_LIMIT_ENABLED` | Enable rate limiting | `true` |
| `JAVA_OPTS` | JVM options | See .env.example |

### Spring Profiles

| Profile | Database | Use Case |
|---------|----------|----------|
| `default` | H2 (in-memory) | Development |
| `postgres` | PostgreSQL | Production |
| `test` | H2 (in-memory) | Testing |

## Health Checks

### Using Health Check Script

```bash
# Check if application is healthy
./health-check.sh

# With custom settings
HOST=example.com PORT=8080 ./health-check.sh
```

### Manual Health Check

```bash
curl http://localhost:8080/actuator/health
```

**Expected Response:**
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

### Monitoring Endpoints

- **Health**: `http://localhost:8080/actuator/health`
- **Metrics**: `http://localhost:8080/actuator/metrics`
- **Info**: `http://localhost:8080/actuator/info`
- **Prometheus**: `http://localhost:8080/actuator/prometheus`

## Testing the Deployment

### Create a Short URL

```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://www.example.com"}'
```

**Expected Response:**
```json
{
  "code": "abc123D",
  "longUrl": "https://www.example.com",
  "shortUrl": "http://localhost:8080/r/abc123D",
  "createdAt": "2026-01-29T10:30:00Z",
  "expiresAt": null
}
```

### Access Short URL

```bash
curl -L http://localhost:8080/r/abc123D
```

### Using Makefile Test Commands

```bash
# Quick create URL test
make test-create-url

# Health check test
make test-health
```

## Production Deployment Checklist

### Security

- [ ] Change default passwords in `.env`
- [ ] Enable HTTPS/TLS
- [ ] Configure firewall rules
- [ ] Use secrets management (Vault, AWS Secrets Manager, etc.)
- [ ] Disable H2 console
- [ ] Configure CORS for specific origins
- [ ] Review and harden security settings

### Performance

- [ ] Adjust JVM heap size based on available memory
- [ ] Configure connection pool sizes
- [ ] Set up database connection pooling
- [ ] Enable caching if needed
- [ ] Configure appropriate timeout values

### Monitoring

- [ ] Set up log aggregation (ELK, Splunk, etc.)
- [ ] Configure metrics collection (Prometheus)
- [ ] Set up alerting (PagerDuty, OpsGenie, etc.)
- [ ] Enable application performance monitoring (APM)
- [ ] Configure health check endpoints

### High Availability

- [ ] Run multiple application instances
- [ ] Use load balancer (Nginx, HAProxy, AWS ALB)
- [ ] Set up database replication
- [ ] Configure auto-scaling
- [ ] Implement circuit breakers

### Backup & Recovery

- [ ] Set up automated database backups
- [ ] Test restore procedures
- [ ] Document disaster recovery plan
- [ ] Configure retention policies

## Troubleshooting

### Application Won't Start

**Check Java version:**
```bash
java -version  # Should be 24 or compatible
```

**Check if port is in use:**
```bash
lsof -i :8080
# or
netstat -an | grep 8080
```

**View application logs:**
```bash
# Local
tail -f logs/url-shortener.log

# Docker
docker-compose logs -f app
```

### Database Connection Issues

**Check PostgreSQL is running:**
```bash
docker-compose ps postgres
```

**Test connection:**
```bash
psql -h localhost -U postgres -d urlshortener
```

**Check connection pool:**
```bash
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

### Docker Issues

**Check Docker is running:**
```bash
docker info
```

**Rebuild image:**
```bash
docker-compose build --no-cache
```

**Remove all containers and volumes:**
```bash
docker-compose down -v
docker system prune -a
```

### Performance Issues

**Check memory usage:**
```bash
docker stats url-shortener-app
```

**Check JVM metrics:**
```bash
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

**Increase heap size:**
```bash
# In .env
JAVA_OPTS=-Xms1g -Xmx2g
```

## Useful Commands

### Makefile Commands

```bash
make build              # Build application and Docker image
make test               # Run all tests
make run-h2             # Run with H2
make run-postgres       # Run with PostgreSQL
make docker-logs        # View Docker logs
make docker-down        # Stop services
make health-check       # Check application health
make clean              # Clean everything
make show-config        # Show current configuration
```

### Docker Commands

```bash
# View running containers
docker-compose ps

# View logs
docker-compose logs -f app

# Restart service
docker-compose restart app

# Execute command in container
docker-compose exec app sh

# View resource usage
docker stats

# Remove everything
docker-compose down -v
```

### Database Commands

```bash
# Connect to PostgreSQL
docker-compose exec postgres psql -U postgres -d urlshortener

# Backup database
docker-compose exec postgres pg_dump -U postgres urlshortener > backup.sql

# Restore database
docker-compose exec -T postgres psql -U postgres -d urlshortener < backup.sql
```

## Additional Resources

- **API Documentation**: http://localhost:8080/swagger-ui.html
- **Actuator Endpoints**: http://localhost:8080/actuator
- **Rate Limiting**: See `RATE_LIMITING_IMPLEMENTATION.md`
- **Source Code**: https://github.com/your-org/url-shortener

## Support

For issues or questions:
- Check logs: `make docker-logs`
- Review health status: `make health-check`
- Consult documentation in `docs/` directory
- Open an issue on GitHub

---

**Last Updated**: 2026-01-29
**Version**: 1.0.0
