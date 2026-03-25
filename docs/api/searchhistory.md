# API Contract: Search History

## Overview

`SearchHistoryService` is responsible for recording user search queries
and retrieving search history. It is NOT responsible for performing
searches (that's `HotelSearchService`), popular destinations (that's
`DestinationService`), or any booking logic.

## Collaborators

```java
@Service
public class SearchHistoryService {
    private final SearchHistoryRepository searchHistoryRepository;
    private final HotelRepository hotelRepository;
}
```

## How HotelSearchService Uses SearchHistoryService

`recordSearch` is called asynchronously after a successful city search.
Anonymous searches (null `userId`) are never recorded.

```java
// Inside HotelSearchService.searchByCity()
if (query.userId() != null) {
    searchHistoryService.recordSearch(query.userId(), query); // @Async
}
```

---

## Methods

### `recordSearch(UUID userId, CitySearchQuery query)`

Records a user's city search query asynchronously. Never blocks the
search response. Only city searches are recorded — drill-down searches
(`HotelSearchQuery`) are not recorded.

```java
@Async
void recordSearch(UUID userId, CitySearchQuery query);
```

**Behavior:**
1. Create `SearchHistory` with status `ACTIVE` and `createdAt` set to now
2. Persist asynchronously — failure does not affect search response

---

### `getSearchHistory(UUID userId, int page, int size)`

Returns all search history entries for a user, paginated.

```java
List<SearchHistory> getSearchHistory(UUID userId, int page, int size);
```

---

### `getSearchHistoryById(UUID searchId)`

Returns a single search history entry by ID.

```java
SearchHistory getSearchHistoryById(UUID searchId);
```

---

### `getSearchHistoryByStatus(UUID userId, SearchHistoryStatus status, int page, int size)`

Returns a user's search history filtered by status, paginated.

```java
List<SearchHistory> getSearchHistoryByStatus(
    UUID userId, SearchHistoryStatus status, int page, int size);
```

---

### `activateSearchHistory(UUID userId, UUID searchId)`

Restores a previously deactivated search history entry.

```java
void activateSearchHistory(UUID userId, UUID searchId);
```

**Behavior:**
1. Look up search history — throw `ResourceNotFoundException` if not found
2. Verify `searchHistory.userId == userId` — throw `UnauthorizedException` if not owner
3. Set status to `ACTIVE`

---

### `deactivateSearchHistory(UUID userId, UUID searchId)`

Hides a search history entry from the user's history.

```java
void deactivateSearchHistory(UUID userId, UUID searchId);
```

**Behavior:**
1. Look up search history — throw `ResourceNotFoundException` if not found
2. Verify `searchHistory.userId == userId` — throw `UnauthorizedException` if not owner
3. Set status to `INACTIVE`

---

### `getRecentlySearchedHotels(UUID userId, int page, int size)`

Returns top-rated `ACTIVE` hotels from the cities in the user's recent
`ACTIVE` search history entries, paginated.

```java
List<HotelSummary> getRecentlySearchedHotels(UUID userId, int page, int size);
```

**Behavior:**
1. Fetch user's recent `ACTIVE` search history entries
2. Extract distinct `cityId` values in recency order
3. For each city, fetch top-rated `ACTIVE` hotels
4. Deduplicate hotels across cities
5. Apply pagination and return

---

## Error Cases

| Condition | Exception |
|---|---|
| Search history entry not found | `ResourceNotFoundException` |
| User does not own the search history entry | `UnauthorizedException` |
| `userId` not found | `ResourceNotFoundException` |