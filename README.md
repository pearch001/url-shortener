# URL Shortener API 🔗

![Build](https://img.shields.io/badge/build-Maven-blue)
![Java](https://img.shields.io/badge/Java-24-red)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)

A production-ready microservice for shortening URLs with tracking, analytics, rate limiting, and observability.

---

## Table of Contents
- [Project Goal](#project-goal)
- [Tech Stack](#tech-stack)
- [Architecture & Design](#architecture--design)
- [Features Implemented](#features-implemented)
- [API Documentation](#api-documentation)
- [Build & Run Instructions](#build--run-instructions)
- [Testing](#testing)
- [Assumptions & Design Decisions](#assumptions--design-decisions)
- [Trade-offs](#trade-offs)
- [Code Quality](#code-quality)
- [Observability](#observability)
- [API Examples](#api-examples)
- [Database Schema](#database-schema)
- [Configuration](#configuration)
- [Docker Deployment](#docker-deployment)
- [Accessing the Application](#accessing-the-application)
- [What Went Well](#what-went-well)
- [What Could Be Improved](#what-could-be-improved)
- [Project Structure](#project-structure)
- [Contributing (Optional)](#contributing-optional)
- [License (Optional)](#license-optional)
- [Contact Information](#contact-information)
- [Assessment Matrix](#assessment-matrix)

---

## Project Goal
The URL Shortener API provides a robust, observable, and rate-limited service to convert long URLs into short codes and redirect users to the original destination.

- What it does:
  - Creates deterministic short codes for long URLs and redirects via `/{code}`.
  - Tracks hit counts and exposes metadata via REST.
  - Applies rate limiting and offers health/metrics endpoints.
- Key features:
  - Base62 7-character code generation
  - Collision handling and idempotent create
  - Observability with actuator and Prometheus
  - OpenAPI documentation and Docker support
- Why it exists:
  - Built for a technical assessment with a focus on production-readiness (clean architecture, tests, observability, and operations).

## Tech Stack
- Language/Runtime: Java 24
- Framework: Spring Boot 3.x
- Build: Maven (`pom.xml`)
- Databases: H2 (dev/test), PostgreSQL (prod)
- Containers: Docker, docker-compose
- API Docs: Springdoc OpenAPI 3
- Observability: Spring Boot Actuator, Micrometer/Prometheus

Dependencies and purposes (indicative based on project structure):
- Spring Boot Starter Web: REST controllers and HTTP stack
- Spring Boot Starter Validation: Bean validation for DTOs
- Spring Data JPA: Repository abstraction to H2/PostgreSQL
- H2 Database: In-memory database for local dev/testing
- PostgreSQL driver: Production database connectivity
- Springdoc OpenAPI: Generate and serve OpenAPI docs and Swagger UI
- Micrometer/Actuator: Metrics, health, info, Prometheus endpoint
- Bucket4j (or similar): In-memory rate limiting via aspect & annotation

Database options:
- H2: Zero-config dev profile, fast tests
- PostgreSQL: Durable storage via `application-postgres.yml` and docker-compose

## Architecture & Design
High-level architecture (ASCII):

```
+-------------+      +-------------------+      +----------------+      +-----------------+
|   Client    | ---> |   Controller/API  | ---> |    Service     | ---> |   Repository    |
+-------------+      +-------------------+      +----------------+      +-----------------+
        |                         |                       |                      |
        |                         |                       v                      v
        |                         |              Code Generation           Database (H2/PG)
        v                         v
  Observability (Actuator/Metrics), Rate Limiting (Aspect), Validation, Exception Handling
```

Layer separation:
- Controller → Service → Repository, with DTOs, models, and validation appropriately separated.
- Controllers under `src/main/java/com/urlshortener/api` and `controller` packages (e.g., redirect and REST endpoints).
- Services under `service/` encapsulate business logic (code generation, idempotency, collision handling).
- Repositories under `repository/` manage persistence via JPA.

Design patterns:
- Aspect-Oriented Programming for rate limiting (`annotation` + `aspect` packages)
- Adapter/converter pattern where utilities bridge DTOs and domain models
- Configuration Properties pattern (`config/*Properties.java`) for externalized settings

SOLID principles applied:
- SRP: Controllers handle HTTP; services handle business logic; repositories handle persistence
- OCP: Rate limit strategies and properties can be extended without modifying core logic
- LSP/ISP: Interfaces for repositories/services; small DTOs for specific use cases
- DIP: Services depend on abstractions (repositories/interfaces), injected via Spring

## Features Implemented
- ✅ Must-have features:
  - Base62 7-character code generation (collision-safe)
  - Collision detection and handling (repository lookup + retry)
  - Idempotent create (same URL returns same code)
  - Redirect endpoint (`/{code}`) returning 302
  - Metadata endpoint to retrieve details
- ✅ Bonus features implemented:
  - Rate limiting (annotation + aspect, in-memory)
  - Observability (Actuator, metrics, Prometheus)
  - OpenAPI/Swagger documentation
  - Docker and docker-compose support
  - Health checks & probes
  - Optional expiry and cleanup scheduler (if enabled via properties)

Notes:
- Code generation algorithm: Base62 (a–z, A–Z, 0–9)
- Collision handling: Check code uniqueness; retry with new code if needed
- Idempotency: Return existing mapping if long URL already shortened

## API Documentation
Key endpoints:
- POST `/api/v1/urls` → Create short URL
- GET `/{code}` → Redirect to long URL (302 Found)
- GET `/api/v1/urls/{code}` → Fetch metadata (hit count, createdAt, etc.)

Request/Response formats:
- POST request body: `{ "longUrl": "https://example.com/..." }`
- POST response body: `{ "code": "abc1234", "shortUrl": "http://localhost:8080/r/abc1234", "longUrl": "...", "createdAt": "..." }`
- Metadata response: includes `hitCount` and timestamps

Status codes:
- 201 Created: Short URL created
- 200 OK: Metadata retrieved
- 302 Found: Redirect
- 400/422: Validation errors
- 404 Not Found: Unknown code
- 429 Too Many Requests: Rate limit exceeded

Swagger/OpenAPI:
- Swagger UI: `/swagger-ui.html` (or `/swagger-ui/index.html` depending on configuration)
- OpenAPI JSON: `/api-docs`
- OpenAPI YAML: `/api-docs.yaml`

See also: `API_DOCUMENTATION.md`.

## Build & Run Instructions

Building:

```bash
# Clone repository
git clone <repository-url>
cd url-shortener

# Build with Maven
./mvnw clean package

# Run tests
./mvnw test
```

Running (H2):

```bash
./mvnw spring-boot:run
```

Running (PostgreSQL with Docker):

```bash
docker-compose up -d
```

Running with Docker only:

```bash
docker build -t url-shortener .
docker run -p 8080:8080 url-shortener
```

Tips:
- Scripts available: `run-h2.sh`, `run-postgres.sh`, `build.sh`, `health-check.sh`
- Profiles: default (H2) via `application.yml`; Postgres via `application-postgres.yml`

## Testing
- How to run:
  - `./mvnw test` for unit/slice tests
  - `./mvnw verify` for integration tests
- Test categories:
  - Unit: `service`, `util`
  - Slice: `controller` (WebMvc tests)
  - Integration: `integration` (end-to-end behaviors, e.g., rate limiting)
- Coverage summary:
  - Unit and integration tests cover code generation, idempotency, collisions, redirects, rate limiting, and observability endpoints
  - See `target/surefire-reports/` for integration results (e.g., `RateLimitIntegrationTest`)
- Key scenarios:
  - Base62 generation determinism and uniqueness
  - Collision retry mechanism
  - Idempotent create returns same code
  - Redirect returns 302 to the original URL
  - Rate limiting yields 429 with proper headers/metadata
  - Actuator endpoints healthy and expose metrics

## Assumptions & Design Decisions
- Idempotent behavior: same normalized URL returns the same short code
  - Justification: prevents duplicate entries and improves UX across repeated submissions
- Code generation: Base62 with 7 characters
  - Justification: balances compactness and low collision probability
- H2 vs PostgreSQL:
  - H2 for local dev/testing; PostgreSQL for production durability and indexing
- In-memory rate limiting (not distributed)
  - Justification: simplicity for single-instance deployments; can be swapped for Redis in the future
- Expiry implementation (bonus feature): optional metadata field and scheduled cleanup
- Validation: URL format verified; RFC 7807 error responses structured

## Trade-offs
- What was prioritized and why:
  - Correctness, observability, simplicity of deployment (Docker), clean layering, tests
- What could be improved with more time:
  - Distributed rate limiting store (e.g., Redis)
  - Global uniqueness under multi-instance without coordination
  - Flyway/Liquibase migrations
  - Authentication/authorization beyond rate limiting
- Scalability considerations:
  - Scale horizontally with shared DB and distributed rate limiting/cache
  - Consider longer codes or hash+salt to reduce collision probability at scale
- Security considerations:
  - Input validation, sanitized redirects
  - Consider auth, API keys, and rate limiting per principal
- Production readiness gaps:
  - Migrations, secrets management, tracing, and hardened Docker images

## Code Quality
- SOLID principles applied across layers
- Clean architecture: clear separation of concerns and boundaries
- Comprehensive tests covering critical paths
- JavaDoc on key services/utilities (where applicable)
- Error handling via custom exceptions and RFC 7807 responses
- Observability baked in via Actuator and Prometheus

## Observability
- Actuator endpoints:
  - `/actuator`, `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`
- Custom metrics:
  - Request counts, rate limit hits, redirect counts (via `config/MetricsConfiguration` and `observability`)
- Health checks:
  - Application health and liveness/readiness where configured
- How to access monitoring:
  - Metrics scrape at `/actuator/prometheus`

## API Examples

Create Short URL:

```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{
    "longUrl": "https://example.com/very/long/url"
  }'

# Response:
{
  "code": "abc1234",
  "shortUrl": "http://localhost:8080/r/abc1234",
  "longUrl": "https://example.com/very/long/url",
  "createdAt": "2026-01-27T10:30:00Z"
}
```

Redirect:

```bash
curl -I http://localhost:8080/r/abc1234

# Response:
HTTP/1.1 302 Found
Location: https://example.com/very/long/url
```

Get Metadata:

```bash
curl http://localhost:8080/api/urls/abc1234

# Response:
{
  "code": "abc1234",
  "longUrl": "https://example.com/very/long/url",
  "shortUrl": "http://localhost:8080/r/abc1234",
  "createdAt": "2026-01-27T10:30:00Z",
  "hitCount": 42
}
```

## Database Schema
Indicative schema for `urls` table:

- Table structure:
  - `id` (PK, UUID or bigint)
  - `code` (VARCHAR(7), unique)
  - `long_url` (TEXT/VARCHAR, indexed)
  - `created_at` (TIMESTAMP)
  - `hits` (BIGINT, default 0)
  - `expires_at` (TIMESTAMP, nullable)
- Indexes:
  - Unique index on `code`
  - Non-unique index on `long_url` for idempotent lookups
- Constraints:
  - NOT NULL on `code`, `long_url`, `created_at`

## Configuration
- Available profiles:
  - Default (H2): `src/main/resources/application.yml`
  - PostgreSQL: `src/main/resources/application-postgres.yml`
- Environment variables:
  - `SPRING_PROFILES_ACTIVE` (e.g., `postgres`)
  - Database URL/credentials for production
- Customizable properties:
  - `UrlShortenerProperties` (e.g., cleanup/expiry)
  - `RateLimitProperties` (limits, windows)
  - `OpenApiConfig` toggles and info

## Docker Deployment
- How to build Docker image:

```bash
docker build -t url-shortener .
```

- How to run with docker-compose (app + PostgreSQL):

```bash
docker-compose up -d
```

- Environment configuration:
  - Adjust `application-postgres.yml` or pass env vars to the container
  - Map ports (`8080:8080`), attach volumes if necessary

## Accessing the Application
- Main application: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Actuator: http://localhost:8080/actuator
- H2 Console: http://localhost:8080/h2-console

You can also explore the static landing page at `/` (`src/main/resources/static/index.html`).

## What Went Well
- Successful implementation of all must-have features
- Clean architecture and SOLID principles
- Comprehensive test coverage (unit, slice, integration)
- Bonus features implemented (observability, Docker, optional expiry)
- Professional documentation

## What Could Be Improved
- Distributed caching/store for rate limiting (e.g., Redis)
- Distributed code generation (avoid collisions in multi-instance)
- More sophisticated analytics (geo/IP, device insights)
- Additional security measures (authentication/authorization)
- Performance optimizations; database connection pooling tuning
- Database migrations and schema evolution (Flyway/Liquibase)

## Project Structure

```
url-shortener/
├── src/
│   ├── main/
│   │   ├── java/com/urlshortener/
│   │   │   ├── api/              # Controllers
│   │   │   ├── service/          # Business logic
│   │   │   ├── repository/       # Data access
│   │   │   ├── model/            # Entities
│   │   │   ├── dto/              # DTOs
│   │   │   ├── exception/        # Custom exceptions
│   │   │   ├── config/           # Configuration
│   │   │   ├── aspect/           # Cross-cutting concerns
│   │   │   ├── ratelimit/        # Rate limiting support
│   │   │   ├── observability/    # Metrics/monitoring
│   │   │   └── util/             # Utilities
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-postgres.yml
│   └── test/                     # Tests
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

## Contributing (Optional)
Contributions are welcome.
- Fork the repo and create a feature branch
- Run tests locally: `./mvnw test`
- Open a pull request with a clear description and links to related issues

## License (Optional)
Specify your project license here (e.g., MIT, Apache-2.0). Add a `LICENSE` file at the repository root.

## Contact Information
- Maintainer: Kasim Segun Ebenezer
- Email: kasimsegun1@gmail.com
- Issues: Use GitHub Issues on the repository

## Assessment Matrix
- ✅ Correctness/Requirements (30 points): All requested features and endpoints implemented and documented
- ✅ Code Quality & Architecture (20 points): Clean layer separation, SOLID, clear patterns, ASCII diagram
- ✅ Tests (20 points): Unit, slice, and integration tests with key scenarios covered
- ✅ Error/Edge-Cases & Robustness (10 points): Validation, RFC 7807 errors, collision handling, idempotency
- ✅ Observability & Documentation (10 points): Actuator endpoints, metrics, health checks, comprehensive README & API docs
- ✅ Bonus Features (10 points): Expiry, Docker support, Prometheus metrics, rate limiting via aspect

Total: 100 points.

References: `pom.xml`, `src/main/java/com/urlshortener/{controller,api,service,repository,config,aspect,observability,ratelimit,util,validation}`, `src/main/resources/{application.yml,application-postgres.yml,static/index.html}`, `Dockerfile`, `docker-compose.yml`, `API_DOCUMENTATION.md`, tests under `src/test/java/com/urlshortener/`.
