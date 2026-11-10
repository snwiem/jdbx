---
status: accepted
---
# Use stripped-down Spring Boot and one plain JDBC connection per Session

Spring Boot provides dependency injection and configuration binding (YAML, `${ENV}` placeholders). We disable auto-configuration where possible and use no web server and no connection pool. Each Session opens one plain JDBC connection with autocommit on. A pool adds nothing for one interactive user. Startup time is not a goal, because dbui normally runs for hours. Plain Spring Framework would start faster but loses the configuration binding.

## Consequences

The `spring-boot-starter-jdbc` dependency is replaced by the PostgreSQL driver alone. We get a single executable jar from the Spring Boot plugin. If batch startup becomes a problem, AppCDS is the first remedy.
