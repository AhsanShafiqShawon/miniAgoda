# 🗂️ Understanding `.gitattributes` Files — A Java Developer's Guide

> A complete guide to `.gitattributes`, line endings, diff control, and per-file Git behaviour in Java projects.

---

## Table of Contents

1. [What is a `.gitattributes` File?](#what-is-a-gitattributes-file)
2. [Why Not Just Leave Git to Its Defaults?](#why-not-just-leave-git-to-its-defaults)
3. [Anatomy of a `.gitattributes` File](#anatomy-of-a-gitattributes-file)
4. [The Line Ending Problem](#the-line-ending-problem)
5. [Using `.gitattributes` in a Java Project](#using-gitattributes-in-a-java-project)
6. [Diff and Merge Control](#diff-and-merge-control)
7. [Binary vs Text Files](#binary-vs-text-files)
8. [Export and Archive Control](#export-and-archive-control)
9. [Project Structure](#project-structure)
10. [Best Practices](#best-practices)
11. [Quick Reference Cheatsheet](#quick-reference-cheatsheet)

---

## What is a `.gitattributes` File?

A `.gitattributes` file is a plain-text configuration file placed in the **root of your Git repository**. It tells Git how to **treat specific files** — controlling things like line endings, diff rendering, and merge behaviour on a per-file-pattern basis.

Think of it as a set of **rules Git reads before touching any file**.

Common things controlled by `.gitattributes`:

| Concern | Example Rule |
|---|---|
| Line endings | `*.java text eol=lf` |
| Binary files | `*.jar binary` |
| Diff display | `*.md diff=markdown` |
| Merge strategy | `package-lock.json merge=ours` |
| Export exclusion | `.github/ export-ignore` |
| Language stats | `*.sql linguist-language=SQL` |

### 🧩 Mental Model

```
.gitignore     = "Git, don't track this file at all"
.gitattributes = "Git, track this file — but treat it THIS way"
```

---

## Why Not Just Leave Git to Its Defaults?

You might think Git handles files correctly on its own. It mostly does — until it doesn't:

```bash
# ❌ What can go wrong without .gitattributes

git diff MyService.java
# ^ Shows hundreds of changed lines — but only whitespace changed
# because Windows checked out CRLF, Linux committed LF

git merge feature-branch
# ^ Merge conflict in package-lock.json every single time
# even though neither branch touched the same dependency

git archive HEAD --output=release.zip
# ^ Your .github/ CI config and test fixtures ship in the zip
# polluting the release artifact
```

These are real, common problems on any team with:
- Developers on both Windows and macOS/Linux
- Binary files like JARs, images, or compiled assets
- Auto-generated files like lock files or build outputs

A `.gitattributes` file solves all of these problems — **declaratively and automatically**, for every developer on the team.

---

## Anatomy of a `.gitattributes` File

```gitattributes
# This is a comment

# Pattern          Attribute(s)
*.java             text eol=lf
*.xml              text eol=lf
*.properties       text eol=lf

*.jar              binary
*.png              binary
*.pdf              binary

*.md               diff=markdown
pom.xml            diff=xml

package-lock.json  merge=ours
```

**Syntax rules:**
- One rule per line: `PATTERN  ATTRIBUTE`
- Lines starting with `#` are comments
- Patterns follow `.gitignore`-style glob matching
- Multiple attributes can be on one line, space-separated
- More specific patterns override broader ones (last match wins)

---

## The Line Ending Problem

This is the **most common reason** `.gitattributes` exists on Java teams.

### The Background

Different operating systems use different characters to mark the end of a line in a text file:

| OS | Line Ending | Symbol |
|---|---|---|
| Linux / macOS | Line Feed | `LF` (`\n`) |
| Windows | Carriage Return + Line Feed | `CRLF` (`\r\n`) |

Git tries to be helpful:
- On **Windows**: Git converts `LF → CRLF` when checking out files
- On **Windows**: Git converts `CRLF → LF` when committing files

This is controlled by a Git setting called `core.autocrlf`. The problem? **Each developer sets this differently**, or not at all.

```
Developer A (Windows, autocrlf=true)  → checks out CRLF files → commits LF ✅
Developer B (Windows, autocrlf=false) → checks out CRLF files → commits CRLF ❌
Developer C (Linux,   autocrlf=false) → checks out LF files   → commits LF ✅
```

Now Developer B's commit looks like **every line changed** even though they only edited one line. `git blame` becomes useless. Code reviews become a nightmare.

### The Fix

`.gitattributes` **overrides everyone's local Git settings** and enforces a single, consistent line-ending strategy for the whole team:

```gitattributes
# Force all text files to use LF in the repository
* text=auto eol=lf

# Explicitly mark Java source files as text
*.java    text eol=lf
*.xml     text eol=lf
*.json    text eol=lf
*.yml     text eol=lf
*.yaml    text eol=lf
*.md      text eol=lf
*.html    text eol=lf
*.css     text eol=lf
*.sh      text eol=lf

# Windows-only scripts get CRLF (they require it)
*.bat     text eol=crlf
*.cmd     text eol=crlf
```

With this in place, no matter what a developer's `core.autocrlf` is set to, the repository **always stores LF line endings** in source files.

---

## Using `.gitattributes` in a Java Project

### Step 1 — Create the file

Place `.gitattributes` in your **project root** (same level as `pom.xml`):

```
your-project/
├── src/
├── .gitattributes    ← here
├── .gitignore
└── pom.xml
```

### Step 2 — Add your rules

```gitattributes
# ─────────────────────────────────────────────
# Default: auto-detect text files, use LF
# ─────────────────────────────────────────────
* text=auto eol=lf

# ─────────────────────────────────────────────
# Java source & config
# ─────────────────────────────────────────────
*.java        text eol=lf
*.xml         text eol=lf
*.properties  text eol=lf
*.yml         text eol=lf
*.yaml        text eol=lf
*.json        text eol=lf
*.sql         text eol=lf
*.html        text eol=lf
*.md          text eol=lf

# ─────────────────────────────────────────────
# Windows scripts need CRLF
# ─────────────────────────────────────────────
*.bat         text eol=crlf
*.cmd         text eol=crlf

# ─────────────────────────────────────────────
# Binary files — never touch line endings
# ─────────────────────────────────────────────
*.jar         binary
*.war         binary
*.class       binary
*.png         binary
*.jpg         binary
*.jpeg        binary
*.gif         binary
*.ico         binary
*.pdf         binary
*.zip         binary
*.keystore    binary
```

### Step 3 — Verify it's working

```bash
# Check what attributes Git applies to a specific file
git check-attr -a src/main/java/App.java
# Output:
# src/main/java/App.java: text: set
# src/main/java/App.java: eol: lf

# Renormalize existing files in the repo (run once after adding .gitattributes)
git add --renormalize .
git commit -m "chore: normalize line endings via .gitattributes"
```

> ⚠️ The `--renormalize` step is important. Without it, already-committed files with wrong line endings stay unchanged in Git's index until they are next modified.

---

## Diff and Merge Control

### Custom Diff Rendering

By default, `git diff` shows raw line changes. You can tell Git to use **language-aware diff drivers** so diffs are more meaningful:

```gitattributes
# Show function/method names in diff hunks
*.java    diff=java
*.xml     diff=xml
*.html    diff=html
*.css     diff=css
*.md      diff=markdown
```

**Before** (without `diff=java`):
```diff
@@ -45,6 +45,7 @@
     private String name;
```

**After** (with `diff=java`):
```diff
@@ -45,6 +45,7 @@ public class UserService {
     private String name;
```

Now the diff hunk header tells you *which class or method* the change is in — huge help during code review.

### Merge Strategy

Some files cause meaningless merge conflicts every time because they are **auto-generated**. Tell Git to resolve them automatically:

```gitattributes
# Auto-generated lock files — always keep "our" version on merge
# (regenerate them properly after merging instead)
package-lock.json  merge=ours
yarn.lock          merge=ours

# Maven wrapper — rarely changes, prefer ours
.mvn/wrapper/maven-wrapper.properties  merge=ours
```

> ⚠️ Use `merge=ours` carefully. It means "in a merge conflict, silently take our side." Only use it on files that are **always regenerated** as part of your build process.

---

## Binary vs Text Files

Git needs to know whether a file is **text** or **binary** to decide:
- Whether to apply line-ending conversion
- Whether to show it in `git diff`
- Whether to attempt a text merge

Marking binary files correctly prevents Git from corrupting them or producing useless diffs:

```gitattributes
# Java build artifacts
*.jar     binary
*.war     binary
*.ear     binary
*.class   binary

# Images
*.png     binary
*.jpg     binary
*.jpeg    binary
*.gif     binary
*.ico     binary
*.svg     text eol=lf   ← SVG is actually XML text!

# Certificates and keystores
*.p12     binary
*.jks     binary
*.keystore binary

# Documents
*.pdf     binary
*.docx    binary
*.xlsx    binary
```

### What happens if you get this wrong?

```bash
# ❌ Without marking .jar as binary:
git diff mylib.jar
# Shows thousands of garbled lines of "text" changes
# Git may also corrupt the file's line endings on checkout
```

---

## Export and Archive Control

When you run `git archive` to create a release package (e.g. a `.zip` or `.tar.gz`), you usually don't want to include development-only files.

```gitattributes
# Exclude from git archive / release zips
.gitattributes    export-ignore
.gitignore        export-ignore
.github/          export-ignore
.mvn/             export-ignore
src/test/         export-ignore
*.md              export-ignore
docker-compose.yml export-ignore
Dockerfile        export-ignore
```

**Create a clean release archive:**
```bash
git archive HEAD --output=myapp-1.0.0.zip
# Only ships production code — no tests, no CI config, no docs
```

---

## Project Structure

Here is what a well-organized Java project looks like with `.gitattributes`:

```
your-project/
│
├── src/
│   ├── main/java/         ← *.java → text eol=lf
│   └── test/java/         ← *.java → text eol=lf, export-ignore
│
├── .gitattributes         ← ✅ always committed
├── .gitignore             ← ✅ always committed
│
├── pom.xml                ← *.xml → text eol=lf, diff=xml
│
├── docker-compose.yml     ← *.yml → text eol=lf, export-ignore
│
└── src/main/resources/
    ├── application.yml    ← *.yml → text eol=lf
    └── banner.png         ← *.png → binary
```

**Key rule:** Unlike `.env` files, `.gitattributes` is **always committed to Git**. It is not secret — it defines how Git behaves for everyone on the team.

---

## Best Practices

| ✅ Do | ❌ Don't |
|---|---|
| Always commit `.gitattributes` to Git | Put it in `.gitignore` |
| Set `* text=auto eol=lf` as a catch-all | Rely on each developer's `core.autocrlf` |
| Explicitly mark binary files as `binary` | Let Git guess whether `.jar` is text or binary |
| Run `git add --renormalize .` after adding the file | Wonder why old files still have wrong line endings |
| Mark auto-generated lock files with `merge=ours` | Resolve the same merge conflict every sprint |
| Use `diff=java` for smarter code review diffs | Review diffs with no method context |
| Add `export-ignore` for dev-only files | Ship test code and CI config in release zips |
| Keep it in the repo root next to `.gitignore` | Scatter multiple `.gitattributes` files in subdirs |

---

## Quick Reference Cheatsheet

```
# Create the file
touch .gitattributes

# Catch-all: auto-detect text files, store as LF
* text=auto eol=lf

# Mark a file as text with LF
*.java    text eol=lf

# Mark a file as binary (never touch line endings or diff)
*.jar     binary

# Windows-only scripts
*.bat     text eol=crlf

# Smarter diffs (shows method/class names in hunk headers)
*.java    diff=java
*.xml     diff=xml

# Skip conflicts on auto-generated files
package-lock.json  merge=ours

# Exclude from `git archive` release packages
src/test/          export-ignore
.github/           export-ignore

# Check what attributes apply to a file
git check-attr -a src/main/java/App.java

# Renormalize all files after adding/changing .gitattributes
git add --renormalize .
git commit -m "chore: normalize line endings"
```

---

## Summary

```
┌────────────────────────────────────────────────────────────┐
│                    .gitattributes                          │
│             (committed, shared with the team)              │
└──────────┬───────────┬───────────────┬─────────────────────┘
           │           │               │
           ▼           ▼               ▼
    Line Endings    Diffs          Merge & Export
    ──────────      ──────────     ──────────────
    *.java → LF    *.java →       lock files →
    *.bat  → CRLF  show method    merge=ours
    *.jar  →       context in     src/test/ →
    binary         reviews        export-ignore
```

> 💡 `.gitattributes` is your repository's **traffic control layer** — telling Git exactly how to handle every file type so that line endings, diffs, merges, and releases all behave consistently for every developer on every machine.

---

*Guide covers: Git 2.x · Java 17+ · Maven · Windows / macOS / Linux cross-platform teams*