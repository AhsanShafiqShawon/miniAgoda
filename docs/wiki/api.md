# Java API Guide — From Zero to Working Knowledge

A complete guide to understanding and using APIs in Java, covering everything from basic concepts to production-ready patterns.

---

## Table of Contents

1. [What is an API?](#1-what-is-an-api)
2. [Building an HTTP Request in Java](#2-building-an-http-request-in-java)
3. [What Happens When You Send an HTTP Request?](#3-what-happens-when-you-send-an-http-request)
4. [How to Check a Response Status Code](#4-how-to-check-a-response-status-code)
5. [Parsing JSON with Jackson](#5-parsing-json-with-jackson)
6. [Building URLs with Query Parameters](#6-building-urls-with-query-parameters)
7. [HTTP Methods — When to Use Each](#7-http-methods--when-to-use-each)
8. [Common HTTP Headers](#8-common-http-headers)
9. [Request Bodies — When and How to Format Them](#9-request-bodies--when-and-how-to-format-them)

---

## 1. What is an API?

### The Restaurant Analogy

Imagine you're at a restaurant. You don't walk into the kitchen and cook your own food. Instead, you tell the **waiter** what you want, the waiter tells the kitchen, and the kitchen sends back your food.

An **API (Application Programming Interface)** is that waiter — a middleman that lets two software systems talk to each other without needing to know how the other one works internally.

### The Three Parts of Every API Conversation

1. **Request** — you ask for something
2. **Response** — you get something back
3. **Endpoint** — the specific "address" you talk to

```
Your App  →  [Request]  →  API  →  Service
Your App  ←  [Response] ←  API  ←  Service
```

### Your First API Call in Java

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MyFirstAPI {
    public static void main(String[] args) throws Exception {

        // Step 1: Create an HTTP client (your messenger)
        HttpClient client = HttpClient.newHttpClient();

        // Step 2: Build the request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://uselessfacts.jsph.pl/api/v2/facts/random"))
            .GET()
            .build();

        // Step 3: Send the request and get the response
        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());

        // Step 4: Print what came back
        System.out.println("Status: " + response.statusCode());
        System.out.println("Body: " + response.body());
    }
}
```

The response comes back as **JSON** — the most common format APIs use to send data:

```json
{
  "id": "efca9f87-2b0b-4b67-88bf-e6be96157bcc",
  "text": "A group of flamingos is called a 'flamboyance'.",
  "source": "djtech.net"
}
```

### Status Codes at a Glance

| Range | Meaning | Examples |
|-------|---------|---------|
| `2xx` | Success | 200 OK, 201 Created |
| `4xx` | Client error (your fault) | 401 Unauthorized, 404 Not Found, 429 Too Many Requests |
| `5xx` | Server error (their fault) | 500 Internal Server Error |

### Key Tip: Reuse Your `HttpClient`

Create it **once** as a static field and reuse it for every request:

```java
private static final HttpClient CLIENT = HttpClient.newHttpClient();
```

---

## 2. Building an HTTP Request in Java

Every HTTP request has four possible parts:

| Part | Required? | Purpose |
|------|-----------|---------|
| URL / endpoint | Always | Where to send the request |
| Method | Always | What action to perform (GET, POST, etc.) |
| Headers | Usually | Auth keys, content type, etc. |
| Body | POST/PUT/PATCH only | Data you're sending |

### 1. The URL

A URL has several meaningful parts:

```
https://api.example.com/users/42?format=json&limit=10
│       │               │        │
scheme  host            path     query parameters
```

```java
// Simple URL
URI simple = URI.create("https://api.example.com/users/42");

// URL with query parameters
String url = "https://api.example.com/users?format=json&limit=10";
URI withParams = URI.create(url);
```

### 2. The Method

```java
// GET (default — can be omitted)
HttpRequest.newBuilder().uri(...).GET().build();

// POST
HttpRequest.newBuilder().uri(...)
    .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();

// DELETE
HttpRequest.newBuilder().uri(...).DELETE().build();

// PATCH (no shortcut — use .method())
HttpRequest.newBuilder().uri(...)
    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody)).build();
```

### 3. Headers

```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .header("Content-Type", "application/json")
    .header("Authorization", "Bearer your-api-key")
    .header("Accept", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
    .build();
```

### 4. Timeouts — Don't Wait Forever

Always set timeouts so a slow server doesn't freeze your app:

```java
HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))   // time to establish connection
    .build();

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .timeout(Duration.ofSeconds(10))          // time to wait for response
    .GET()
    .build();
```

### Complete, Production-Style Request

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class CompleteRequest {
    public static void main(String[] args) {

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        String body = """
            {
                "name": "Alice",
                "email": "alice@example.com"
            }
            """;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/users"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + System.getenv("API_KEY"))
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        try {
            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                System.out.println("User created: " + response.body());
            } else {
                System.out.println("Error " + response.statusCode() + ": " + response.body());
            }

        } catch (Exception e) {
            System.out.println("Request failed: " + e.getMessage());
        }
    }
}
```

---

## 3. What Happens When You Send an HTTP Request?

When you call `client.send(request, ...)`, here is what actually happens under the hood:

### Stage 1 — Your Java code calls `client.send()`
Your thread blocks and waits. Nothing has left your machine yet.

```java
HttpResponse<String> response = client.send(request,
    HttpResponse.BodyHandlers.ofString());
// ← your thread blocks here until done
```

### Stage 2 — DNS lookup (hostname → IP address)
The OS resolves `"api.example.com"` to an actual IP like `"104.21.33.10"`. Java does this automatically. Results are cached so repeated calls skip this step.

### Stage 3 — TCP + TLS (open a secure connection)
A TCP connection is established to port 443. Then a TLS handshake occurs — certificates are exchanged and encryption keys are agreed upon. Your `connectTimeout` applies here.

```java
HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .build();
```

### Stage 4 — Request travels over the internet
Your request is broken into packets and routed through multiple servers to reach the destination. Typically takes 10–300ms.

```
// The raw bytes sent look like:
POST /users HTTP/1.1
Host: api.example.com
Content-Type: application/json
Authorization: Bearer abc123

{"name":"Alice","email":"a@b.com"}
```

### Stage 5 — Server processes your request
The server checks your API key, runs its logic, reads from a database, and builds a response. This is where most of the waiting time is spent. Your `request timeout` applies here.

### Stage 6 — Response travels back
The server sends back a status code, headers, and a JSON body. Java reassembles the packets into the `HttpResponse` object.

```
HTTP/1.1 201 Created
Content-Type: application/json

{"id": 42, "name": "Alice"}
```

### Stage 7 — Your thread unblocks
`client.send()` returns. You now have your `HttpResponse` object.

### When Things Go Wrong

Always wrap `client.send()` in try-catch:

```java
try {
    HttpResponse<String> r = client.send(request,
        HttpResponse.BodyHandlers.ofString());

    if (r.statusCode() >= 400) {
        System.out.println("API error: " + r.statusCode());
    }
} catch (java.net.ConnectException e) {
    System.out.println("Can't reach server");
} catch (java.net.http.HttpTimeoutException e) {
    System.out.println("Server too slow");
} catch (Exception e) {
    System.out.println("Something broke: " + e.getMessage());
}
```

### Blocking vs Async

`client.send()` is **synchronous** — your thread waits. For parallel requests, use **async**:

```java
client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
    .thenAccept(response -> {
        System.out.println(response.body());
    });

System.out.println("Request sent, not waiting...");
```

---

## 4. How to Check a Response Status Code

```java
HttpResponse<String> response = client.send(request,
    HttpResponse.BodyHandlers.ofString());

int status = response.statusCode();  // e.g. 200, 404, 500
```

### Level 1 — Basic Check

```java
if (response.statusCode() == 200) {
    System.out.println("Success: " + response.body());
} else {
    System.out.println("Something went wrong: " + response.statusCode());
}
```

### Level 2 — Check Ranges (better)

```java
int status = response.statusCode();

if (status >= 200 && status < 300) {
    System.out.println("Success: " + response.body());
} else if (status >= 400 && status < 500) {
    System.out.println("Client error " + status + ": " + response.body());
} else if (status >= 500) {
    System.out.println("Server error " + status + ": " + response.body());
}
```

### Level 3 — Production-Ready with Specific Handling

```java
switch (status / 100) {   // integer division groups by hundreds
    case 2 -> {
        System.out.println("Got data: " + response.body());
    }
    case 4 -> {
        if (status == 401) {
            System.out.println("Bad API key — check your credentials");
        } else if (status == 404) {
            System.out.println("That resource doesn't exist");
        } else if (status == 429) {
            System.out.println("Rate limited — slow down your requests");
        } else {
            System.out.println("Request error " + status + ": " + response.body());
        }
    }
    case 5 -> {
        System.out.println("Server error " + status + " — try again later");
    }
    default -> {
        System.out.println("Unexpected status: " + status);
    }
}
```

> **Tip:** `status / 100` is a clean trick — `201 / 100 = 2`, `404 / 100 = 4`, `503 / 100 = 5`.

### A Reusable Helper Method

```java
public class ApiHelper {

    public static String checkAndGet(HttpResponse<String> response) {
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            return response.body();
        }

        String error = switch (status) {
            case 400 -> "Bad request — check your data";
            case 401 -> "Unauthorized — check your API key";
            case 403 -> "Forbidden — you don't have permission";
            case 404 -> "Not found — check the URL";
            case 429 -> "Rate limited — too many requests";
            case 500 -> "Server error — try again later";
            case 503 -> "Service unavailable — try again later";
            default  -> "Unexpected error";
        };

        throw new RuntimeException("API error " + status + ": " + error
                                   + "\nBody: " + response.body());
    }
}

// Usage — two clean lines for every call:
HttpResponse<String> response = client.send(request,
    HttpResponse.BodyHandlers.ofString());
String body = ApiHelper.checkAndGet(response);
```

### Always Read the Error Body

```java
if (status >= 400) {
    // APIs put the real reason here — always print it
    System.out.println("Error body: " + response.body());
    // e.g. {"error": "Invalid email format", "field": "email"}
}
```

---

## 5. Parsing JSON with Jackson

### Add Jackson to Your Project

**Maven (`pom.xml`):**
```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.1</version>
</dependency>
```

**Gradle (`build.gradle`):**
```groovy
implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.1'
```

### Two Approaches

| Approach | Best For |
|----------|---------|
| `JsonNode` (tree model) | Quick reads, exploring the API, only need a few fields |
| Java class (data binding) | Real projects, reusing data throughout the app, type safety |

### Approach 1 — JsonNode

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

String json = """
    {
        "id": 42,
        "name": "Alice",
        "email": "alice@example.com",
        "active": true
    }
    """;

ObjectMapper mapper = new ObjectMapper();
JsonNode root = mapper.readTree(json);

int     id     = root.get("id").asInt();
String  name   = root.get("name").asText();
String  email  = root.get("email").asText();
boolean active = root.get("active").asBoolean();
```

**`.as___()` conversion methods:**

| Method | Returns |
|--------|---------|
| `.asText()` | `String` |
| `.asInt()` | `int` |
| `.asLong()` | `long` |
| `.asDouble()` | `double` |
| `.asBoolean()` | `boolean` |

### Navigating Nested JSON

```java
String json = """
    {
        "user": {
            "name": "Alice",
            "address": {
                "city": "Bangkok",
                "country": "Thailand"
            }
        }
    }
    """;

ObjectMapper mapper = new ObjectMapper();
JsonNode root = mapper.readTree(json);

String name    = root.get("user").get("name").asText();
String city    = root.get("user").get("address").get("city").asText();
String country = root.get("user").get("address").get("country").asText();
```

### Handling JSON Arrays

```java
JsonNode items = root.get("items");

for (JsonNode item : items) {
    int    id   = item.get("id").asInt();
    String name = item.get("name").asText();
    System.out.println(id + ": " + name);
}
```

### Approach 2 — Mapping to a Java Class

**Step 1 — Define a class mirroring the JSON:**

```java
public class User {
    public int id;
    public String name;
    public String email;
    public boolean active;

    public User() {}  // Jackson needs this
}
```

**Step 2 — Deserialize:**

```java
ObjectMapper mapper = new ObjectMapper();
User user = mapper.readValue(json, User.class);

System.out.println(user.name);   // Alice
System.out.println(user.email);  // alice@example.com
```

### Mapping a JSON Array to a List

```java
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;

List<User> users = mapper.readValue(json, new TypeReference<List<User>>() {});

for (User u : users) {
    System.out.println(u.id + ": " + u.name);
}
```

### Complete Round-Trip Example

```java
import java.net.URI;
import java.net.http.*;
import com.fasterxml.jackson.databind.*;

public class FetchUser {

    static HttpClient   client = HttpClient.newHttpClient();
    static ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://jsonplaceholder.typicode.com/users/1"))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.out.println("Error: " + response.statusCode());
            return;
        }

        JsonNode root = mapper.readTree(response.body());
        System.out.println("Name: "  + root.get("name").asText());
        System.out.println("Email: " + root.get("email").asText());
        System.out.println("City: "  + root.get("address").get("city").asText());
    }
}
```

### Safe Null Handling

```java
// .get() returns null if the field doesn't exist — guard against it
JsonNode nameNode = root.get("name");
if (nameNode != null && !nameNode.isNull()) {
    System.out.println(nameNode.asText());
}
```

> **Tip:** Create `ObjectMapper` once as a static field and reuse it — it's expensive to create.

---

## 6. Building URLs with Query Parameters

### Why Not String Concatenation?

```java
// DANGEROUS — breaks if userInput contains &, ?, spaces, etc.
String url = "https://api.example.com/search?q=" + userInput + "&limit=10";
```

The search term `coffee & tea` needs to become `coffee+%26+tea`. Always encode properly.

### Option 1 — `URLEncoder` (no extra dependencies)

```java
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

String query   = URLEncoder.encode("coffee & tea", StandardCharsets.UTF_8);
String country = URLEncoder.encode("Thailand",     StandardCharsets.UTF_8);

String url = "https://api.example.com/search"
           + "?q="       + query
           + "&country=" + country;
```

> **Rules:** Encode **values** only, never keys or `?`/`&` separators. Always use `StandardCharsets.UTF_8`.

### Building from a Map (cleaner for many params)

```java
public static String buildUrl(String base, Map<String, String> params) {
    if (params.isEmpty()) return base;

    String query = params.entrySet().stream()
        .map(e -> URLEncoder.encode(e.getKey(),   StandardCharsets.UTF_8)
             + "=" +
             URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));

    return base + "?" + query;
}

// Usage
String url = buildUrl("https://api.example.com/search", Map.of(
    "q",      "coffee & tea",
    "limit",  "10",
    "page",   "2"
));
```

### Option 2 — `UriComponentsBuilder` (Spring projects)

```java
import org.springframework.web.util.UriComponentsBuilder;

String url = UriComponentsBuilder
    .fromHttpUrl("https://api.example.com/search")
    .queryParam("q",       "coffee & tea")
    .queryParam("country", "Thailand")
    .queryParam("limit",   10)
    .toUriString();
```

### Option 3 — `URIBuilder` (Apache HttpComponents)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
    <version>5.3.1</version>
</dependency>
```

```java
import org.apache.hc.core5.net.URIBuilder;

URI uri = new URIBuilder("https://api.example.com/search")
    .addParameter("q",       "coffee & tea")
    .addParameter("country", "Thailand")
    .addParameter("limit",   "10")
    .build();

// Pass directly to HttpRequest — no conversion needed
HttpRequest request = HttpRequest.newBuilder()
    .uri(uri)
    .GET()
    .build();
```

### Handling Optional Parameters

```java
URIBuilder builder = new URIBuilder("https://api.example.com/search")
    .addParameter("q",     searchTerm)
    .addParameter("limit", String.valueOf(limit));

if (country != null && !country.isBlank()) {
    builder.addParameter("country", country);
}

URI uri = builder.build();
```

---

## 7. HTTP Methods — When to Use Each

### The 5 Core Methods

| Method | Action | Has Body? | Idempotent? |
|--------|--------|-----------|-------------|
| `GET` | Fetch data — read only | No | Yes |
| `POST` | Create something new | Yes | No |
| `PUT` | Replace a resource completely | Yes | Yes |
| `PATCH` | Update part of a resource | Yes | No |
| `DELETE` | Remove a resource | Usually no | Yes |

### GET — Fetch Data

```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users/42"))
    .GET()   // optional — GET is the default
    .build();
```

### POST — Create Something New

```java
String newUser = """
    {
        "name":  "Alice",
        "email": "alice@example.com"
    }
    """;

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(newUser))
    .build();
// Server responds with 201 Created
```

### PUT — Replace Completely

```java
// Must send the FULL object — missing fields may be deleted
String fullUser = """
    {
        "id":    42,
        "name":  "Alice Smith",
        "email": "alice@example.com",
        "role":  "admin"
    }
    """;

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users/42"))
    .header("Content-Type", "application/json")
    .PUT(HttpRequest.BodyPublishers.ofString(fullUser))
    .build();
```

### PATCH — Update Partially

```java
// Only send what you want to change
String nameChange = """
    {
        "name": "Alice Smith"
    }
    """;

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users/42"))
    .header("Content-Type", "application/json")
    .method("PATCH", HttpRequest.BodyPublishers.ofString(nameChange))
    .build();
// Note: Java has no .patch() shortcut — use .method()
```

### DELETE — Remove a Resource

```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users/42"))
    .DELETE()
    .build();

// Common responses:
// 200 OK         — deleted, returns confirmation
// 204 No Content — deleted, no body
// 404 Not Found  — already gone
```

### The REST Pattern — One URL, Multiple Methods

```
GET    /users       → get list of all users
POST   /users       → create a new user

GET    /users/42    → get user 42
PUT    /users/42    → replace user 42 entirely
PATCH  /users/42    → update part of user 42
DELETE /users/42    → delete user 42
```

### Safe vs Idempotent

- **Safe** — never changes data. GET, HEAD, OPTIONS are safe.
- **Idempotent** — calling once or ten times has the same result. GET, PUT, DELETE are idempotent. POST is not — posting twice creates two records.

> **Retry rule:** Safe to auto-retry GET and DELETE. Do not blindly retry POST — it can create duplicates.

---

## 8. Common HTTP Headers

### Setting Headers in Java

```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .header("Authorization", "Bearer " + System.getenv("API_KEY"))
    .header("Content-Type",  "application/json")
    .header("Accept",        "application/json")
    .header("User-Agent",    "MyApp/1.0")
    .POST(HttpRequest.BodyPublishers.ofString(body))
    .build();
```

### Authorization — The Most Important Header

**Bearer token (most common):**
```java
.header("Authorization", "Bearer " + System.getenv("API_KEY"))
```

**Basic auth (username + password):**
```java
import java.util.Base64;

String credentials = "myUsername:myPassword";
String encoded = Base64.getEncoder()
    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

.header("Authorization", "Basic " + encoded)
```

**Custom header (some APIs):**
```java
.header("X-Api-Key", System.getenv("API_KEY"))
```

### Content-Type — What Format Is My Body?

Only needed on requests with a body (POST, PUT, PATCH):

```java
.header("Content-Type", "application/json")                    // JSON
.header("Content-Type", "application/x-www-form-urlencoded")   // HTML form
.header("Content-Type", "multipart/form-data")                  // File upload
.header("Content-Type", "text/plain")                           // Plain text
```

### Accept — What Format Do I Want Back?

```java
.header("Accept", "application/json")
```

### Reading Response Headers

```java
HttpResponse<String> response = client.send(request,
    HttpResponse.BodyHandlers.ofString());

response.headers().firstValue("Content-Type")
    .ifPresent(ct -> System.out.println("Response type: " + ct));

response.headers().firstValue("X-RateLimit-Remaining")
    .ifPresent(r  -> System.out.println("Requests left: " + r));

// Print all response headers (useful for debugging)
response.headers().map().forEach((name, values) ->
    System.out.println(name + ": " + values));
```

### A Reusable API Client

```java
public class ApiClient {

    private static final HttpClient CLIENT  = HttpClient.newHttpClient();
    private static final String     API_KEY = System.getenv("API_KEY");
    private static final String     BASE_URL = "https://api.example.com";

    private static HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .header("Authorization", "Bearer " + API_KEY)
            .header("Accept",        "application/json")
            .header("User-Agent",    "MyApp/1.0")
            .timeout(Duration.ofSeconds(10));
    }

    public static HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = baseRequest(path).GET().build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest request = baseRequest(path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

// Usage
HttpResponse<String> response = ApiClient.get("/users/42");
HttpResponse<String> created  = ApiClient.post("/users", """
    {"name": "Alice", "email": "alice@example.com"}
""");
```

### Security Rule — Never Hardcode API Keys

```java
// Good
String apiKey = System.getenv("MY_API_KEY");

// Also good — from a properties file
Properties props = new Properties();
props.load(new FileInputStream("config.properties"));
String apiKey = props.getProperty("api.key");

// NEVER do this
String apiKey = "sk-abc123realkey...";  // exposed if code is shared
```

---

## 9. Request Bodies — When and How to Format Them

### When Do You Need a Body?

| Method | Needs body? |
|--------|-------------|
| `POST` | Yes |
| `PUT` | Yes |
| `PATCH` | Yes |
| `GET` | No — use URL / query params |
| `DELETE` | No — identify via URL |

### Format 1 — JSON (most common)

```java
// Option A: write string directly (for static data)
String jsonBody = """
    {
        "name":  "Alice",
        "email": "alice@example.com",
        "age":   30
    }
    """;

// Option B: build with Jackson (for dynamic data — always prefer this)
ObjectMapper mapper = new ObjectMapper();
ObjectNode body = mapper.createObjectNode();
body.put("name",  userName);
body.put("email", userEmail);
body.put("age",   userAge);
String jsonBody = mapper.writeValueAsString(body);

// Option C: serialize a class (cleanest for real projects)
public class CreateUserRequest {
    public String name;
    public String email;
    public int    age;
}

CreateUserRequest payload = new CreateUserRequest("Alice", "alice@example.com", 30);
String jsonBody = mapper.writeValueAsString(payload);

// Send it
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
    .build();
```

### Format 2 — Form Data (HTML forms, OAuth endpoints)

```java
String formBody = "grant_type=" + URLEncoder.encode("authorization_code", StandardCharsets.UTF_8)
    + "&code="         + URLEncoder.encode(authCode,    StandardCharsets.UTF_8)
    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://oauth.example.com/token"))
    .header("Content-Type", "application/x-www-form-urlencoded")
    .POST(HttpRequest.BodyPublishers.ofString(formBody))
    .build();
```

### Format 3 — File Upload (multipart)

```java
String boundary  = "Boundary" + System.currentTimeMillis();
byte[] fileBytes = Files.readAllBytes(Path.of("/path/to/photo.jpg"));

String bodyStart = "--" + boundary + "\r\n"
    + "Content-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\n"
    + "Content-Type: image/jpeg\r\n\r\n";
String bodyEnd = "\r\n--" + boundary + "--\r\n";

byte[] start = bodyStart.getBytes(StandardCharsets.UTF_8);
byte[] end   = bodyEnd.getBytes(StandardCharsets.UTF_8);

byte[] fullBody = new byte[start.length + fileBytes.length + end.length];
System.arraycopy(start,     0, fullBody, 0,                               start.length);
System.arraycopy(fileBytes, 0, fullBody, start.length,                    fileBytes.length);
System.arraycopy(end,       0, fullBody, start.length + fileBytes.length, end.length);

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/upload"))
    .header("Authorization", "Bearer " + System.getenv("API_KEY"))
    .header("Content-Type",  "multipart/form-data; boundary=" + boundary)
    .POST(HttpRequest.BodyPublishers.ofByteArray(fullBody))
    .build();
```

### Format 4 — Plain Text / Raw Bytes

```java
// Plain text
HttpRequest textRequest = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/notes"))
    .header("Content-Type", "text/plain")
    .POST(HttpRequest.BodyPublishers.ofString("This is a plain text note."))
    .build();

// Raw bytes (e.g. binary image)
byte[] imageBytes = Files.readAllBytes(Path.of("/path/to/image.png"));
HttpRequest binaryRequest = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/images"))
    .header("Content-Type", "image/png")
    .POST(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
    .build();
```

### `BodyPublishers` Quick Reference

```java
HttpRequest.BodyPublishers.ofString("{ \"key\": \"value\" }")   // JSON / text
HttpRequest.BodyPublishers.ofByteArray(byteArray)               // Binary
HttpRequest.BodyPublishers.ofFile(Path.of("/path/to/file.json"))// File from disk
HttpRequest.BodyPublishers.noBody()                              // No body
```

### Complete Example — Create a User

```java
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class CreateUser {

    static HttpClient   client = HttpClient.newHttpClient();
    static ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        CreateUserRequest payload = new CreateUserRequest(
            "Alice", "alice@example.com", 30
        );
        String jsonBody = mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://jsonplaceholder.typicode.com/users"))
            .header("Content-Type", "application/json")
            .header("Accept",       "application/json")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201) {
            JsonNode created = mapper.readTree(response.body());
            System.out.println("Created user with ID: " + created.get("id").asInt());
        } else {
            System.out.println("Error " + response.statusCode() + ": " + response.body());
        }
    }
}
```

### The Most Common Mistake

Forgetting `Content-Type`. If you send JSON without `Content-Type: application/json`, most servers either reject the request with `400 Bad Request` or misread the body entirely.

> **Rule:** The `Content-Type` header and the body format must always agree.

---

## Free APIs to Practice With

| API | What it does | URL |
|-----|-------------|-----|
| JSONPlaceholder | Fake REST API for testing | `jsonplaceholder.typicode.com` |
| Useless Facts | Random fun facts | `uselessfacts.jsph.pl` |
| CoinDesk | Bitcoin price | `api.coindesk.com` |
| Open-Meteo | Weather (no key needed) | `api.open-meteo.com` |
| OpenWeatherMap | Full weather API | `openweathermap.org` (free tier) |

---

*This guide covers the full foundation for making API calls in Java — from understanding what an API is, to building requests, handling responses, parsing JSON, and sending data. Each section builds on the last.*