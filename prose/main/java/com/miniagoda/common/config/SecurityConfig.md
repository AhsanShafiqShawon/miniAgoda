# SecurityConfig — Code Prose

`com.miniagoda.common.config.SecurityConfig`

---

## Overview

This class defines the HTTP security rules for the application. Spring Security calls `securityFilterChain` once at startup, and the resulting `SecurityFilterChain` becomes the gatekeeper for every incoming HTTP request.

It depends on `JwtAuthenticationFilter`, which is injected through the constructor and inserted into the filter chain.

---

## `securityFilterChain(HttpSecurity http)`

The method configures four things in sequence.

**CSRF is disabled.** This is intentional for a stateless REST API. CSRF attacks exploit session cookies, and since this application transmits JWTs in `Authorization` headers — never in cookies — there is nothing for an attacker to exploit. The protection is unnecessary overhead.

**Sessions are set to `STATELESS`.** Spring Security will never create or consult an `HttpSession`. Every request must carry its own proof of identity in the form of a JWT. This is the standard posture for a JWT-based API and allows the application to scale horizontally without shared session state.

**Authorization rules are configured by route.** Public routes — auth endpoints, hotel reads, and search reads — are open to all. Host-only and admin-only routes are gated by role. Everything else requires an authenticated user.

| Pattern | Access |
|---------|--------|
| `POST /api/auth/**` | Public |
| `GET /api/hotels/**` | Public |
| `GET /api/search/**` | Public |
| `/api/host/**` | `ROLE_HOST` |
| `/api/admin/**` | `ROLE_ADMIN` |
| Everything else | Authenticated |

**`JwtAuthenticationFilter` is inserted before `UsernamePasswordAuthenticationFilter`.** This ensures the JWT is extracted and validated early in the filter chain, before Spring attempts any other form of authentication. If the token is missing or invalid, the filter writes a `401 ErrorResponse` directly to the response and the request never reaches the controller.

Finally, `http.build()` assembles everything into a `SecurityFilterChain` and returns it to Spring as the active security policy.

# SecurityConfig — Plain English Breakdown

---

## 🧠 Big Picture

Think of your app like a club with a security guard at the door.

| Concept | What it is |
|---|---|
| `SecurityConfig` | The rules you give to the guard |
| `SecurityFilterChain` | The actual guard behavior |

---

## 🚪 `securityFilterChain(HttpSecurity http)`

This method is basically:

> *"Hey Spring, here are the rules for handling every incoming request."*

Spring runs this **once** when the app starts. After that, every request follows these rules.

---

## 🧩 Step-by-Step in Plain Terms

### 1. `HttpSecurity http` — the settings panel

Think of this like a configuration panel where you decide:

- How sessions work
- What login style to use
- Who is allowed in

---

### 2. ❌ CSRF is disabled

**What is CSRF?**
CSRF is a protection against attacks that exploit browser cookies — specifically, an attacker tricking your browser into sending a request without you realizing it.

**Classic CSRF scenario:**
1. You're logged into a banking site
2. The site uses cookies to remember you
3. You visit a malicious site
4. That site secretly sends: *"transfer money"*
5. Your browser auto-attaches your cookie — the bank thinks it's you

💥 That's CSRF.

**Why your app disables it:**

Your app does not use cookies or sessions. It uses JWT tokens sent manually in request headers. The browser does NOT auto-send those — so an attacker can't forge your identity.

> No cookies → no auto-sending → nothing to steal → CSRF protection unnecessary

---

### 3. 🚫 Sessions set to `STATELESS`

**Traditional (session-based) approach:**
1. User logs in
2. Server creates a session and remembers the user
3. Server gives back a cookie
4. Browser sends that cookie automatically every time

**Your app's approach:**

> *"Don't remember anything. Treat every request like a stranger."*

Every request must bring its own proof — a JWT token. The server verifies it and moves on, no memory kept.

