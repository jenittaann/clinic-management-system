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
$env:FRONTEND_URL = "http://localhost:5173"
$env:MAIL_HOST = "your-smtp-host"
$env:MAIL_PORT = "587"
$env:MAIL_USERNAME = "your-smtp-user"
$env:MAIL_PASSWORD = "your-smtp-password"
$env:MAIL_FROM = "camillus@example.com"
```

Email sign-in reads these values from the backend process environment. `MAIL_HOST` and `MAIL_FROM`
must be non-empty; `MAIL_PORT` defaults to `587`, and SMTP authentication and STARTTLS default to
enabled. Use the SMTP host, port, username, password, and sender address supplied by your email
provider. Do not commit real credentials.

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

Hospital and doctor browsing is public. Protected patient and staff operations use a verified one-time email link to establish a server session. Configure SMTP and `MAIL_FROM` to use those operations. Patient registration is available through the patient dashboard and creates an account only after the emailed link is verified. Existing doctor and receptionist accounts must be assigned to the selected hospital; doctors must also be linked to a doctor profile.

### First administrator

When `users` is empty, a one-time administrator can be provisioned without a public endpoint. Before
the first backend start only, set `CAMILLUS_BOOTSTRAP_ADMIN_ENABLED=true`,
`CAMILLUS_BOOTSTRAP_ADMIN_EMAIL`, and `CAMILLUS_BOOTSTRAP_ADMIN_PASSWORD` in the backend process
environment. Choose a unique administrator email and a strong password of at least 16 characters.
The startup provisioner refuses to run if any user account already exists and stores only a password
hash. After the first successful startup, remove the three bootstrap variables (or set enabled to
false) and restart normally. This does not alter the hospital or department records.

An administrator adds each doctor with the doctor’s email in the existing doctor management form.
The API creates the doctor profile, assigns the selected hospital department, and creates and links
a DOCTOR account in one transaction. Once SMTP is configured, the doctor can request a sign-in link
using that email and manage their profile and schedule. Patient accounts are created through the
existing patient registration checkbox after the patient verifies their email link.

For an existing unlinked doctor profile, an administrator can select an unlinked DOCTOR account by
email from the Doctor management section and confirm that the account belongs to that profile.
Doctor records do not carry an account email, so verify the relationship from trusted existing
records before confirming. The API only offers unlinked DOCTOR accounts and rejects links that
would replace another doctor/profile association.

## Test

```powershell
mvn test
```

Tests use an in-memory H2 database in MySQL compatibility mode and run the same Flyway migration. They do not require a local MySQL server. Configure MySQL separately to verify a real database connection.

## Initial data model

- `users` stores account identity, a password hash column, and one of the supported roles. A verified email link establishes a server-side session.
- `patients` links patient-only details to one user account.
- `hospitals` stores facility details and generic source/provenance metadata. Dataset fields have not been guessed, and no sample hospital data is inserted.
- `departments` belong to hospitals; `doctors` belong to departments; availability rows belong to doctors.
- `appointments` link patients and doctors and store a status and time range. Hospital context is derived through the doctor's department. Booking validates recurring availability, appointment duration, and patient/doctor overlaps.

Hospital, department, doctor, availability, and appointment responses use separate string public IDs. DTO names mirror the discovery frontend fields (`type`, `beds`, `departmentIds`, `hospitalId`, `specialization`, `experienceYears`, `dayOfWeek`, and `time`) without coupling API IDs to numeric database primary keys.
