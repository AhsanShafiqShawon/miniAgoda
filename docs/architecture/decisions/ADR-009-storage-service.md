# ADR-009: StorageService Abstraction for File Storage

## Status
Accepted

## Context

`ImageService` needs to store uploaded image files somewhere. The simplest
approach is to write files directly to the local filesystem inside
`ImageService`. However, this creates a hard dependency on local storage
that would need to be rewritten when moving to cloud storage (e.g. AWS S3,
Google Cloud Storage) in production.

## Decision

Introduce a `StorageService` interface that abstracts the file storage
backend. `ImageService` depends on `StorageService`, not on any specific
storage implementation.

```java
public interface StorageService {
    String store(byte[] data, String fileName, String contentType);
    void delete(String fileKey);
    String getUrl(String fileKey);
}
```

Two implementations:

```java
// Current phase — local filesystem
@Service
@Profile("local")
public class LocalStorageService implements StorageService { ... }

// Future phase — AWS S3
@Service
@Profile("production")
public class S3StorageService implements StorageService { ... }
```

Spring profiles control which implementation is active. Switching from
local to S3 requires no changes to `ImageService` — only the active
profile changes.

## Consequences

**Positive:**
- `ImageService` is decoupled from storage implementation
- Switching to S3 or any other backend requires no changes to `ImageService`
- Local storage works for development and testing
- Clean interface — easy to mock in tests

**Negative:**
- One additional abstraction layer
- Local storage is not suitable for production or multi-instance deployment
  (files not shared across instances)

## Migration Path

Phase 1 (current): `LocalStorageService` — files stored on local filesystem
Phase 2 (production): `S3StorageService` — files stored on AWS S3 with CDN

## Alternatives Considered

- **Store URLs directly in entity fields**: No lifecycle management, no
  confirmation step. Already rejected in favour of `Image` entity.
- **Write directly to S3 in `ImageService`**: Hard dependency on AWS SDK.
  Rejected in favour of abstraction.