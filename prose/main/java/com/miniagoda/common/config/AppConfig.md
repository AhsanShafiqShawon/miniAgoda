# AppConfig — Code Prose

`com.miniagoda.common.config.AppConfig`

---

## `passwordEncoder()`

This method has one job: give the application a single, consistent way to hash passwords.

It returns a `BCryptPasswordEncoder` — a concrete implementation of Spring's `PasswordEncoder` interface. BCrypt is chosen deliberately here because it does not just hash; it also salts automatically, meaning two users with the same password will produce two completely different hashes in the database.

The method is annotated with `@Bean`, which means Spring calls this method once at startup and registers the returned encoder into its application context. From that point on, any class in the application that declares a `PasswordEncoder` dependency — a registration service, an authentication manager, a password reset flow — gets handed this exact instance. Nobody instantiates it manually; Spring wires it in.

The class itself is annotated with `@Configuration`, which tells Spring to treat it as a source of bean definitions rather than a regular component. This is what makes the `@Bean` annotation on the method meaningful.

So in practice: when a user registers and their raw password needs to be stored, the service handling that request injects this encoder, calls `.encode(rawPassword)`, and saves the result. The plain-text password never touches the database.

# AppConfig — Plain English Breakdown

---

## 🧠 Big Picture

Think of this class as the **central supply room** for shared tools your app needs.

Right now it only stocks one thing — a password encoder. But as the app grows, other app-wide utilities get registered here too. Instead of every class creating its own tools, they all come to this one place and ask Spring for what they need.

| Concept | What it is |
|---|---|
| `AppConfig` | A class that registers shared, reusable tools into Spring |
| `passwordEncoder()` | Registers the tool responsible for hashing passwords |

---

## 🧩 Step-by-Step in Plain Terms

### 1. `@Configuration` — this is a settings class, not a regular class

This annotation tells Spring:

> *"Don't treat this like a normal component. Treat it as a source of bean definitions."*

That distinction matters because Spring processes `@Configuration` classes specially — it ensures that `@Bean` methods inside it behave correctly and that the same instance is returned every time something asks for it.

---

### 2. `passwordEncoder()` — the one method

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

This method has one job: tell Spring what to use whenever something asks for a `PasswordEncoder`.

The `@Bean` annotation means Spring calls this method once at startup, takes the object it returns, and registers it into the application context. From that point on, any class that declares a `PasswordEncoder` dependency gets this exact instance handed to it automatically. Nobody calls `new BCryptPasswordEncoder()` anywhere else in the codebase — Spring handles it.

The implementation chosen is `BCryptPasswordEncoder`. BCrypt doesn't just hash — it also salts automatically. Every time you encode the same password, you get a different result. That means even if two users have the same password, their stored hashes look completely different. An attacker who gets the database cannot use a precomputed table of hashes to reverse them.

---

## 🔥 One-Line Summary

> This class registers a BCrypt password encoder into Spring once at startup so every part of the app that needs to hash or verify passwords gets the same, consistent tool.

---

## 💡 Deep Dive: Why BCrypt?

Not all hashing algorithms are equal. Some are designed for speed — MD5, SHA-256. Speed is great for checksums and data integrity. It is terrible for passwords.

---

### ❌ The problem with fast hashing

A fast hashing algorithm can compute billions of hashes per second on modern hardware. If an attacker gets your database, they can run every common password through the algorithm at enormous speed and find matches.

```
"password123" → MD5 → 482c811da5d5b4bc...  ← computed in nanoseconds
```

With a big enough wordlist and enough hardware, fast hashing offers very little real protection.

---

### ✅ Why BCrypt is different

BCrypt is deliberately slow. It has a built-in cost factor — a number that controls how many rounds of computation are needed to produce a hash. The higher the cost, the longer it takes.

On your laptop, one BCrypt hash might take 100ms. That feels instant to a user logging in. But for an attacker trying millions of passwords, 100ms per attempt makes brute-forcing impractical.

The cost factor can also be increased over time as hardware gets faster — without changing the algorithm. Old hashes stay valid; new ones just take a little longer to produce.

---

### The three things BCrypt does that matter

| Feature | What it means |
|---|---|
| Automatic salting | Same password → different hash every time |
| Adaptive cost | Slows down as needed to keep pace with faster hardware |
| One-way | You can verify a password against a hash, but you cannot reverse the hash to get the password back |

---

### How it actually gets used

Once this bean is registered, a service like `UserService` can declare it as a dependency:

```java
private final PasswordEncoder passwordEncoder;
```

Spring injects it automatically. When a user registers, the service calls:

```java
String hashed = passwordEncoder.encode(rawPassword);
```

When a user logs in, it calls:

```java
boolean matches = passwordEncoder.matches(rawPassword, storedHash);
```

The plain-text password never touches the database. Not even once.

---

### The analogy

| Thing | Analogy |
|---|---|
| Fast hashing (MD5) | A combination lock where the combination is taped to the front — technically locked, trivially broken |
| BCrypt | A safe that takes 10 seconds to open — fine for one user, completely impractical for an attacker trying a million combinations |
| Salt | Even if two people have the same combination, their safes look completely different from the outside |

---

### Final one-liner

> BCrypt is slow by design, salts automatically, and can get slower over time — and that is exactly what you want when the thing being protected is a user's password.