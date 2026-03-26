# API Contract: Image

## Overview

`ImageService` is responsible for uploading, managing, and retrieving
images for hotels, room types, destinations, and users. It is NOT
responsible for managing the entities themselves (that's `HotelService`,
`RoomTypeService`, etc.) or image processing/resizing (future concern).

See [ADR-009](../architecture/decisions/ADR-009-storage-service.md) for
the StorageGateway abstraction rationale.

## Collaborators

```java
@Service
public class ImageService {
    private final ImageRepository imageRepository;
    private final StorageGateway storageService;
}
```

## Two-Step Upload Process

```
1. uploadImage(request)
   → StorageGateway stores the file
   → Image created with confirmed=false
   → Returns Image with URL

2. confirmImage(id)
   → Image.confirmed set to true
   → Image becomes publicly visible
```

Unconfirmed images are never returned in public queries.

---

## Methods

### `uploadImage(ImageUploadRequest request)`

Uploads an image file to storage and creates an unconfirmed `Image` record.

```java
Image uploadImage(ImageUploadRequest request);
```

**Behavior:**
1. Validate request — fail fast
2. Verify entity exists — throw `ResourceNotFoundException` if not found
3. Store file via `StorageGateway` — returns storage URL
4. Create `Image` with `confirmed=false`, status `ACTIVE`
5. Set `createdAt`, persist and return

---

### `confirmImage(UUID id)`

Confirms an image after successful upload. Makes it publicly visible.

```java
void confirmImage(UUID id);
```

**Behavior:**
1. Look up image — throw `ResourceNotFoundException` if not found
2. Set `confirmed=true`, set `updatedAt`
3. Persist

---

### `activateImage(UUID id)`

Sets image status to `ACTIVE`. Image becomes publicly visible.

```java
void activateImage(UUID id);
```

---

### `deactivateImage(UUID id)`

Sets image status to `INACTIVE`. Image hidden from public queries.

```java
void deactivateImage(UUID id);
```

---

### `getImageById(UUID id)`

Returns a single image by ID including metadata.

```java
Image getImageById(UUID id);
```

---

### `getAllImagesByHotel(UUID hotelId, int page, int size)`

Returns all `ACTIVE` confirmed images for a hotel, paginated.

```java
List<Image> getAllImagesByHotel(UUID hotelId, int page, int size);
```

---

### `getAllImagesByRoomType(UUID roomTypeId, int page, int size)`

Returns all `ACTIVE` confirmed images for a room type, paginated.

```java
List<Image> getAllImagesByRoomType(UUID roomTypeId, int page, int size);
```

---

### `getAllImagesByDestination(UUID destinationId, int page, int size)`

Returns all `ACTIVE` confirmed images for a destination, paginated.

```java
List<Image> getAllImagesByDestination(UUID destinationId, int page, int size);
```

---

### `getAllHotelImagesByCity(UUID cityId, int page, int size)`

Returns all `ACTIVE` confirmed hotel images for all hotels in a city.
Used for city landing pages. Paginated.

```java
List<Image> getAllHotelImagesByCity(UUID cityId, int page, int size);
```

---

## Error Cases

| Condition | Exception |
|---|---|
| Image not found | `ResourceNotFoundException` |
| Entity not found | `ResourceNotFoundException` |
| Storage failure | `ImageUploadException` |
| Confirming already confirmed image | `InvalidImageStateException` |
| Activating already `ACTIVE` image | `InvalidImageStateException` |
| Deactivating already `INACTIVE` image | `InvalidImageStateException` |