# JVM Memory Walkthrough — miniAgoda

This document traces every line of `Main.java` at the JVM level: what the ClassLoader does, how stack frames are built and destroyed, how heap objects are allocated and linked, and what `this` actually is in memory. Every class in the project is covered.

---

## Table of Contents

1. [Memory regions overview](#memory-regions-overview)
2. [Class: Hotel](#class-hotel)
3. [Class: HotelRepository](#class-hotelrepository)
4. [Class: HotelSearchService](#class-hotelsearchservice)
5. [Class: Main — full execution trace](#class-main--full-execution-trace)
6. [Reference semantics cheat sheet](#reference-semantics-cheat-sheet)
7. [GC reachability trace](#gc-reachability-trace)

---

## Memory regions overview

Before any code runs, the JVM carves up memory into distinct regions. Understanding which region owns what is the foundation for everything below.

| Region | Lives where | What it holds | Lifetime |
|---|---|---|---|
| **Metaspace** | Native (off-heap) | Class metadata, method bytecode, constant pool, vtables | Until class is unloaded |
| **Heap** | JVM-managed | Every object created with `new` | Until GC collects it |
| **Thread stack** | Per-thread | Stack frames, local variables, operand stack | Frame lifetime = method lifetime |
| **PC register** | Per-thread | Address of the next bytecode instruction | Per-instruction |
| **String pool** | Heap (Java 7+) | Interned `String` literals | Until GC with no live refs |

> **Key rule:** Variables on the stack hold *references* (memory addresses), not objects. Objects live exclusively on the heap.

---

## Class: Hotel

### Source

```java
class Hotel {
    String name;

    public Hotel(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
```

### ClassLoader phase (happens once, before any `new Hotel(...)`)

```
ClassLoader reads Hotel.class from disk
  └─ Metaspace entry created for class Hotel
       ├─ field table:  { name: String }
       ├─ method table (vtable):
       │    ├─ [0] Object.hashCode()
       │    ├─ [1] Object.equals()
       │    ├─ [2] Hotel.toString()   ← your @Override lands here
       │    └─ ...
       └─ constant pool: string refs, method descriptors
```

The `@Override` annotation does not change runtime behaviour. It is a compile-time guard that tells `javac`: "fail the build if I am not actually overriding a parent method." At runtime, `toString()` is found via the vtable at index `[2]`.

### `new Hotel("Hotel A")` — step by step

```
1. JVM allocates memory on the heap:
     ┌──────────────────────────────────────┐
     │  Hotel object @ 0x5000               │
     │  ┌─────────────┬───────────────────┐ │
     │  │ mark word   │ class ptr → Meta  │ │  ← 16-byte object header
     │  ├─────────────┴───────────────────┤ │
     │  │ name        │ null              │ │  ← field slot, zero-initialised
     │  └─────────────────────────────────┘ │
     └──────────────────────────────────────┘

2. Constructor frame pushed onto the thread stack:
     ┌─────────────────────────────────┐
     │ Hotel(String) frame             │
     │  slot 0 │ this = @0x5000        │  ← hidden first parameter
     │  slot 1 │ name = @0x5100        │  ← reference to "Hotel A" String
     └─────────────────────────────────┘

3. this.name = name executes:
     - Read slot 0  → go to heap address 0x5000
     - Find field "name" inside that object
     - Write the value from slot 1 (0x5100) into it

4. Constructor frame popped. Heap object now:
     ┌──────────────────────────────────────┐
     │  Hotel object @ 0x5000               │
     │  name  →  @0x5100  ("Hotel A")       │
     └──────────────────────────────────────┘

5. 0x5000 is returned to the caller.
```

### `this` — what it actually is

`this` is not a keyword with special runtime treatment. It is **slot 0 in the local variable table** of every instance method and constructor. The JVM prepends it automatically as a hidden first argument when dispatching any non-static method call.

```
// What you write:
this.name = name;

// What the JVM executes (simplified bytecode):
aload_0          // push slot 0 (this = @0x5000) onto operand stack
aload_1          // push slot 1 (name = @0x5100) onto operand stack
putfield Hotel.name  // pop both; store @0x5100 at the "name" field of the object at @0x5000
```

Static methods have no `this` — slot 0 is simply not allocated. That is the precise reason you cannot reference `this` inside a static method.

### `toString()` — vtable dispatch

```java
@Override
public String toString() {
    return name;  // reads this.name from the heap
}
```

When `println(hotel)` is called:

```
1. println receives a reference (@0x5000) typed as Object
2. JVM reads the class pointer from the object header at @0x5000
3. Looks up the vtable for class Hotel in Metaspace
4. Finds toString() at vtable index [2]
5. Pushes a new stack frame for Hotel.toString():
     slot 0 │ this = @0x5000
6. getfield Hotel.name → reads @0x5100 from the heap
7. Returns @0x5100 (the String reference) to println
8. Frame is popped
```

This is virtual dispatch (polymorphism). If `Hotel` had a subclass overriding `toString()`, the vtable of the subclass's object would point to the subclass's implementation instead — without any conditional logic.

---

## Class: HotelRepository

### Source

```java
class HotelRepository {
    public List<Hotel> findByCity(String city) {
        return Arrays.asList(new Hotel("Hotel A"), new Hotel("Hotel B"));
    }
}
```

### ClassLoader phase

```
Metaspace entry for HotelRepository:
  ├─ field table:  {} (no instance fields)
  ├─ vtable: inherited Object methods only + findByCity()
  └─ constant pool
```

`HotelRepository` has no instance fields. Its heap object (once created) contains only the 16-byte header.

### `new HotelRepository()` — heap layout

```
HotelRepository object @ 0x2000
┌─────────────────────────────┐
│ mark word  │ class ptr      │  ← 16 bytes only; no fields
└─────────────────────────────┘
```

### `findByCity(String city)` — full execution

```
Stack frame pushed:
  slot 0 │ this = @0x2000    (HotelRepository instance)
  slot 1 │ city = @0x4000    (reference to "Bangkok" from String pool)

Inside the frame, evaluation order is strictly left-to-right:

  Step 1: new Hotel("Hotel A")
    → heap allocates Hotel @ 0x5000
    → Hotel constructor runs: this.name = @0x5100 ("Hotel A")

  Step 2: new Hotel("Hotel B")
    → heap allocates Hotel @ 0x5200
    → Hotel constructor runs: this.name = @0x5300 ("Hotel B")

  Step 3: Arrays.asList(@0x5000, @0x5200)
    → heap allocates an internal fixed-size List @ 0x6000
    → internal Object[] array holds [@0x5000, @0x5200]

Heap state after this line:
  ┌──────────────────────────────────────────────┐
  │  ArrayList @ 0x6000                          │
  │  elementData[0] → @0x5000  (Hotel "Hotel A") │
  │  elementData[1] → @0x5200  (Hotel "Hotel B") │
  └──────────────────────────────────────────────┘
        │                    │
        ▼                    ▼
  Hotel @ 0x5000       Hotel @ 0x5200
  name → @0x5100       name → @0x5300

return @0x6000 to caller — frame popped.
```

> **Note on `Arrays.asList()`:** This returns a fixed-size list backed by the array. It is not a standard `ArrayList` — calling `add()` or `remove()` on it throws `UnsupportedOperationException`. The backing array is a separate heap allocation; the list object holds a reference to it.

---

## Class: HotelSearchService

### Source

```java
class HotelSearchService {
    private final HotelRepository hotelRepository;

    public HotelSearchService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    public List<Hotel> search(String city) {
        List<Hotel> hotels = hotelRepository.findByCity(city);
        return hotels;
    }
}
```

### ClassLoader phase

```
Metaspace entry for HotelSearchService:
  ├─ field table:  { hotelRepository: HotelRepository }
  ├─ vtable: Object methods + search()
  └─ constant pool
```

### `new HotelSearchService(repo)` — dependency injection in memory

This is the most instructive allocation in the project.

```
Step 1 — heap allocation:
  HotelSearchService object @ 0x3000
  ┌──────────────────────────────────────────────┐
  │ mark word  │ class ptr                        │
  ├────────────────────────────────────────────── │
  │ hotelRepository  │  null  (zero-initialised)  │
  └──────────────────────────────────────────────┘

Step 2 — constructor frame pushed:
  ┌─────────────────────────────────────────────┐
  │ HotelSearchService(HotelRepository) frame   │
  │  slot 0 │ this           = @0x3000          │
  │  slot 1 │ hotelRepository = @0x2000         │  ← copy of the reference from main()
  └─────────────────────────────────────────────┘

Step 3 — this.hotelRepository = hotelRepository executes:
  aload_0        // this = @0x3000
  aload_1        // hotelRepository = @0x2000
  putfield HotelSearchService.hotelRepository
               // writes @0x2000 into the field slot of the object at @0x3000

Step 4 — constructor frame popped. Heap now:
  HotelSearchService @ 0x3000
  ┌──────────────────────────────────────────────┐
  │ hotelRepository  │  @0x2000                  │  ← permanently wired
  └──────────────────────────────────────────────┘
```

This is **dependency injection at the memory level**. The `HotelSearchService` object holds a reference to the `HotelRepository` object inside one of its field slots. They are two separate heap objects, linked by an address stored in a field. No frameworks required — this is the same mechanism Spring/Guice use under the hood.

**Important:** Java passes references by value. When `main()` calls `new HotelSearchService(repo)`, the *value* of `repo` (which is an address, `0x2000`) is copied into the constructor's slot 1. There is exactly one `HotelRepository` object on the heap. Both `main()`'s local variable `repo` and `HotelSearchService`'s field `hotelRepository` contain the same address.

### `search(String city)` — frame and field access

```
Frame pushed:
  slot 0 │ this = @0x3000
  slot 1 │ city = @0x4000

List<Hotel> hotels = hotelRepository.findByCity(city);

Bytecode:
  aload_0            // push this (@0x3000)
  getfield HotelSearchService.hotelRepository
                     // read field from @0x3000 → gets @0x2000
  aload_1            // push city (@0x4000)
  invokevirtual HotelRepository.findByCity
                     // push new frame for findByCity(), passing @0x2000 as its this

  (findByCity runs, returns @0x6000)

  astore_2           // store @0x6000 into slot 2 (hotels)

return @0x6000 → frame popped.
```

`search()` never touches the heap directly for any write. It only reads a field from `this`, passes references around, and forwards a return value. No allocations occur in `search()` itself.

---

## Class: Main — full execution trace

### Source

```java
public class Main {
    public static void main(String[] args) {
        HotelRepository repo = new HotelRepository();
        HotelSearchService service = new HotelSearchService(repo);

        List<Hotel> result = service.search("Bangkok");

        for (Hotel hotel : result) {
            System.out.println(hotel);
        }
    }
}
```

### Step-by-step execution

#### Phase 0 — JVM startup (before line 1)

```
JVM process starts
Bootstrap ClassLoader initialises
  → loads java.lang.Object, java.lang.String, java.util.List, etc. into Metaspace

Application ClassLoader loads:
  Main.class          → Metaspace
  Hotel.class         → Metaspace
  HotelRepository.class   → Metaspace
  HotelSearchService.class → Metaspace

Static initialisers run (none in this project).

JVM locates public static void main(String[]) in Main.class.
```

#### Phase 1 — `main()` frame created

```
Thread stack (empty → first frame pushed):

  ┌────────────────────────────────────────┐
  │ Main.main(String[]) frame   ← TOP      │
  │  slot 0 │ args   = @0x1000            │
  │  slot 1 │ repo   = (uninitialised)     │
  │  slot 2 │ service= (uninitialised)     │
  │  slot 3 │ result = (uninitialised)     │
  └────────────────────────────────────────┘

Heap: [ String[] @ 0x1000 (length=0) ]
```

#### Phase 2 — `HotelRepository repo = new HotelRepository()`

```
new HotelRepository()
  → heap allocates @ 0x2000 (header only, no fields)
  → default constructor runs (no-op, implicit super() call)
  → returns @0x2000

main() frame slot 1 (repo) ← @0x2000

Stack:
  │ slot 1 │ repo = @0x2000  │

Heap:
  HotelRepository @ 0x2000  [header only]
```

#### Phase 3 — `HotelSearchService service = new HotelSearchService(repo)`

```
Argument evaluation: repo → @0x2000 (copy of the reference)

new HotelSearchService(@0x2000)
  → heap allocates HotelSearchService @ 0x3000
  → constructor frame pushed:
       slot 0: this = @0x3000
       slot 1: hotelRepository = @0x2000
  → this.hotelRepository = @0x2000  (putfield)
  → constructor frame popped
  → returns @0x3000

main() frame slot 2 (service) ← @0x3000

Heap:
  HotelRepository     @ 0x2000  [header]
  HotelSearchService  @ 0x3000  [hotelRepository → @0x2000]
```

#### Phase 4 — `List<Hotel> result = service.search("Bangkok")`

```
"Bangkok" — string literal lookup:
  JVM checks String pool → not present → allocates String @ 0x4000, interns it
  (on a second call with "Bangkok", the same @0x4000 would be reused)

service.search(@0x4000):
  → push search() frame:
       slot 0: this = @0x3000
       slot 1: city = @0x4000
  → getfield hotelRepository → @0x2000
  → push findByCity() frame:
       slot 0: this = @0x2000
       slot 1: city = @0x4000
  → new Hotel("Hotel A") @ 0x5000, name → @0x5100
  → new Hotel("Hotel B") @ 0x5200, name → @0x5300
  → Arrays.asList(@0x5000, @0x5200) → ArrayList @ 0x6000
  → findByCity() returns @0x6000 → frame popped
  → search() stores @0x6000 in slot 2 (hotels)
  → search() returns @0x6000 → frame popped

main() frame slot 3 (result) ← @0x6000

Full heap at this point:
  0x1000  String[]          (args)
  0x2000  HotelRepository   (no fields)
  0x3000  HotelSearchService { hotelRepository → @0x2000 }
  0x4000  String            "Bangkok"  (String pool)
  0x5000  Hotel             { name → @0x5100 }
  0x5100  String            "Hotel A"
  0x5200  Hotel             { name → @0x5300 }
  0x5300  String            "Hotel B"
  0x6000  ArrayList         { [0] → @0x5000, [1] → @0x5200 }
```

#### Phase 5 — `for (Hotel hotel : result)`

The compiler desugars the enhanced for-each loop at compile time:

```java
// What you wrote:
for (Hotel hotel : result) { ... }

// What the compiler generates:
Iterator<Hotel> $it = result.iterator();
while ($it.hasNext()) {
    Hotel hotel = $it.next();
    System.out.println(hotel);
}
```

```
result.iterator()
  → invokevirtual on @0x6000
  → ArrayList creates an internal Itr object @ 0x7000
       Itr { cursor=0, lastRet=-1, expectedModCount=... }

Iteration 1 ($it.next()):
  → Itr.next() frame pushed
  → reads elementData[0] → @0x5000
  → cursor incremented to 1
  → returns @0x5000
  → frame popped
  → main() slot: hotel = @0x5000

  System.out.println(@0x5000):
    → PrintStream.println(Object) frame pushed
    → calls hotel.toString():
         toString() frame pushed
           slot 0: this = @0x5000
           getfield Hotel.name → @0x5100
           return @0x5100
         frame popped
    → PrintStream writes "Hotel A" to stdout
    → frame popped

Iteration 2 ($it.next()):
  → same sequence, hotel = @0x5200
  → toString() returns @0x5300 → "Hotel B" printed

$it.hasNext() returns false → loop exits
```

After the loop, `hotel` and `$it` are out of scope. `@0x7000` (Itr) is no longer referenced and becomes **GC-eligible immediately** — though GC does not run at this exact moment.

#### Phase 6 — `main()` returns

```
main() returns (void)
  → main() stack frame destroyed
  → all local variable slots (repo, service, result) cease to exist

Objects now unreachable from any GC root:
  @0x2000  HotelRepository       ← no live references
  @0x3000  HotelSearchService    ← no live references
  @0x5000  Hotel "Hotel A"       ← no live references
  @0x5200  Hotel "Hotel B"       ← no live references
  @0x6000  ArrayList             ← no live references
  @0x7000  Itr                   ← no live references

JVM runs shutdown hooks, flushes output streams.
OS reclaims the entire process address space — GC is irrelevant at this point.
```

---

## Reference semantics cheat sheet

| Scenario | What gets copied | What does NOT get copied |
|---|---|---|
| `Hotel h2 = h1` | The address (e.g. `0x5000`) | The `Hotel` object itself |
| `method(repo)` | The address stored in `repo` | The `HotelRepository` object |
| `this.field = param` | The address stored in `param` | The object `param` points to |
| `return hotels` | The address stored in `hotels` | The `ArrayList` object |

There is no object copying in this entire program. Every assignment, every method argument, every return value is a reference (an address). There is exactly one copy of each object — on the heap.

**Implication:** If you do `repo2 = repo` and then mutate `repo2`'s internals, `repo` sees the mutation too — they are the same object.

---

## GC reachability trace

The GC starts from **roots**: local variables in live stack frames, static fields, JNI references. It traces every reference chain. Anything reachable from a root is live; everything else is collectible.

```
During main() execution — all objects reachable:

  GC root: main() stack frame
    ├─ repo        → @0x2000 (HotelRepository)       LIVE
    ├─ service     → @0x3000 (HotelSearchService)     LIVE
    │    └─ hotelRepository → @0x2000                 LIVE (also via repo)
    └─ result      → @0x6000 (ArrayList)              LIVE
         ├─ [0]    → @0x5000 (Hotel "Hotel A")        LIVE
         │    └─ name → @0x5100 (String "Hotel A")    LIVE
         └─ [1]    → @0x5200 (Hotel "Hotel B")        LIVE
              └─ name → @0x5300 (String "Hotel B")    LIVE

After loop, before main() returns:
  @0x7000 (Itr) — no variable holds it → GC-ELIGIBLE

After main() returns:
  All of the above — GC-ELIGIBLE
  @0x4000 (String "Bangkok") — interned in String pool → may survive longer
```

> **String pool note:** Interned strings (`"Bangkok"`) are referenced by the String pool itself, which is a GC root. They survive until the pool releases them, which typically happens only if the classloader that interned them is collected. For `String.intern()` or literal strings in application code, this effectively means they live for the lifetime of the JVM process.