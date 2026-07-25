# Log Intelligence Platform

A Spring Boot REST API for centralised structured log ingestion and retrieval, backed by PostgreSQL.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.x |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL |
| Validation | Jakarta Bean Validation (Hibernate Validator) |
| Build | Maven |

---

## Project Structure

```
src/
└── main/
│   ├── java/com/logplatform/
│   │   ├── LogIntelligencePlatformApplication.java   ← Entry point
│   │   ├── controller/
│   │   │   └── LogEntryController.java               ← REST endpoints
│   │   ├── exception/
│   │   │   └── GlobalExceptionHandler.java           ← Validation error responses
│   │   ├── model/
│   │   │   └── LogEntry.java                         ← JPA entity / DB table
│   │   └── repository/
│   │       └── LogEntryRepository.java               ← Spring Data repository
│   └── resources/
│       └── application.properties                    ← DB + JPA config
└── test/
    └── java/com/logplatform/
        └── LogIntelligencePlatformApplicationTests.java
```

---

## Setup

### 1. Configure PostgreSQL

Edit `src/main/resources/application.properties` and replace the placeholders:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/logdb
spring.datasource.username=postgres
spring.datasource.password=yourpassword
```

### 2. Create the database (first time only)

```sql
CREATE DATABASE logdb;
```

Hibernate will auto-create the `log_entries` table on first startup (`ddl-auto=update`).

### 3. Build & Run

```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

The API is available at `http://localhost:8080`.

---

## API Reference

### `POST /logs` — Ingest a log entry

**Request body** (JSON):

```json
{
  "serviceName": "auth-service",
  "logLevel": "ERROR",
  "message": "Failed to authenticate user: token expired",
  "timestamp": "2024-01-15T10:30:00"
}
```

> `timestamp` is optional — defaults to the current server time if omitted.

**Response** `201 Created`:

```json
{
  "id": 1,
  "serviceName": "auth-service",
  "logLevel": "ERROR",
  "message": "Failed to authenticate user: token expired",
  "timestamp": "2024-01-15T10:30:00"
}
```

**Validation error** `400 Bad Request` (when a required field is blank):

```json
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 400,
  "errors": ["serviceName: serviceName must not be blank"]
}
```

---

### `GET /logs` — Retrieve all log entries

**Response** `200 OK`:

```json
[
  {
    "id": 1,
    "serviceName": "auth-service",
    "logLevel": "ERROR",
    "message": "Failed to authenticate user: token expired",
    "timestamp": "2024-01-15T10:30:00"
  }
]
```

---

## Validation Rules

| Field | Constraint |
|---|---|
| `serviceName` | Must not be blank |
| `logLevel` | Must not be blank (recommended values: INFO, WARN, ERROR, DEBUG, TRACE) |
| `message` | Must not be blank |
| `timestamp` | Optional; auto-set to current time if omitted |