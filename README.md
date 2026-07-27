# Log Intelligence Platform

> AI-powered log intelligence platform that clusters application errors and generates plain-English root-cause summaries.

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-316192?logo=postgresql)](https://www.postgresql.org/)
[![Groq](https://img.shields.io/badge/Groq-Llama%203.3-F55036)](https://console.groq.com/)

---

## Problem Statement

Modern distributed systems generate thousands of log lines per minute. When something goes wrong, engineers face a wall of raw text — searching for patterns across services, log levels, and time windows manually is slow, error-prone, and exhausting.

**The result:** incidents take longer to diagnose, on-call engineers burn out faster, and subtle recurring errors go unnoticed until they become outages.

Log Intelligence Platform solves this by:
- **Automatically grouping** log events into meaningful clusters (same service + severity + time window)
- **Filtering the noise** — only surfacing WARN and ERROR patterns, not thousands of INFO lines
- **Generating plain-English summaries** of each cluster using an LLM, so engineers immediately understand *what* is happening and *how urgent* it is — no manual log spelunking required

---

## Demo

![Dashboard Demo](screenshots/demo.gif)

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        Client / Service                          │
│              (your microservices, LogGenerator tool)             │
└────────────────────────────┬─────────────────────────────────────┘
                             │  POST /logs  (JSON)
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                    Spring Boot REST API                          │
│                    LogEntryController                            │
└────────────────────────────┬─────────────────────────────────────┘
                             │  JPA / Hibernate
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                      PostgreSQL Database                         │
│                    (log_entries table)                           │
└────────────────────────────┬─────────────────────────────────────┘
                             │  findAll()
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                    LogClusterService                             │
│   Filter WARN/ERROR → Group by service + level + 10-min bucket  │
│              → Sort by count descending                          │
└────────────────────────────┬─────────────────────────────────────┘
                             │  LogClusterResult list
                             ▼
┌──────────────────────────────────────────────────────────────────┐
│                       GroqService                                │
│     Build SRE prompt → POST to Groq API (Llama 3.3 70B)        │
│              → Parse AI response → aiSummary                    │
└────────────────────────────┬─────────────────────────────────────┘
                             │  JSON response
                             ▼
                    GET /logs/clusters/summary
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| ORM | Spring Data JPA + Hibernate 6 |
| Database | PostgreSQL |
| AI Model | Groq API — `llama-3.3-70b-versatile` |
| HTTP Client | JDK built-in `java.net.http.HttpClient` |
| Build | Maven (via `mvnw` wrapper — no local Maven install needed) |

---

## Key Features

- **Log Ingestion API** — Accept structured log events (service name, level, message, timestamp) via a validated REST endpoint. Timestamps default to server time if omitted.

- **Time-Window Clustering** — Groups WARN and ERROR events by service, log level, and 10-minute time buckets. Returns clusters sorted by event count, so the highest-volume incidents surface first.

- **AI-Generated Root-Cause Summaries** — Each cluster is passed to the Groq LLM with an SRE-flavoured prompt. The model returns a 1-2 sentence plain-English explanation of the likely cause and urgency.

- **Graceful AI Failure Handling** — If the Groq API is unavailable (rate limit, bad key, network error), individual clusters fall back to `"Summary unavailable"` instead of crashing the entire endpoint. The API always returns `200 OK`.

- **Seed Data Generator** — A standalone `LogGenerator` tool generates 50 realistic fake log entries across 4 services and POSTs them to the running API, making it easy to populate the database and explore clustering immediately.

---

## Setup

### Prerequisites

- **JDK 17+** — [Download OpenJDK](https://adoptium.net/)  
  *(The project ships with a Maven Wrapper — no separate Maven installation required)*
- **PostgreSQL** — running locally or remotely
- **Groq API key** — [Get a free key](https://console.groq.com/keys)

---

### 1. Clone the repository

```bash
git clone https://github.com/albyantony25-jpg/log-intelligence-platform.git
cd log-intelligence-platform
```

### 2. Create the PostgreSQL database

```sql
CREATE DATABASE log_intelligence;
```

Hibernate will auto-create the `log_entries` table on first startup.

### 3. Configure the application

Edit `src/main/resources/application.properties` and fill in your PostgreSQL credentials:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/log_intelligence
spring.datasource.username=YOUR_POSTGRES_USERNAME
spring.datasource.password=YOUR_POSTGRES_PASSWORD
```

### 4. Set the Groq API key

The API key is read from an environment variable to keep secrets out of source control.

**Windows (PowerShell):**
```powershell
$env:GROQ_API_KEY = "gsk_your_key_here"
```

**Linux / macOS:**
```bash
export GROQ_API_KEY="gsk_your_key_here"
```

> **Note:** If `GROQ_API_KEY` is not set, the app starts normally but `GET /logs/clusters/summary` will return `"Summary unavailable"` for every cluster instead of AI-generated text.

### 5. Run the application

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

The API is available at **`http://localhost:8081`**.

### 6. (Optional) Seed fake log data

```bash
# Windows
.\mvnw.cmd exec:java "-Dexec.mainClass=com.logplatform.tools.LogGenerator"

# Linux / macOS
./mvnw exec:java -Dexec.mainClass="com.logplatform.tools.LogGenerator"
```

This sends 50 randomised log entries across 4 services to the running API.

---

## Run with Docker

> **No local Java, Maven, or PostgreSQL installation required.**  
> Docker Compose handles everything — the app is compiled inside the build container.

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (includes Docker Compose)
- A Groq API key — [get one free](https://console.groq.com/keys)

### 1. Set the Groq API key on your host

**Windows (PowerShell):**
```powershell
$env:GROQ_API_KEY = "gsk_your_key_here"
```

**Linux / macOS:**
```bash
export GROQ_API_KEY="gsk_your_key_here"
```

### 2. Start everything

```bash
docker compose up --build
```

Docker Compose will:
1. Pull the `postgres:16` image and start the database
2. Wait for PostgreSQL to pass its health check
3. Build the Spring Boot app image (compiles the JAR inside Docker — no local JDK needed)
4. Start the app once the database is ready

The API is available at **`http://localhost:8081`** once you see:

```
log_intelligence_app  | Started LogIntelligencePlatformApplication in X.XXX seconds
```

### 3. (Optional) Seed fake log data

While the stack is running, open a second terminal and run:

```bash
docker compose exec app \
  java -cp app.jar \
  -Dloader.main=com.logplatform.tools.LogGenerator \
  org.springframework.boot.loader.launch.PropertiesLauncher
```

Or simply start your local `LogGenerator` against the exposed port — the API is reachable at `http://localhost:8081` from your host machine.

### Useful Docker commands

| Command | Description |
|---|---|
| `docker compose up --build -d` | Start in detached (background) mode |
| `docker compose logs -f app` | Stream live app logs |
| `docker compose logs -f postgres` | Stream live database logs |
| `docker compose down` | Stop and remove containers (data volume preserved) |
| `docker compose down -v` | Stop containers **and wipe the database volume** |
| `docker compose build --no-cache` | Force a full image rebuild |

### Services and ports

| Service | Host port | Description |
|---|---|---|
| `app` | `8081` | Spring Boot REST API |
| `postgres` | `5433` | PostgreSQL (mapped to avoid conflict with local Postgres on 5432) |

---

## API Endpoints

### `POST /logs` — Ingest a log entry

| | |
|---|---|
| **Method** | `POST` |
| **Path** | `/logs` |
| **Description** | Accepts a structured log event, validates it, persists it to PostgreSQL, and returns the saved entry with its auto-generated ID and timestamp. |

**Request body:**
```json
{
  "serviceName": "payment-service",
  "logLevel":    "ERROR",
  "message":     "Payment gateway timeout for transaction txn_4823 after 4200ms",
  "timestamp":   "2024-01-15T10:03:00"
}
```
> `timestamp` is optional — defaults to the current server time if omitted.

**Response `201 Created`:**
```json
{
  "id":          1,
  "serviceName": "payment-service",
  "logLevel":    "ERROR",
  "message":     "Payment gateway timeout for transaction txn_4823 after 4200ms",
  "timestamp":   "2024-01-15T10:03:00"
}
```

**Validation error `400 Bad Request`** (blank required field):
```json
{
  "timestamp": "2024-01-15T10:03:00",
  "status":    400,
  "errors":    ["serviceName: serviceName must not be blank"]
}
```

---

### `GET /logs` — Retrieve all log entries

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/logs` |
| **Description** | Returns all stored log entries as a JSON array. |

**Response `200 OK`:**
```json
[
  {
    "id":          1,
    "serviceName": "payment-service",
    "logLevel":    "ERROR",
    "message":     "Payment gateway timeout for transaction txn_4823 after 4200ms",
    "timestamp":   "2024-01-15T10:03:00"
  }
]
```

---

### `GET /logs/clusters` — Retrieve WARN/ERROR clusters

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/logs/clusters` |
| **Description** | Groups WARN and ERROR log entries by `serviceName`, `logLevel`, and 10-minute time bucket. Returns clusters sorted by event count descending. INFO entries are excluded. |

**Response `200 OK`:**
```json
[
  {
    "serviceName":     "payment-service",
    "logLevel":        "ERROR",
    "timeBucketStart": "2024-01-15T10:00:00",
    "count":           4,
    "sampleMessages": [
      "Payment gateway timeout for transaction txn_4823 after 4200ms",
      "Charge declined for user usr_342 — insufficient funds (txn_5091)",
      "Payment processor returned error code 503 for txn_6617"
    ]
  }
]
```

---

### `GET /logs/clusters/summary` — AI-summarised clusters

| | |
|---|---|
| **Method** | `GET` |
| **Path** | `/logs/clusters/summary` |
| **Description** | Same as `/logs/clusters` but each cluster is enriched with an `aiSummary` field — a 1-2 sentence plain-English root-cause explanation generated by Groq's Llama 3.3 70B model. Falls back to `"Summary unavailable"` if the Groq API is unreachable. |

**Response `200 OK`:**
```json
[
  {
    "serviceName":     "payment-service",
    "logLevel":        "ERROR",
    "timeBucketStart": "2024-01-15T10:00:00",
    "count":           4,
    "sampleMessages": [
      "Payment gateway timeout for transaction txn_4823 after 4200ms",
      "Charge declined for user usr_342 — insufficient funds (txn_5091)",
      "Payment processor returned error code 503 for txn_6617"
    ],
    "aiSummary": "The payment-service is experiencing repeated failures connecting to the
                  payment gateway, likely due to an upstream provider outage or network
                  instability — this is high urgency as it is directly blocking transactions."
  }
]
```

---

## Design Decisions

### In-memory clustering vs. database-level `GROUP BY`

Clustering is currently performed in Java using the Streams API after loading all records with `findAll()`. This was a deliberate starting choice:

- **Simplicity** — The repository interface stays clean (`JpaRepository` out of the box); no custom JPQL or native SQL queries needed.
- **Flexibility** — The 10-minute bucket logic and multi-field grouping are easier to express and test in Java than in SQL.
- **Correctness first** — Getting the algorithm right in Java is faster to iterate on than debugging aggregate SQL.

**Scaling path:** Once the dataset grows large (millions of rows), this approach will become a bottleneck. The correct migration is to push the `GROUP BY service_name, log_level, date_trunc('10 minutes', timestamp)` aggregation down to PostgreSQL via a `@Query` projection in `LogEntryRepository`, eliminating the full-table scan in Java.

---

### Graceful AI failure handling

The `GroqService.summarize()` method catches all exceptions internally and returns `"Summary unavailable"` rather than propagating them. This is intentional for several reasons:

- **AI is enhancement, not core** — The clustering result is the primary value. The AI summary is a convenience layer. An LLM outage should not make the endpoint return a `500`.
- **Partial success is better than total failure** — If one of 20 cluster summaries fails, the other 19 should still be returned. Fail-fast behaviour here would be actively harmful to the user experience.
- **Rate limits are expected** — Groq's free tier has rate limits. Handling them gracefully means the endpoint remains usable even during high-traffic periods.

---

## Future Improvements

| Area | Description |
|---|---|
| ~~**Docker Compose**~~ | ✅ Done — see [Run with Docker](#run-with-docker) |
| **Frontend Dashboard** | A React/Next.js UI that visualises clusters on a timeline, colour-codes severity, and displays AI summaries inline |
| **Elasticsearch / OpenSearch** | Replace PostgreSQL full-table scans with an Elasticsearch index for sub-second aggregations across hundreds of millions of log events |
| **Parallel AI summarisation** | Use `CompletableFuture` or Java 21 virtual threads to call Groq concurrently for multiple clusters, reducing latency from `O(n × LLM_latency)` to `O(LLM_latency)` |
| **Streaming log ingestion** | Add a Kafka consumer so log events can be ingested in real time from existing Kafka topics without changing microservice code |
| **Alerting** | Trigger PagerDuty / Slack alerts when a cluster's count exceeds a configurable threshold within a time window |
