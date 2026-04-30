# TypeReference in Jackson

## The Problem It Solves

At runtime, `List<Hotel>` is just `List`. The `<Hotel>` is erased (see: [Type Erasure](type-erasure.md)).

Now Jackson has a job to do — it needs to deserialize this JSON:

```json
[
  { "id": 1, "name": "Hotel Sunshine" },
  { "id": 2, "name": "Hotel Moon" }
]
```

Into a Java object. You tell it:

```java
objectMapper.readValue(inputStream, List.class);
```

Jackson sees `List.class` and thinks: *"ok, a list — but a list of what?"*

It has no answer. `List.class` carries no element type. So it falls back to the safest thing it knows — it deserializes each element as a `LinkedHashMap` (a generic key-value map), not a `Hotel`.

You'd end up with `List<LinkedHashMap>` instead of `List<Hotel>`. Useless.

---

## The Solution

You need a way to tell Jackson: *"a list, and each element is a `Hotel`"* — even though the JVM erased that information.

This is what `TypeReference` does:

```java
new TypeReference<List<Hotel>>() {}
```

---

## The Anonymous Subclass Trick

The `{}` at the end is the key. It creates an **anonymous subclass** of `TypeReference`.

```java
// This is NOT just an object creation
// It is creating a new anonymous subclass
new TypeReference<List<Hotel>>() {}
```

In Java, when a class **extends** a generic class, the type parameter is written into the bytecode of that subclass — and erasure does **not** remove it.

So:

```java
// The parent class
TypeReference<List<Hotel>>

// The anonymous subclass that extends it
new TypeReference<List<Hotel>>() {}
```

The subclass "bakes in" `List<Hotel>` into its own class definition in the bytecode. It survives erasure because it's part of the class structure, not a variable.

Jackson then reads it back using reflection:

```java
// Internally, Jackson does something like this
Type type = typeRef.getClass().getGenericSuperclass();
// → TypeReference<List<Hotel>>
// → element type is Hotel ✓
```

---

## Summary

| | Survives Erasure? |
|---|---|
| `List<Hotel> x` (variable) | ✗ — `<Hotel>` is gone at runtime |
| `class Foo extends TypeReference<List<Hotel>>` (subclass) | ✓ — `<Hotel>` is baked into bytecode |

`TypeReference` exploits exactly that difference — it wraps your desired type inside a subclass so the generic information is preserved and Jackson can read it back via reflection.