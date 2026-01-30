# API Documentation

This document describes how to access the OpenAPI documentation and Swagger UI for the URL Shortener API.

## Swagger UI (Interactive Documentation)

Access the interactive API documentation at:
```
http://localhost:8080/swagger-ui.html
```

The Swagger UI provides:
- **Try It Out**: Execute API calls directly from the browser
- **Request/Response Examples**: Pre-filled examples for all endpoints
- **Schema Documentation**: Detailed descriptions of all DTOs
- **Response Codes**: Complete documentation of all HTTP status codes

## OpenAPI Specification

### JSON Format
```
http://localhost:8080/api-docs
```

### YAML Format
```
http://localhost:8080/api-docs.yaml
```

## API Groups

The API is organized into two groups:

### 1. Public API (`/api/**`, `/r/**`)
- **URL Management**: Create, read, delete short URLs
- **URL Redirection**: Redirect short codes to original URLs
- **Statistics**: Get access counts and analytics

### 2. Actuator (`/actuator/**`)
- **Health Check**: `/actuator/health`
- **Metrics**: `/actuator/metrics`
- **Prometheus**: `/actuator/prometheus`
- **Info**: `/actuator/info`

## Welcome Page

A custom welcome page with links to all documentation is available at:
```
http://localhost:8080/
```

## API Endpoints Summary

### URL Shortener (Legacy)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/shorten` | Create a short URL |
| GET | `/r/{shortCode}` | Redirect to original URL |
| GET | `/api/stats/{shortCode}` | Get URL statistics |
| DELETE | `/api/urls/{shortCode}` | Delete a short URL |

### URL Redirection (Optimized)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/r/{code}` | Redirect to original URL (optimized path) |
| GET | `/r/health` | Health check for redirect service |

## Rate Limiting

The API implements rate limiting on the following endpoints:
- `POST /api/shorten`: 10 requests per minute per IP

Rate limit headers are included in responses:
- `X-RateLimit-Limit`: Maximum requests allowed
- `X-RateLimit-Remaining`: Requests remaining in window
- `X-RateLimit-Reset`: Seconds until limit resets

## Error Handling

All errors follow RFC 7807 Problem Details format:

```json
{
    "type": "about:blank",
    "title": "Bad Request",
    "status": 400,
    "detail": "Invalid URL format",
    "instance": "/api/shorten"
}
```

Common error codes:
- `400 Bad Request`: Validation failed
- `404 Not Found`: Short URL not found
- `409 Conflict`: Custom alias already exists
- `410 Gone`: URL has expired
- `429 Too Many Requests`: Rate limit exceeded

## Configuration

Springdoc configuration in `application.yml`:

```yaml
springdoc:
  api-docs:
    path: /api-docs
    enabled: true
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
    operations-sorter: alpha
    tags-sorter: alpha
    display-request-duration: true
    try-it-out-enabled: true
  show-actuator: true
```

## Development

### H2 Database Console
During development with H2 database:
```
http://localhost:8080/h2-console
```

Connection settings:
- JDBC URL: `jdbc:h2:mem:urlshortenerdb`
- Username: `sa`
- Password: (empty)

## Quick Start Examples

### Create a Short URL
```bash
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/very/long/url"}'
```

### Create with Custom Alias
```bash
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com/very/long/url",
    "customAlias": "my-link",
    "expirationDays": 30
  }'
```

### Get URL Statistics
```bash
curl http://localhost:8080/api/stats/abc123d
```

### Delete a Short URL
```bash
curl -X DELETE http://localhost:8080/api/urls/abc123d
```
