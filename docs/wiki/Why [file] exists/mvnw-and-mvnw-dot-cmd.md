# 🚀 Understanding `mvnw` and `mvnw.cmd` — A Java Developer's Guide

> A complete guide to the Maven Wrapper, why it exists, how it works, and how to use it in Java projects.

---

## Table of Contents

1. [What are `mvnw` and `mvnw.cmd`?](#what-are-mvnw-and-mvnwcmd)
2. [Why Not Just Use `mvn` Directly?](#why-not-just-use-mvn-directly)
3. [Anatomy of the Maven Wrapper](#anatomy-of-the-maven-wrapper)
4. [How the Wrapper Works](#how-the-wrapper-works)
5. [Using the Wrapper in a Java Project](#using-the-wrapper-in-a-java-project)
6. [The `.mvn/` Folder](#the-mvn-folder)
7. [CI/CD and the Wrapper](#cicd-and-the-wrapper)
8. [Adding the Wrapper to an Existing Project](#adding-the-wrapper-to-an-existing-project)
9. [Project Structure](#project-structure)
10. [Best Practices](#best-practices)
11. [Quick Reference Cheatsheet](#quick-reference-cheatsheet)

---

## What are `mvnw` and `mvnw.cmd`?

`mvnw` and `mvnw.cmd` are **Maven Wrapper** scripts — thin shell scripts that sit in the root of your project and act as a **self-contained launcher for Maven**.

They are the same file for two different operating systems:

| File | OS | Shell |
|---|---|---|
| `mvnw` | Linux / macOS | Bash script |
| `mvnw.cmd` | Windows | Command Prompt batch script |

Instead of running:
```bash
mvn clean install
```

You run:
```bash
./mvnw clean install        # Linux / macOS
mvnw.cmd clean install      # Windows
```

The result is identical — but with one critical difference: **you don't need Maven installed on your machine at all.**

### 🧩 Mental Model

```
mvn          = "Use whatever Maven version happens to be installed on this machine"
./mvnw       = "Use exactly the Maven version this project requires — download it if needed"
```

---

## Why Not Just Use `mvn` Directly?

You might already have Maven installed. So why use the wrapper?

```bash
# ❌ The problem with relying on a globally installed Maven

Developer A:  mvn --version  →  Apache Maven 3.9.6
Developer B:  mvn --version  →  Apache Maven 3.6.3
CI Server:    mvn --version  →  Apache Maven 3.8.1
```

Now you have **three people running the same build with three different Maven versions**. This causes:

- 🐛 **Subtle build differences** — plugin behaviour changes between Maven versions
- 😤 **"Works on my machine"** — a build that passes locally fails on CI
- 🔄 **Onboarding friction** — new developers must install the exact right Maven version
- 🚨 **CI/CD fragility** — upgrading the build server's Maven breaks all projects at once

The Maven Wrapper solves all of this by **pinning the Maven version inside the project itself**.

---

## Anatomy of the Maven Wrapper

When you add the Maven Wrapper to a project, it creates these files:

```
your-project/
├── mvnw                                        ← Linux/macOS launcher script
├── mvnw.cmd                                    ← Windows launcher script
└── .mvn/
    └── wrapper/
        └── maven-wrapper.properties            ← Pinned Maven version + download URL
```

Let's look at each one:

### `mvnw` (Linux/macOS)

A bash script — you never need to edit this. Its only job is:
1. Read `.mvn/wrapper/maven-wrapper.properties`
2. Check if that Maven version is already cached locally
3. Download it if not
4. Run it, passing through all your arguments

### `mvnw.cmd` (Windows)

The identical logic, written as a Windows batch script. Same job, different shell syntax.

### `maven-wrapper.properties`

This is the important file — it specifies **exactly which Maven version** to use:

```properties
# .mvn/wrapper/maven-wrapper.properties
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
```

| Property | Purpose |
|---|---|
| `distributionUrl` | The exact Maven version to download and use |
| `wrapperUrl` | The wrapper JAR itself (the download machinery) |

To change the Maven version for the entire team, you update one line in this file and commit it.

---

## How the Wrapper Works

Here is what happens the first time you run `./mvnw clean install` on a fresh machine:

```
You type: ./mvnw clean install
          │
          ▼
mvnw script reads .mvn/wrapper/maven-wrapper.properties
          │
          ▼
Checks local cache: ~/.m2/wrapper/dists/apache-maven-3.9.6/
          │
     ┌────┴────┐
     │         │
  Found?     Not found?
     │             │
     │             ▼
     │    Downloads Maven 3.9.6 from distributionUrl
     │    Extracts it to ~/.m2/wrapper/dists/
     │             │
     └──────┬──────┘
            │
            ▼
  Runs Maven 3.9.6 with your arguments: clean install
            │
            ▼
  Your build runs exactly as expected ✅
```

**On the second run**, Maven is already cached in `~/.m2/wrapper/dists/` — no download, no delay.

> 💡 The download happens once per machine per Maven version. After that, `./mvnw` is just as fast as `mvn`.

---

## Using the Wrapper in a Java Project

### Running common Maven commands

Everything you know from `mvn` works identically with `./mvnw`:

```bash
# Compile the project
./mvnw compile

# Run all tests
./mvnw test

# Package into a JAR
./mvnw package

# Install to local .m2 repository
./mvnw install

# Full clean build
./mvnw clean install

# Skip tests during packaging
./mvnw clean package -DskipTests

# Run a Spring Boot app
./mvnw spring-boot:run

# Check for dependency updates
./mvnw versions:display-dependency-updates
```

### On Windows

```cmd
REM Same commands, different script
mvnw.cmd clean install
mvnw.cmd spring-boot:run
mvnw.cmd test
```

### Making `mvnw` executable (Linux/macOS)

On a fresh clone, the script may not have execute permission:

```bash
# ❌ Error you might see
bash: ./mvnw: Permission denied

# ✅ Fix — grant execute permission once
chmod +x mvnw

# Commit the permission so teammates don't hit the same issue
git update-index --chmod=+x mvnw
git commit -m "chore: make mvnw executable"
```

---

## The `.mvn/` Folder

The `.mvn/` folder is more than just the wrapper. It's the **project-level Maven configuration home**:

```
.mvn/
├── wrapper/
│   └── maven-wrapper.properties    ← Pinned Maven version
│
└── maven.config                    ← (optional) Default CLI flags for this project
```

### `maven.config` — Project-wide Maven flags

If you always pass the same flags to Maven, put them here instead of typing them every time:

```
# .mvn/maven.config

# Always show full stack traces on failure
-e

# Always use 4 threads for faster builds
-T 4

# Suppress download progress noise in CI
--no-transfer-progress
```

Now every `./mvnw` invocation automatically uses these flags — no need to remember them.

---

## CI/CD and the Wrapper

The Maven Wrapper is especially valuable in **CI/CD pipelines** (GitHub Actions, Jenkins, GitLab CI, etc.).

### Without the wrapper

```yaml
# GitHub Actions — fragile ❌
- name: Build
  run: mvn clean install
# Problem: relies on whatever Maven version the runner image has installed
# Updating the runner image could silently change your Maven version
```

### With the wrapper

```yaml
# GitHub Actions — reliable ✅
- name: Build
  run: ./mvnw clean install
# The project dictates the Maven version — not the CI server
```

### Real GitHub Actions example

```yaml
name: Build

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Check out code
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build with Maven Wrapper
        run: ./mvnw clean verify --no-transfer-progress
```

> **Same Java code. Same Maven version. Every machine. Every time.** ✅

---

## Adding the Wrapper to an Existing Project

If your project doesn't have `mvnw` yet, add it with one command:

```bash
# Requires Maven to be installed once — just to bootstrap the wrapper
mvn wrapper:wrapper

# Pin a specific Maven version
mvn wrapper:wrapper -Dmaven=3.9.6
```

This generates:
- `mvnw`
- `mvnw.cmd`
- `.mvn/wrapper/maven-wrapper.properties`

Then commit everything:

```bash
git add mvnw mvnw.cmd .mvn/
git commit -m "chore: add Maven Wrapper (Maven 3.9.6)"
```

> ⚠️ You only need Maven installed on your machine **once** — to run this bootstrap command. After that, everyone else (and CI) uses `./mvnw` and never needs Maven installed at all.

### Upgrading the Maven version later

```bash
# Re-run the wrapper goal with a new version
mvn wrapper:wrapper -Dmaven=3.9.9

# Commit the updated properties file
git add .mvn/wrapper/maven-wrapper.properties
git commit -m "chore: upgrade Maven Wrapper to 3.9.9"
```

One commit. Every developer and every CI server picks up the new version automatically.

---

## Project Structure

Here is what a well-organized Java project looks like with the Maven Wrapper:

```
your-project/
│
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/example/App.java
│   └── test/
│       └── java/
│           └── com/example/AppTest.java
│
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties   ← ✅ committed — pinned Maven version
│
├── mvnw                               ← ✅ committed — Linux/macOS launcher
├── mvnw.cmd                           ← ✅ committed — Windows launcher
│
├── .gitattributes                     ← marks mvnw as text eol=lf, mvnw.cmd as text eol=crlf
├── .gitignore
└── pom.xml
```

**Key rule:** All three files — `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties` — are **always committed to Git**. They are not secret and not generated on the fly. They travel with the project.

### Important: `.gitattributes` and the wrapper

Because `mvnw` is a shell script, it must have **LF line endings** on all platforms. Add this to your `.gitattributes`:

```gitattributes
mvnw        text eol=lf
mvnw.cmd    text eol=crlf
```

Without this, Windows developers checking out the repo may corrupt `mvnw` with CRLF line endings, breaking it for Linux/macOS teammates and CI servers.

---

## Best Practices

| ✅ Do | ❌ Don't |
|---|---|
| Always commit `mvnw`, `mvnw.cmd`, and `.mvn/` | Add them to `.gitignore` |
| Use `./mvnw` in all CI/CD scripts | Use bare `mvn` in pipelines |
| Set `mvnw` to LF in `.gitattributes` | Let Windows corrupt the script with CRLF |
| Pin a specific, tested Maven version | Leave `distributionUrl` pointing to "latest" |
| Upgrade via `mvn wrapper:wrapper -Dmaven=X.Y.Z` | Manually edit `maven-wrapper.properties` |
| Use `.mvn/maven.config` for shared CLI flags | Paste the same `-e -T 4` flags in every script |
| Make `mvnw` executable and commit the permission | Ask teammates to run `chmod +x` themselves |
| Tell new developers to use `./mvnw` in your README | Document only bare `mvn` commands |

---

## Quick Reference Cheatsheet

```
# Run any Maven command via the wrapper
./mvnw <goal>                          # Linux/macOS
mvnw.cmd <goal>                        # Windows

# Common commands
./mvnw clean install                   # Full clean build
./mvnw clean package -DskipTests       # Package without running tests
./mvnw test                            # Run tests only
./mvnw spring-boot:run                 # Run a Spring Boot app

# Fix permission on Linux/macOS after cloning
chmod +x mvnw
git update-index --chmod=+x mvnw

# Add the wrapper to an existing project (requires Maven installed once)
mvn wrapper:wrapper
mvn wrapper:wrapper -Dmaven=3.9.6      # Pin a specific version

# Upgrade the pinned Maven version
mvn wrapper:wrapper -Dmaven=3.9.9

# Files to always commit
mvnw
mvnw.cmd
.mvn/wrapper/maven-wrapper.properties

# .gitattributes entries (prevent line ending corruption)
mvnw        text eol=lf
mvnw.cmd    text eol=crlf

# Where downloaded Maven versions are cached
~/.m2/wrapper/dists/
```

---

## Summary

```
┌────────────────────────────────────────────────────────────┐
│                  Your Java Project                         │
│  mvnw / mvnw.cmd + .mvn/wrapper/maven-wrapper.properties  │
└──────────────────────┬─────────────────────────────────────┘
                       │ everyone runs ./mvnw
         ┌─────────────┼──────────────┐
         ▼             ▼              ▼
  Developer A     Developer B      CI Server
  (macOS)         (Windows)        (Ubuntu)
  ./mvnw          mvnw.cmd         ./mvnw
     │               │                │
     └───────┬────────┘                │
             │    All download and use │
             ▼    Maven 3.9.6          ▼
        ~/.m2/wrapper/dists/apache-maven-3.9.6/
             │
             ▼
    Same Maven. Same build. Zero surprises. ✅
```

> 💡 `mvnw` and `mvnw.cmd` are your project's **Maven version contract** — ensuring that every developer, every CI server, and every deployment pipeline builds with exactly the same tool, with no installation required.

---

*Guide covers: Maven Wrapper 3.x · Maven 3.9.x · Java 17+ · GitHub Actions · Windows / macOS / Linux*