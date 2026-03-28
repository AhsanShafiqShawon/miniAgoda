# Flow Conversations

End-to-end scenarios narrated as conversations between every layer and
class involved — from the user's browser all the way to the database
and back. Each character speaks in first person, passing the baton to
the next.

These flows exist to build a deep, felt understanding of how the system
works before writing a single line of code.

---

## Search Flows

| Scenario | User | Outcome | File |
|---|---|---|---|
| Full results | Logged in (Shawon) | 2 hotels found, discount applied | [search-logged-in-full-results.md](search/search-logged-in-full-results.md) |
| Insufficient results | Anonymous | Below threshold → recommendations triggered | [search-anonymous-insufficient-results.md](search/search-anonymous-insufficient-results.md) |
| No results | Logged in (Shawon) | Zero results → relaxed date → relaxed guests | [search-logged-in-no-results.md](search/search-logged-in-no-results.md) |

## Coming Soon

| Flow | Description |
|---|---|
| Registration | User signs up, email verification sent |
| Login | JWT issued, SecurityContext populated |
| Booking | Room selected, availability locked, payment initiated |
| Cancel Booking | Booking cancelled, inventory released, refund triggered |
| Write Review | Review submitted, hotel rating recalculated |
| Add Hotel | Hotel owner adds property, inventory initialized |