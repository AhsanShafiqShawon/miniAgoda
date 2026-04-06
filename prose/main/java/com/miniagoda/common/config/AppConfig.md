# AppConfig — Code Prose

`com.miniagoda.common.config.AppConfig`

---

## `passwordEncoder()`

This method has one job: give the application a single, consistent way to hash passwords.

It returns a `BCryptPasswordEncoder` — a concrete implementation of Spring's `PasswordEncoder` interface. BCrypt is chosen deliberately here because it does not just hash; it also salts automatically, meaning two users with the same password will produce two completely different hashes in the database.

The method is annotated with `@Bean`, which means Spring calls this method once at startup and registers the returned encoder into its application context. From that point on, any class in the application that declares a `PasswordEncoder` dependency — a registration service, an authentication manager, a password reset flow — gets handed this exact instance. Nobody instantiates it manually; Spring wires it in.

The class itself is annotated with `@Configuration`, which tells Spring to treat it as a source of bean definitions rather than a regular component. This is what makes the `@Bean` annotation on the method meaningful.

So in practice: when a user registers and their raw password needs to be stored, the service handling that request injects this encoder, calls `.encode(rawPassword)`, and saves the result. The plain-text password never touches the database.