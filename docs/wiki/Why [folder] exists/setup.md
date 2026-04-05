# 📁 The `setup/` Directory — A Complete Guide

> A developer's reference for understanding, maintaining, and contributing to the `setup/` folder in a Java Spring Boot / Maven project.

---

## Table of Contents

1. [What is `setup/`?](#what-is-setup)
2. [What Problem Does It Solve?](#what-problem-does-it-solve)
3. [Folder Structure](#folder-structure)
4. [Files Inside `setup/`](#files-inside-setup)
   - [getting-started.md](#1-getting-startedmd)
   - [environment-variables.md](#2-environment-variablesmd)
   - [database.md](#3-databasemd)
   - [configuration.md](#4-configurationmd)
5. [When to Edit Each File](#when-to-edit-each-file)
6. [File Format Examples](#file-format-examples)
7. [What NOT To Do](#what-not-to-do)
8. [Best Practices](#best-practices)
9. [Quick Reference](#quick-reference)

---

## What is `setup/`?

The `setup/` directory is a **dedicated folder at the root of your project** that contains all the documentation and reference files a developer needs to get the project running from scratch.

Think of it as the **onboarding manual** for your codebase. It lives alongside your source code but contains no logic — only guides, templates, and instructions.

```
your-project/
├── src/                  ← your application code
├── setup/                ← everything needed to SET UP the app
│   ├── getting-started.md
│   ├── environment-variables.md
│   ├── database.md
│   └── configuration.md
├── .env.example
├── pom.xml
└── README.md
```

---

## What Problem Does It Solve?

Without a `setup/` folder, a new developer joining your project faces a wall of questions with no answers:

```
❓ How do I run this project?
❓ What environment variables do I need?
❓ Which database does this use? How do I set it up?
❓ Are there any special config files I need to create?
❓ What Java version do I need?
❓ Why isn't it starting?
```

They either spend hours guessing, or interrupt a senior developer to ask. Both are wastes of time.

The `setup/` folder answers all of these questions **before they're even asked**.

| Without `setup/` | With `setup/` |
|---|---|
| New dev asks around for hours | New dev reads docs and runs the app |
| Tribal knowledge lives in people's heads | Knowledge is written down and versioned |
| Every dev sets up differently | Everyone follows the same process |
| Onboarding takes days | Onboarding takes minutes |
| "It works on my machine" | Consistent setup across all machines |

> 💡 The `setup/` folder is not just for new developers. It's also for **you**, three months from now, when you've forgotten how your own project works.

---

## Folder Structure

Here is a recommended `setup/` structure for a Spring Boot / Maven project:

```
setup/
│
├── getting-started.md          ← First file any developer should read
├── environment-variables.md    ← All .env variables explained
├── database.md                 ← DB setup, migrations, seeding
└── configuration.md            ← application.properties / YAML explained
```

Each file has a **single responsibility** — it covers one topic and nothing else. This makes it easy to find exactly what you need without reading everything.

---

## Files Inside `setup/`

---

### 1. `getting-started.md`

**Purpose:** The very first file a developer reads. It walks through everything needed to get the app running locally from zero — prerequisites, cloning, environment setup, and running the app.

**Think of it as:** The ignition key. Nothing else in `setup/` matters if you can't start the app.

**What it should cover:**
- System prerequisites (Java version, Maven version, Docker, etc.)
- How to clone the repository
- How to set up the `.env` file
- How to install dependencies
- How to run the application
- How to verify it's working (health check URL, expected output)
- Common first-run errors and how to fix them

---

### 2. `environment-variables.md`

**Purpose:** A complete reference for every variable in your `.env` file. Lists what each variable does, what values are valid, whether it is required or optional, and what the default is.

**Think of it as:** The dictionary for your `.env` file.

**What it should cover:**
- Every variable name
- A plain-English description of what it does
- Whether it is required or optional
- The expected format or data type
- Example values (never real secrets)
- Where to get the value (e.g. "Get this from the AWS console")

---

### 3. `database.md`

**Purpose:** Everything related to the database — what engine is used, how to create and connect to it locally, how to run migrations, and how to seed it with test data.

**Think of it as:** The database administrator's handoff note.

**What it should cover:**
- Database engine and version (e.g. PostgreSQL 15)
- How to create the local database
- Connection details (host, port, name)
- How to run migrations (Flyway, Liquibase, or raw SQL)
- How to seed test/demo data
- How to reset the database
- ERD or schema overview (optional but very helpful)

---

### 4. `configuration.md`

**Purpose:** Explains the `application.properties` or `application.yml` files used by Spring Boot — what each property does, which profiles exist (`dev`, `staging`, `prod`), and when to change them.

**Think of it as:** The control panel manual.

**What it should cover:**
- Overview of Spring profiles used in the project
- Key properties and what they control
- How to switch between profiles
- Which properties are safe to change locally
- Which properties must never be changed without team discussion

---

## When to Edit Each File

Use this as a quick guide for knowing when a file needs updating:

| File | Edit when... |
|---|---|
| `getting-started.md` | Prerequisites change · New setup steps are added · A common error is discovered and solved |
| `environment-variables.md` | A new `.env` variable is added · A variable is removed or renamed · The format or valid values change |
| `database.md` | The DB engine or version changes · A new migration step is added · Seed data changes · The schema is significantly updated |
| `configuration.md` | A new Spring profile is added · A key property changes behavior · New third-party integrations are configured |

> ⚠️ **Rule of thumb:** If you change something that would break another developer's local setup, update `setup/` in the **same pull request**. Never after.

---

## File Format Examples

The following are recommended formats for each file. Keep them clean, scannable, and practical.

---

### Format: `getting-started.md`

```markdown
# Getting Started

## Prerequisites

| Tool    | Version  | Install |
|---------|----------|---------|
| Java    | 17+      | https://adoptium.net |
| Maven   | 3.9+     | https://maven.apache.org |
| Docker  | 24+      | https://docker.com |
| Git     | any      | https://git-scm.com |

Verify your versions:
\```bash
java -version
mvn -version
docker --version
\```

---

## 1. Clone the Repository

\```bash
git clone https://github.com/your-org/your-project.git
cd your-project
\```

---

## 2. Set Up Environment Variables

\```bash
cp .env.example .env
\```

Open `.env` and fill in your local values.
See `setup/environment-variables.md` for a full explanation of each variable.

---

## 3. Set Up the Database

Follow the instructions in `setup/database.md`.

---

## 4. Install Dependencies

\```bash
mvn clean install
\```

---

## 5. Run the Application

\```bash
mvn spring-boot:run
\```

The app will start on: http://localhost:8080

Health check: http://localhost:8080/actuator/health

Expected response:
\```json
{ "status": "UP" }
\```

---

## Common Issues

**Port 8080 already in use**
\```bash
# Find and kill the process using port 8080
lsof -i :8080
kill -9 <PID>
\```

**`.env` file not found**
Make sure you ran `cp .env.example .env` and that the `.env` file is in the project root.

**`mvn` command not found**
Maven is not installed or not on your PATH. See the Prerequisites section above.
```

---

### Format: `environment-variables.md`

```markdown
# Environment Variables

All variables are stored in `.env` in the project root.
Copy `.env.example` to get started: `cp .env.example .env`

---

## Database

| Variable     | Required | Default     | Description                        |
|--------------|----------|-------------|------------------------------------|
| `DB_HOST`    | ✅ Yes   | `localhost` | Hostname of the PostgreSQL server  |
| `DB_PORT`    | ✅ Yes   | `5432`      | Port the database listens on       |
| `DB_NAME`    | ✅ Yes   | —           | Name of the database               |
| `DB_USER`    | ✅ Yes   | —           | Database login username            |
| `DB_PASSWORD`| ✅ Yes   | —           | Database login password            |

---

## Application

| Variable     | Required | Default        | Description                              |
|--------------|----------|----------------|------------------------------------------|
| `APP_ENV`    | ✅ Yes   | `development`  | Environment name: development/staging/production |
| `APP_PORT`   | ❌ No    | `8080`         | Port the Spring Boot app listens on      |
| `LOG_LEVEL`  | ❌ No    | `DEBUG`        | Logging level: DEBUG / INFO / WARN / ERROR |

---

## External APIs

| Variable      | Required | Description                                      |
|---------------|----------|--------------------------------------------------|
| `API_KEY`     | ✅ Yes   | API key for the third-party service. Get it from the dashboard at https://example.com/dashboard |
| `WEBHOOK_SECRET` | ❌ No | Secret used to verify incoming webhook payloads  |

---

## Example `.env`

\```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=myapp_dev
DB_USER=admin
DB_PASSWORD=localpassword

# App
APP_ENV=development
APP_PORT=8080
LOG_LEVEL=DEBUG

# External APIs
API_KEY=sk-test-fake-key-for-local-dev
\```
```

---

### Format: `database.md`

```markdown
# Database Setup

This project uses **PostgreSQL 15**.

---

## Prerequisites

- PostgreSQL 15 installed locally, or Docker running

---

## 1. Create the Database

**Option A — Using psql directly:**
\```sql
CREATE DATABASE myapp_dev;
CREATE USER admin WITH PASSWORD 'localpassword';
GRANT ALL PRIVILEGES ON DATABASE myapp_dev TO admin;
\```

**Option B — Using Docker:**
\```bash
docker run --name myapp-postgres \
  -e POSTGRES_DB=myapp_dev \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=localpassword \
  -p 5432:5432 \
  -d postgres:15
\```

---

## 2. Run Migrations

This project uses **Flyway** for database migrations.
Migrations run automatically on app startup.

To run them manually:
\```bash
mvn flyway:migrate
\```

Migration files are located in:
\```
src/main/resources/db/migration/
\```

---

## 3. Seed Test Data (Optional)

\```bash
mvn spring-boot:run -Dspring-boot.run.profiles=seed
\```

---

## 4. Reset the Database

\```bash
mvn flyway:clean flyway:migrate
\```

> ⚠️ This wipes all data. Only use locally, never against staging or production.

---

## Connection Details (Local)

| Field    | Value        |
|----------|--------------|
| Host     | localhost    |
| Port     | 5432         |
| Database | myapp_dev    |
| User     | admin        |
| Password | localpassword |
```

---

### Format: `configuration.md`

```markdown
# Configuration

Spring Boot configuration lives in:
\```
src/main/resources/
├── application.yml           ← shared defaults (all environments)
├── application-dev.yml       ← development overrides
├── application-staging.yml   ← staging overrides
└── application-prod.yml      ← production overrides
\```

---

## Switching Profiles

Set the active profile via your `.env` file:

\```env
APP_ENV=development
\```

Or pass it at runtime:
\```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
\```

---

## Key Properties

| Property | File | Description |
|---|---|---|
| `server.port` | `application.yml` | Port the app runs on |
| `spring.datasource.url` | `application-dev.yml` | JDBC connection string |
| `spring.jpa.show-sql` | `application-dev.yml` | Logs all SQL queries (dev only) |
| `logging.level.root` | per profile | Controls verbosity of logs |
| `spring.flyway.enabled` | `application.yml` | Enables/disables DB migrations |

---

## Safe to Change Locally

- `server.port` — if 8080 conflicts with another app
- `logging.level.root` — if you want more/less log output
- `spring.jpa.show-sql` — toggle SQL query logging

## Do NOT Change Without Team Discussion

- `spring.flyway.enabled` — disabling this skips migrations
- `spring.jpa.hibernate.ddl-auto` — wrong value can wipe your schema
- Any production profile values
```

---

## What NOT To Do

These are the most common mistakes developers make with the `setup/` folder:

### ❌ Storing real secrets in `setup/` files

```markdown
<!-- BAD — never put real values in setup/ docs -->
DB_PASSWORD=Tr0ub4dor&3_Ultra_Secure!!
API_KEY=sk-live-real-paid-key-xyz
```

`setup/` files are committed to Git. Anyone with repo access — or anyone who ever had access — can see them. Use placeholder values only.

```markdown
<!-- GOOD — placeholder values only -->
DB_PASSWORD=your-database-password-here
API_KEY=get-this-from-the-team-dashboard
```

---

### ❌ Letting `setup/` go stale

The most dangerous `setup/` file is one that is **out of date**. A developer who follows wrong instructions will waste hours and blame themselves before realizing the docs are wrong.

Update `setup/` in the **same PR** as the change that requires it. No exceptions.

---

### ❌ Dumping everything into one giant file

```
setup/
└── setup.md   ← 800 lines covering everything
```

Nobody reads this. Split by topic so developers can jump to exactly what they need.

---

### ❌ Writing for yourself, not for others

Avoid assuming knowledge. A phrase like:

> "Configure the datasource as usual."

...means nothing to someone new. Write as if the reader has never touched this codebase.

---

### ❌ Skipping the "why"

Don't just say *what* to do — briefly explain *why*. This helps developers make good decisions when they inevitably encounter a situation not covered by the docs.

```markdown
<!-- BAD -->
Run: mvn flyway:clean

<!-- GOOD -->
To reset your local database and re-run all migrations from scratch:
mvn flyway:clean flyway:migrate

⚠️ This deletes all data. Only use locally — never on staging or production.
```

---

### ❌ No `setup/` at all

The worst `setup/` is no `setup/`. Undocumented projects create dependency on specific people — if that person leaves, the knowledge leaves with them.

---

## Best Practices

| ✅ Do | ❌ Don't |
|---|---|
| Keep one file per topic | Cram everything into one file |
| Use placeholder values in examples | Use real secrets anywhere in `setup/` |
| Update docs in the same PR as code changes | Update docs "later" (later never comes) |
| Write for a developer who is brand new | Assume knowledge of your project |
| Explain the "why", not just the "what" | Write instructions with no context |
| Keep examples copy-paste ready | Write examples that need heavy editing to use |
| Version your prerequisites | Leave tool versions unspecified |
| Link between `setup/` files | Repeat the same content in multiple files |

---

## Quick Reference

```
setup/
├── getting-started.md       ← Start here. Prerequisites + run the app.
├── environment-variables.md ← What every .env variable means.
├── database.md              ← Create DB, run migrations, seed data.
└── configuration.md         ← Spring profiles + key properties explained.
```

**Edit trigger cheatsheet:**

```
Added a new .env variable?       → update environment-variables.md
Changed Java/Maven version?      → update getting-started.md
Added a DB migration?            → update database.md
Added a new Spring profile?      → update configuration.md
Found a bug in the setup steps?  → fix getting-started.md immediately
```

**Golden rule:**

> If a developer can clone this repo on a fresh machine and be running the app in under 15 minutes by reading only the `setup/` folder — your documentation is good.

---

*Guide covers: Java 17+ · Spring Boot 3.x · Maven 3.9+ · PostgreSQL 15 · Flyway*