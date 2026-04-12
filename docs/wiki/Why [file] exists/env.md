# 🔐 Understanding `.env` Files — A Java Developer's Guide

> A complete guide to environment variables, `.env` files, and multi-environment configuration in Java.

---

## Table of Contents

1. [What is a `.env` File?](#what-is-a-env-file)
2. [Why Not Hardcode Values?](#why-not-hardcode-values)
3. [Anatomy of a `.env` File](#anatomy-of-a-env-file)
4. [Using `.env` in Java](#using-env-in-java)
5. [What Are Environments?](#what-are-environments)
6. [Multiple `.env` Files](#multiple-env-files)
7. [Loading the Right File in Java](#loading-the-right-file-in-java)
8. [The `.gitignore` Rule](#the-gitignore-rule)
9. [Project Structure](#project-structure)
10. [Best Practices](#best-practices)
11. [Quick Reference Cheatsheet](#quick-reference-cheatsheet)

---

## What is a `.env` File?

A `.env` file (short for **environment**) is a plain-text configuration file used to store **environment variables** — key-value pairs that hold sensitive or environment-specific settings **outside** of your source code.

Common things stored in a `.env` file:

| Type | Example |
|---|---|
| Database credentials | `DB_PASSWORD=secret123` |
| API keys | `API_KEY=sk-abc123xyz` |
| Server configuration | `APP_PORT=8080` |
| Feature flags | `ENABLE_DARK_MODE=true` |
| External service URLs | `PAYMENT_API_URL=https://pay.example.com` |
| App mode | `APP_ENV=development` |

### Mental Model

`API key = “Hello server, I am this app”`
`JWT     = “Hello server, I am this user and here’s proof”`
---

## Why Not Hardcode Values?

You might be tempted to write this in your Java code:

```java
// ❌ BAD — Never do this
String dbPassword = "mySecret123";
String apiKey = "sk-live-real-paid-key-xyz";
```

This causes serious problems:

- 🚨 **Security risk** — Anyone with access to your code (e.g. on GitHub) can see your secrets
- 🔄 **No flexibility** — Different environments (laptop, test server, production) need different values
- 🛠️ **Maintenance nightmare** — Changing a value requires editing and redeploying your code
- 👥 **Team conflicts** — Every developer on your team may have a different local setup

A `.env` file solves all of these problems.

---

## Anatomy of a `.env` File

```env
# This is a comment — use it to document your variables

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=myapp_db
DB_USER=admin
DB_PASSWORD=superSecretPassword123

# External APIs
API_KEY=sk-abc123xyz789
PAYMENT_API_URL=https://sandbox.stripe.com

# App Config
APP_ENV=development
APP_PORT=8080
LOG_LEVEL=DEBUG
```

**Syntax rules:**
- One variable per line: `KEY=VALUE`
- No spaces around the `=` sign
- Lines starting with `#` are comments
- Keys are usually written in `UPPER_SNAKE_CASE`
- No quotes needed around values (but some tools support them)

---

## Using `.env` in Java

Java doesn't read `.env` files natively, so we use the **`dotenv-java`** library.

### Step 1 — Add the Maven dependency

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.github.cdimascio</groupId>
    <artifactId>dotenv-java</artifactId>
    <version>3.0.0</version>
</dependency>
```

### Step 2 — Create your `.env` file

Place it in your **project root** (same level as `pom.xml`):

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=myapp_db
DB_USER=admin
DB_PASSWORD=superSecretPassword123
API_KEY=sk-abc123xyz789
APP_PORT=8080
LOG_LEVEL=DEBUG
```

### Step 3 — Read variables in Java

```java
import io.github.cdimascio.dotenv.Dotenv;

public class AppConfig {
    public static void main(String[] args) {

        // Load the .env file
        Dotenv dotenv = Dotenv.load();

        // Read variables
        String dbHost     = dotenv.get("DB_HOST");
        String dbPort     = dotenv.get("DB_PORT");
        String dbName     = dotenv.get("DB_NAME");
        String dbUser     = dotenv.get("DB_USER");
        String dbPassword = dotenv.get("DB_PASSWORD");
        String apiKey     = dotenv.get("API_KEY");
        int    appPort    = Integer.parseInt(dotenv.get("APP_PORT"));

        // Build a JDBC connection string — no secrets in code!
        String jdbcUrl = String.format(
            "jdbc:postgresql://%s:%s/%s", dbHost, dbPort, dbName
        );

        System.out.println("Connecting to: " + jdbcUrl);
        System.out.println("App running on port: " + appPort);
    }
}
```

**Output:**
```
Connecting to: jdbc:postgresql://localhost:5432/myapp_db
App running on port: 8080
```

### Optional: Provide default values

```java
// Returns "localhost" if DB_HOST is not defined in .env
String dbHost = dotenv.get("DB_HOST", "localhost");
```

---

## What Are Environments?

When you build an app, it runs in **different places at different stages** of development:

```
You write code → You test it → You ship it to users
   (Local)          (Staging)      (Production)
```

| Environment | Who uses it | Purpose |
|---|---|---|
| **Development** | You, on your laptop | Writing & debugging code |
| **Staging / Testing** | QA team, internal testers | Testing before release |
| **Production** | Real users | The live, real app |

Each environment connects to its **own database and services**, so the **same variable holds different values** depending on where the app runs:

```
DB_HOST on your laptop   →  localhost
DB_HOST on staging       →  staging.db.mycompany.com
DB_HOST on production    →  prod.db.mycompany.com
```

> ⚠️ **You never want a developer accidentally deleting real user data while testing on their laptop!**

---

## Multiple `.env` Files

Yes — you typically have **one `.env` file per environment**:

```
your-project/
├── .env                  ← Local dev settings       (gitignored ❌)
├── .env.staging          ← Staging server settings  (gitignored ❌)
├── .env.production       ← Production settings      (gitignored ❌)
└── .env.example          ← Template, no real values (committed ✅)
```

Here's how the **same variables** differ across each file:

### `.env` — Your Laptop (Development)

```env
APP_ENV=development
APP_PORT=8080

DB_HOST=localhost
DB_PORT=5432
DB_NAME=myapp_dev
DB_USER=admin
DB_PASSWORD=easypassword

API_KEY=sk-test-fake-key-123
LOG_LEVEL=DEBUG
```

### `.env.staging` — Test Server

```env
APP_ENV=staging
APP_PORT=8080

DB_HOST=staging.db.mycompany.com
DB_PORT=5432
DB_NAME=myapp_staging
DB_USER=staging_user
DB_PASSWORD=stagingP@ssw0rd!

API_KEY=sk-staging-limited-key
LOG_LEVEL=INFO
```

### `.env.production` — Live Server (Real Users)

```env
APP_ENV=production
APP_PORT=443

DB_HOST=prod.db.mycompany.com
DB_PORT=5432
DB_NAME=myapp_production
DB_USER=prod_user
DB_PASSWORD=Tr0ub4dor&3_Ultra_Secure!!

API_KEY=sk-live-real-paid-key-xyz
LOG_LEVEL=ERROR
```

Notice the differences:
- **Passwords** go from simple → complex
- **API keys** go from fake/test → real/paid
- **Log level** goes from `DEBUG` (verbose) → `ERROR` (minimal noise)
- **Port** changes from `8080` → `443` (standard HTTPS)

---

## Loading the Right File in Java

```java
import io.github.cdimascio.dotenv.Dotenv;

public class AppConfig {

    public static Dotenv loadConfig() {

        // Read which environment we're in from the OS/server
        String appEnv = System.getenv("APP_ENV");
        if (appEnv == null) appEnv = "development"; // default to dev

        // Pick the correct .env file
        String envFile = switch (appEnv) {
            case "staging"    -> ".env.staging";
            case "production" -> ".env.production";
            default           -> ".env";
        };

        System.out.println("Loading config: " + envFile);

        return Dotenv.configure()
                     .filename(envFile)
                     .load();
    }

    public static void main(String[] args) {
        Dotenv dotenv = loadConfig();

        System.out.println("Environment : " + dotenv.get("APP_ENV"));
        System.out.println("DB Host     : " + dotenv.get("DB_HOST"));
        System.out.println("DB Name     : " + dotenv.get("DB_NAME"));
        System.out.println("Log Level   : " + dotenv.get("LOG_LEVEL"));
    }
}
```

**On your laptop** (no `APP_ENV` set → defaults to dev):
```
Loading config  : .env
Environment     : development
DB Host         : localhost
DB Name         : myapp_dev
Log Level       : DEBUG
```

**On the production server** (`APP_ENV=production` set on the server):
```
Loading config  : .env.production
Environment     : production
DB Host         : prod.db.mycompany.com
DB Name         : myapp_production
Log Level       : ERROR
```

> **Same Java code. Different config. Zero hardcoding.** ✅

---

## The `.gitignore` Rule

This is the **most important rule**: never commit your `.env` files to Git.

```gitignore
# .gitignore
.env
.env.staging
.env.production
```

Instead, commit a **`.env.example`** — a template with all the variable names but **no real values**:

```env
# .env.example — safe to commit to GitHub ✅
# Copy this to .env and fill in your values

APP_ENV=
APP_PORT=

DB_HOST=
DB_PORT=
DB_NAME=
DB_USER=
DB_PASSWORD=

API_KEY=
LOG_LEVEL=
```

This way:
- New developers know exactly what variables they need to set up
- No secrets are ever exposed in version control
- Each developer fills in their own local values

---

## Project Structure

Here is what a well-organized Java project looks like with `.env` support:

```
your-project/
│
├── src/
│   └── main/
│       └── java/
│           ├── AppConfig.java       ← loads .env, exposes config
│           ├── DatabaseService.java ← uses DB vars
│           └── ApiService.java      ← uses API_KEY
│
├── .env                             ← ❌ gitignored, your local secrets
├── .env.staging                     ← ❌ gitignored, staging secrets
├── .env.production                  ← ❌ gitignored, production secrets
├── .env.example                     ← ✅ committed, safe template
│
├── .gitignore                       ← ignores all .env files
└── pom.xml                          ← includes dotenv-java dependency
```

---

## Best Practices

| ✅ Do | ❌ Don't |
|---|---|
| Store secrets in `.env` | Hardcode secrets in source code |
| Add `.env` to `.gitignore` | Commit `.env` files to Git |
| Commit `.env.example` as a template | Share real `.env` files via Slack/email |
| Use separate files per environment | Use one `.env` for all environments |
| Use `UPPER_SNAKE_CASE` for key names | Mix naming styles (`dbHost`, `Db_Host`) |
| Add comments to explain variables | Leave cryptic variables undocumented |
| Rotate secrets regularly | Reuse the same password forever |
| Use strong passwords in production | Use `password123` anywhere near prod |

---

## Quick Reference Cheatsheet

```
# Create a .env file
touch .env

# Typical .env structure
KEY=value
DB_HOST=localhost
APP_PORT=8080
API_KEY=your-key-here

# Maven dependency (pom.xml)
io.github.cdimascio : dotenv-java : 3.0.0

# Java — load default .env
Dotenv dotenv = Dotenv.load();

# Java — load specific file
Dotenv dotenv = Dotenv.configure().filename(".env.staging").load();

# Java — read a value
String value = dotenv.get("KEY");

# Java — read with fallback default
String value = dotenv.get("KEY", "default-value");

# .gitignore entries
.env
.env.staging
.env.production
```

---

## Summary

```
┌────────────────────────────────────────────────────────────┐
│                      Your Java Code                        │
│                  (same code everywhere)                    │
└──────────────────────┬─────────────────────────────────────┘
                       │ reads from
         ┌─────────────┼──────────────┐
         ▼             ▼              ▼
       .env       .env.staging   .env.production
    (localhost)   (test server)  (live server)
    fake API key  limited key    real paid key
    easy password  test password  ultra-secure pw
    DEBUG logs    INFO logs       ERROR logs only
```

> 💡 The `.env` file is your app's **private, flexible configuration layer** — keeping secrets out of your code and making your app adaptable to any environment without changing a single line of Java.

---

*Guide covers: dotenv-java 3.0.0 · Java 17+ · Maven*