**Why this is better for a scalable system like Agoda:**
- Multiple servers can handle requests without sharing session state
- No central session storage needed
- Cleaner, more scalable architecture

| Style | Analogy |
|---|---|
| Session-based | The club remembers your face after the first visit |
| Stateless (JWT) | You show your ID card every single time |

---

### 4. 🔓 `anyRequest().permitAll()` — let everyone in for now

Right now, **no requests are blocked**. This is a temporary placeholder.

Why? Because the real security — the `JwtAuthFilter` — hasn't been built yet. If you locked things down now, even you as the developer couldn't access anything. So `permitAll()` keeps the door open during early development.

---

### 5. 🔜 The future access rules

Once the JWT system is in place, the rules will look like this:

| Route | Who can access |
|---|---|
| `POST /api/auth/**` | Everyone (login, register) |
| `GET /api/hotels/**` | Everyone |
| `GET /api/search/**` | Everyone |
| `/api/host/**` | Hosts only |
| `/api/admin/**` | Admins only |
| Everything else | Logged-in users only |

---

### 6. 🔐 The missing piece: `JwtAuthFilter`

This is the future "ID checker." It will sit in front of every request and do the following:

1. Read the `Authorization` header
2. Extract the JWT token: `Bearer <token>`
3. Validate the token
4. Identify the user
5. Tell Spring: *"This request is from user X"*

**Current flow:**
```
Incoming request → SecurityFilterChain → allow everything
```

**Future flow:**
```
Incoming request
        ↓
JwtAuthFilter (check and validate token)
        ↓
Security rules (who can access what)
        ↓
Controller
```

---

### 7. 📍 Where the filter will go

`JwtAuthFilter` will be inserted **before** `UsernamePasswordAuthenticationFilter`.

Why? Because you want JWT authentication to run early — before Spring tries any other form of authentication check.

---

### 8. 🏗️ `http.build()`

This just means:

> *"I'm done configuring. Build the security system."*

It finalises all the rules and returns a `SecurityFilterChain` that Spring registers as the active policy.

---

## 🔥 One-Line Summary

> This class sets up your app to not use sessions, not care about CSRF, allow everything for now, and later plug in JWT-based security.

---

## 💡 Deep Dive: Does JWT Prevent CSRF?

**Short answer: not by itself. It depends on *how* you use the JWT.**

### ❌ The wrong mental model

> "JWT = no CSRF"

### ✅ The correct mental model

> "CSRF depends on how authentication is *sent*, not on whether you use JWT."

---

### The real rule

CSRF happens when:
- Auth is stored in a cookie
- The browser auto-sends that cookie with every request

That auto-sending is what the attacker exploits. JWT doesn't change that on its own.

---

### Two JWT setups compared

**❌ Case 1: JWT stored in a cookie**

The server puts the JWT inside a cookie. The browser auto-sends it. An attacker can still trigger requests that carry that cookie — the server trusts them.

💥 CSRF is still possible.

**✅ Case 2: JWT stored manually, sent in headers**

The frontend stores the JWT (e.g., in `localStorage`) and sends it like this:

```
Authorization: Bearer <token>
```

The browser does NOT auto-send this. An attacker cannot force your browser to attach it to a forged request.

✅ CSRF is not possible.

---

### What your app does

| Setting | Value |
|---|---|
| Cookies | ❌ Not used |
| Sessions | ❌ Not used |
| JWT location | ✅ Request header |
| Requests | ✅ Stateless |

Because auth travels in headers — not cookies — there is nothing for an attacker to exploit. CSRF protection is genuinely unnecessary, so it is disabled.

---

### The analogy

| Auth style | Analogy |
|---|---|
| Cookie-based | Your ID is automatically clipped to every letter you send — an attacker can send letters pretending to be you |
| Header-based JWT | You manually hand over your ID each time — no one can force your hand |

---

### Final one-liner

> JWT does **not** prevent CSRF by itself. Using JWT **in headers** (not cookies) prevents CSRF — and that is exactly what this app does.