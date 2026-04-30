# Type Erasure in Java

## Why Generics Exist

Before Java 5, a `List` could hold anything:

```java
List hotels = new ArrayList();
hotels.add("a string");
hotels.add(new Hotel());
hotels.add(42);
```

The compiler had no way to stop you from mixing types. You'd only discover the mistake at runtime when something blew up.

Generics were introduced to fix this — you declare what type the list holds, and the compiler enforces it:

```java
List<Hotel> hotels = new ArrayList<>();
hotels.add(new Hotel());   // fine
hotels.add("a string");    // compiler error
```

---

## The Catch

The Java designers had a problem: **millions of existing programs were already using raw `List`**, and they couldn't break all of them.

So they made a compromise. Generics are **compile-time only**. The compiler uses the type parameter to check your code, then **strips it out** before producing bytecode.

That stripping is called **type erasure**.

---

## What Erasure Means

At runtime, this:

```java
List<Hotel> hotels
```

becomes just this in bytecode:

```java
List hotels
```

The `<Hotel>` is gone. Completely. The JVM never sees it.

---

## The Consequence

At runtime, these three are identical to the JVM:

```java
List<Hotel> a
List<String> b
List<Integer> c
```

All three are just `List`. There is no way to ask the JVM at runtime: *"what type does this list hold?"*

---

> **Summary:** Generics are a compile-time safety net. They help you write correct code, but they leave no trace in the bytecode the JVM actually runs.