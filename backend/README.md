# CAMILLUS Backend

Spring Boot REST API and MySQL data foundation. The frontend remains in `../frontend` and is not changed by this backend setup.

## Requirements

- JDK 21 or newer
- Maven 3.6.3 or newer
- MySQL 8.0 or newer

## Configure MySQL

Create a local database account and grant it access to the `camillus` database. The application can create the database when the configured account has permission; otherwise create it yourself:

```sql
CREATE DATABASE camillus CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Set connection values in the shell that will run Maven. The password is intentionally not stored in this repository.

PowerShell example:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/camillus?serverTimezone=UTC"
$env:DB_USERNAME = "your_local_mysql_user"
$env:DB_PASSWORD = "your_local_mysql_password"
$env:SPRING_PROFILES_ACTIVE = "local"
$env:SESSION_COOKIE_SECURE = "false"
$env:FRONTEND_ORIGIN = "http://localhost:5173"
```

`backend/.env.example` lists the supported environment variable names. Spring Boot does not load that example file automatically; set the variables in your terminal or IDE run configuration. A real `backend/.env` file is ignored by Git.

## Run

For local HTTP development, activate the `local` profile as shown above. Only that profile permits
`SESSION_COOKIE_SECURE=false`. Production deployments must activate the `production` profile and
use HTTPS; its session cookie is secure by default, and startup rejects an insecure override.

From this directory:

```powershell
mvn spring-boot:run
```

Flyway applies versioned database migrations at startup. Hibernate checks that the entity mappings match the migrated schema; it does not create or modify the MySQL tables automatically.

## Test

```powershell
mvn test
```

Tests use an in-memory H2 database in MySQL compatibility mode and run the same Flyway migration. They do not require a local MySQL server. Configure MySQL separately to verify a real database connection.

## Initial data model

- `users` stores account identity, a password hash column, and one of the supported roles. Authentication is not implemented in this phase.
- `patients` links patient-only details to one user account.
- `hospitals` stores facility details and generic source/provenance metadata. Dataset fields have not been guessed, and no sample hospital data is inserted.
- `departments` belong to hospitals; `doctors` belong to departments; availability rows belong to doctors.
- `appointments` link patients and doctors and store a status and time range. Booking rules and overlap checks are not implemented yet.

Hospital, department, doctor, availability, and appointment responses use separate string public IDs. DTO names mirror the discovery frontend fields (`type`, `beds`, `departmentIds`, `hospitalId`, `specialization`, `experienceYears`, `dayOfWeek`, and `time`) without coupling API IDs to numeric database primary keys.
