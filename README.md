# QuietAdmin API

Spring Boot 3.3.x backend for QuietAdmin.

------------------------------------------------------------------------

## Tech Stack

-   Java 21
-   Spring Boot 3
-   Spring Security (JWT)
-   Spring Data JPA
-   Flyway
-   MySQL 8+
-   Gradle

------------------------------------------------------------------------

# 1️⃣ Local Database Setup (MySQL)

Install MySQL 8+ locally.

### Create database & user

``` sql
CREATE DATABASE quietadmin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'quietadmin_user'@'localhost' IDENTIFIED BY 'quietadmin';

GRANT ALL PRIVILEGES ON quietadmin.* TO 'quietadmin_user'@'localhost';

FLUSH PRIVILEGES;
```

------------------------------------------------------------------------

# 2️⃣ Application Configuration

The API uses Spring profiles.

### application.properties (base)

Common properties shared across environments.

### application-local.properties (not committed)

Used for local development.

Example:

``` properties
spring.datasource.url=jdbc:mysql://localhost:3306/quietadmin?useSSL=false&serverTimezone=UTC
spring.datasource.username=quietadmin_user
spring.datasource.password=quietadmin

security.jwt.secret=YOUR_64_CHAR_SECRET
security.jwt.expiration-seconds=3600
security.jwt.issuer=quietadmin-local
security.jwt.audience=quietadmin-api
```

⚠ Do NOT commit real secrets.

------------------------------------------------------------------------

# 3️⃣ Run the API Locally

From project root:

``` bash
./gradlew clean build
./gradlew bootRun
```

API runs on:

    http://localhost:8180

Swagger UI:

    http://localhost:8180/swagger-ui.html

------------------------------------------------------------------------

# 4️⃣ Flyway Migrations

Database schema is managed by Flyway.

Migration files are located in:

    src/main/resources/db/migration

On startup, Flyway automatically migrates the database.

------------------------------------------------------------------------

# 5️⃣ JWT Security

-   Access tokens are returned in JSON
-   Refresh tokens are stored in HTTP-only cookies
-   `/api/**` endpoints are protected by JWT filter

------------------------------------------------------------------------

# 6️⃣ Useful Endpoints

## Register

POST `/api/auth/register`

## Login

POST `/api/auth/login`

## Refresh

POST `/api/auth/refresh`

## Sessions

GET `/api/auth/sessions`\
POST `/api/auth/sessions/{id}/revoke`

------------------------------------------------------------------------

# 7️⃣ Production Notes

-   Use environment variables for secrets
-   Use HTTPS only
-   Set secure cookies
-   Configure database with least privileges
