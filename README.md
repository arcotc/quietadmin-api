# QuietAdmin API

Spring Boot 3.3.x backend for QuietAdmin.

------------------------------------------------------------------------

## 🧱 Tech Stack

-   Java 21
-   Spring Boot 3.3.x
-   Spring Security (JWT + Refresh Rotation)
-   Spring Data JPA
-   Flyway
-   MySQL 8+
-   Gradle
-   MailHog (local email testing)

------------------------------------------------------------------------

# 1️⃣ Local Database Setup (MySQL)

Install MySQL 8+ locally.

## Create database & user

``` sql
CREATE DATABASE quietadmin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'quietadmin_user'@'localhost' IDENTIFIED BY 'quietadmin';

GRANT ALL PRIVILEGES ON quietadmin.* TO 'quietadmin_user'@'localhost';

FLUSH PRIVILEGES;
```

------------------------------------------------------------------------

# 2️⃣ Application Configuration

The API uses Spring profiles.

## application.properties (base)

Contains shared configuration.

## application-local.properties (NOT committed)

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

security.session.idle-seconds=3600
security.cookie.secure=false

spring.mail.host=localhost
spring.mail.port=1025
spring.mail.username=
spring.mail.password=
spring.mail.properties.mail.smtp.auth=false
spring.mail.properties.mail.smtp.starttls.enable=false
app.mail.from=no-reply@quietadmin.local
app.base-url=http://localhost:8180
```

⚠ NEVER commit real secrets.

------------------------------------------------------------------------

# 3️⃣ Run MailHog (Local Email Testing)

Install MailHog (macOS):

    brew install mailhog

Start MailHog:

    /opt/homebrew/opt/mailhog/bin/MailHog -api-bind-addr 127.0.0.1:8025 -smtp-bind-addr 127.0.0.1:1025 -ui-bind-addr 127.0.0.1:8025

Open MailHog UI:

http://localhost:8025

------------------------------------------------------------------------

# 4️⃣ Run Tests

Tests use H2 in-memory database. No external services required.

Run all tests:

    ./gradlew test

Run with output:

    ./gradlew test --info

View HTML report after a run:

    open build/reports/tests/test/index.html

### Test coverage

| Suite | What it tests |
|---|---|
| `TeamServiceTest` | Admin creates team, non-admin blocked, blank/duplicate name, soft delete, cross-group add member |
| `NoticeServiceTest` | Defaults to DRAFT, publish draft, cannot publish expired, cross-group delete blocked |
| `RotaServiceTest` | Admin creates rota, non-admin blocked, wrong-user decline, cross-group access denied, admin delete |
| `AuthSecurityTest` | 401 without token, 401 with invalid token, public endpoints accessible |

------------------------------------------------------------------------

# 5️⃣ Run the API Locally (Development)

    ./gradlew clean build
    ./gradlew bootRun

API runs at:

http://localhost:8180

Swagger UI:

http://localhost:8180/swagger-ui.html

------------------------------------------------------------------------

# 5️⃣ Authentication Flow

### Registration

-   User registers
-   Verification email sent (MailHog locally)
-   User clicks `/api/auth/verify?token=...`
-   Account becomes ACTIVE

### Login

-   Requires verified email
-   Returns access token
-   Sets HTTP-only refresh cookie

### Refresh

-   Rotates refresh token
-   Performs replay detection
-   Performs fingerprint anomaly detection

------------------------------------------------------------------------

# 6️⃣ Useful Endpoints

POST /api/auth/register\
POST /api/auth/login\
POST /api/auth/refresh\
POST /api/auth/logout-all\
GET /api/auth/sessions\
POST /api/auth/sessions/{id}/revoke\
GET /api/auth/verify?token=...

------------------------------------------------------------------------

# 7️⃣ Production Checklist

-   Use environment variables for JWT secrets
-   Enable HTTPS
-   Set `security.cookie.secure=true`
-   Configure reverse proxy correctly
-   Use real SMTP provider (SendGrid, SES, etc.)
-   Enable structured audit logging
-   Monitor login throttle & replay detection

------------------------------------------------------------------------

QuietAdmin API is now production-ready with:

-   Email verification
-   JWT + Refresh rotation
-   Replay attack detection
-   Fingerprint anomaly detection
-   Device session management
-   Login throttling
-   Audit logging

------------------------------------------------------------------------

# API Steps to register user:
/api/auth/register

X-Device-Id: test-swagger

User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36
```
{
  "email": "admin@quietadmin.co.uk",
  "password": "12345678",
  "firstName": "Mark",
  "lastName": "Hunter"
}
```

/api/auth/verify

token: <non encrypted token - see email>

User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36

X-Device-Id: test-swagger

