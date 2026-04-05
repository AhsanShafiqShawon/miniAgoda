# Spring Boot Configuration: `application.yml` & `.env`

> A practical guide to understanding, using, and not abusing Spring Boot's configuration files.

---

## Table of Contents

1. [What is `application.yml`?](#what-is-applicationyml)
2. [Why does it exist?](#why-does-it-exist)
3. [What problem does it solve?](#what-problem-does-it-solve)
4. [Anatomy of a typical file](#anatomy-of-a-typical-file)
5. [When to edit it](#when-to-edit-it)
6. [What NOT to do with it](#what-not-to-do-with-it)
7. [Profile system](#profile-system)
8. [How your code reads from it](#how-your-code-reads-from-it)
9. [Mental model](#mental-model)
10. [`application.yml` vs `.env`](#applicationyml-vs-env)

---

## What is `application.yml`?

`application.yml` is a configuration file used primarily in **Spring Boot** applications (Java/Kotlin). It uses YAML (YAML Ain't Markup Language) syntax to define settings that control how your application behaves — without changing any code.

---

## Why does it exist?

It exists to **separate configuration from code**. Instead of hardcoding values like database URLs, ports, or feature flags directly into your Java classes, you put them here. This is a core principle known as the **12-Factor App methodology** — config belongs in the environment, not the codebase.

Before this, developers used `.properties` files (`application.properties`), but YAML became preferred because it supports **hierarchical structure** naturally, making it far more readable for nested config.

---

## What problem does it solve?

| Problem | How `application.yml` solves it |
|---|---|
| Hardcoded values | Centralizes all config in one place |
| Environment differences (dev/prod) | Profile-specific files like `application-prod.yml` |
| Repetitive flat keys | YAML nesting removes redundancy |
| Rebuilding for config changes | Change config without touching or recompiling code |
| Secret sprawl | Points to env vars / secret managers instead of inlining secrets |

---

## Anatomy of a typical file

```yaml
server:
  port: 8080                      # Which port to run on

spring:
  application:
    name: my-service              # App name (used in logs, service discovery)

  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: ${DB_USER}          # Read from environment variable
    password: ${DB_PASS}

  jpa:
    hibernate:
      ddl-auto: validate          # Don't auto-create tables in prod
    show-sql: false

logging:
  level:
    root: INFO
    com.mycompany: DEBUG          # Verbose logs only for your own packages

management:
  endpoints:
    web:
      exposure:
        include: health,info      # Expose only these actuator endpoints
```

---

## When to edit it

- Changing the **server port**
- Wiring up a **new database, cache (Redis), or message broker (Kafka)**
- Adding **environment-specific behavior** (timeouts, pool sizes, log levels)
- Enabling/disabling **Spring features** (security, actuator endpoints, scheduling)
- Defining **custom app properties** your code reads via `@Value` or `@ConfigurationProperties`
- Adjusting **connection pool settings** for performance tuning

---

## What NOT to do with it

### 🔴 Never hardcode secrets

```yaml
# BAD
datasource:
  password: mysupersecretpassword123

# GOOD
datasource:
  password: ${DB_PASSWORD}   # Injected at runtime from env or secrets manager
```

### 🔴 Never commit production config to Git

Your `application-prod.yml` with real URLs, credentials, or internal hostnames should not live in source control.

### 🔴 Don't put logic in it

It's data, not code. If you're trying to express conditionals or loops, that belongs in your code, not here.

### 🔴 Don't ignore indentation

YAML is whitespace-sensitive. Tabs will break it. Always use spaces (2 per level is standard).

### 🔴 Don't use it as a feature flag system at scale

For simple flags it's fine, but at scale use a proper feature flag tool (LaunchDarkly, Unleash, etc.) — changing a flag shouldn't require a redeploy.

---

## Profile system

One of `application.yml`'s most powerful features. Spring Boot lets you have multiple config files per environment:

```
application.yml           ← shared defaults
application-dev.yml       ← dev overrides
application-staging.yml   ← staging overrides
application-prod.yml      ← prod overrides
```

Activate a profile via an environment variable:

```bash
SPRING_PROFILES_ACTIVE=prod java -jar myapp.jar
```

The prod file's values **override** the base file, so you only need to write what's different.

---

## How your code reads from it

**Via `@Value`** (simple injection):

```java
@Value("${server.port}")
private int port;
```

**Via `@ConfigurationProperties`** (typed, structured — preferred):

```java
@ConfigurationProperties(prefix = "spring.datasource")
public class DataSourceConfig {
    private String url;
    private String username;
    // getters/setters...
}
```

---

## Mental model

Think of `application.yml` as the **control panel for your app**. The app is the machine; the YAML is the set of knobs and switches. You don't rewire the machine every time you want different behavior — you just adjust the panel. That's the whole point.

---

## `application.yml` vs `.env`

They solve a **similar problem** (keeping config out of code) but at **different layers**.

### The core difference in one line

| | `application.yml` | `.env` |
|---|---|---|
| **Who reads it** | Your **application/framework** (Spring Boot) | Your **OS/shell/runtime environment** |
| **Lives at** | App layer | System/process layer |

### How they relate

```
┌─────────────────────────────────────┐
│           Your Machine / Container  │
│                                     │
│  .env → sets ENVIRONMENT VARIABLES  │
│              ↓                      │
│     OS Environment (export DB=...)  │
│              ↓                      │
│   application.yml reads ${DB_URL}   │
│              ↓                      │
│         Spring Boot App             │
└─────────────────────────────────────┘
```

`.env` feeds **into** the environment. `application.yml` reads **from** the environment. They work together, not against each other.

### Side-by-side comparison

| Aspect | `application.yml` | `.env` |
|---|---|---|
| **Format** | YAML (hierarchical) | `KEY=VALUE` (flat) |
| **Parsed by** | Spring Boot / framework | Docker, shell, dotenv libraries |
| **Scope** | One specific app | Any process on that machine |
| **Supports nesting** | Yes | No |
| **Supports comments** | Yes (`#`) | Sometimes (depends on tool) |
| **Profiles/environments** | Built-in (`-dev`, `-prod`) | You manage manually |
| **Committed to Git?** | Base file yes, prod file no | **Never** |
| **Primary use** | App structure & behavior | Secrets & environment-specific values |

### What each one is good at

**`.env` is good for:**
- Raw secrets (`DB_PASSWORD=abc123`)
- Values that differ per machine/developer
- Things that need to be available to multiple tools (your app, CLI scripts, Docker, etc.)
- CI/CD pipelines injecting secrets at runtime

**`application.yml` is good for:**
- Structured, nested config (JPA settings, Kafka topics, pool sizes)
- App behavior (which endpoints to expose, log levels, retry counts)
- Referencing env vars rather than holding secrets directly
- Defaults that apply across all environments

### How they work together (the right pattern)

**.env file** holds the secret:

```bash
DB_PASSWORD=mysecretpassword
DB_URL=jdbc:postgresql://localhost:5432/mydb
```

**application.yml** references it:

```yaml
spring:
  datasource:
    url: ${DB_URL}
    password: ${DB_PASSWORD}
```

Your code never sees the raw secret. Spring Boot resolves `${DB_URL}` at startup from the environment.

### Who loads the `.env`?

The `.env` file isn't magic — something has to load it into the environment first:

| Tool | How |
|---|---|
| **Docker Compose** | `env_file: .env` in `docker-compose.yml` |
| **Local dev (Node)** | `dotenv` library auto-loads it |
| **Local dev (Java)** | IDE run config, or a plugin like `dotenv-java` |
| **Shell manually** | `export $(cat .env \| xargs)` |
| **CI/CD (GitHub Actions)** | Secrets set in the UI, injected as env vars |

> Spring Boot itself **does not natively read `.env` files** — it reads environment variables that are already set.

### The golden rule

```
.env             → holds secrets, never committed to Git
application.yml  → holds structure, safe to commit (without secrets)
```

- If a value is **secret** → `.env` (or a secrets manager)
- If a value is **structural config** → `application.yml`
- If it's **both** → define it in `.env`, reference it in `application.yml` via `${VAR_NAME}`