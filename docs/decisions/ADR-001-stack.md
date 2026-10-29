# ADR-001: Stack tecnológico

**Estado:** Aceptado

## Contexto

El enunciado del challenge pide Node.js, pero Cocos pidió explícitamente resolverlo en Java. El proyecto tiene que poder ejecutarse localmente con el menor número de dependencias externas posible. El repo arrancó con un scaffold de Gradle (Kotlin DSL, wrapper 9.3) cuyo `Main.java` ya usa características de Java 25.

## Decisión

| Aspecto | Elección |
|---|---|
| Lenguaje | Java 25 (LTS) |
| Build | Gradle con Kotlin DSL |
| Framework | Spring Boot 4.1 |
| Base de datos | PostgreSQL, levantado localmente con docker-compose |
| Migraciones | Flyway (ver [ADR-004](ADR-004-schema-and-migrations.md)) |
| Tests | JUnit con AssertJ, y Testcontainers con PostgreSQL real para los tests de integración |
| Observabilidad | Micrometer con exportación OTLP mediante `spring-boot-starter-opentelemetry` (ver [ADR-009](ADR-009-observability.md)) |
| Documentación de la API | springdoc-openapi (Swagger UI) y un archivo `.http` con ejemplos de invocación |

No se usan Lombok ni MapStruct: los records de Java cubren los objetos de valor y los DTOs, y el mapeo es explícito.

## Alternativas consideradas

- **Java 21.** También es LTS, pero no hay razón para elegir una versión anterior en un proyecto nuevo, y el scaffold ya usa Java 25.
- **Quarkus o Micronaut.** Su ventaja principal es el tiempo de arranque y el consumo de memoria, gracias a la resolución de dependencias en compilación y a las imágenes nativas. Esa ventaja pesa en escenarios de escalado a cero o funciones serverless; en esta API el costo de cada pedido está en la base de datos, no en el framework. Spring Boot cubre de forma integrada todo lo que el diseño necesita sin código de infraestructura propio: transacciones declarativas sobre `JdbcClient` ([ADR-002](ADR-002-data-access.md)), Problem Details nativo ([ADR-007](ADR-007-error-handling.md)), Bean Validation, la autoconfiguración de Flyway, la integración de Testcontainers con `@ServiceConnection` y los endpoints de health y readiness de Actuator. Con Java 25 puede atender pedidos en hilos virtuales, lo que da alta concurrencia con JDBC bloqueante sin pasar a un modelo reactivo. Y si el arranque llegara a importar, ofrece AOT, CDS e imágenes nativas con GraalVM.
- **Javalin.** Minimalista y rápido, pero no trae transacciones declarativas, validación, manejo de errores estándar ni integración con migraciones o Testcontainers: todo eso habría que armarlo y mantenerlo a mano.
- **Maven.** Su modelo declarativo en XML es predecible y uniforme, pero más verboso, y extender el build requiere escribir o configurar plugins. Gradle tiene ventajas técnicas concretas: compilación incremental, caché de build y daemon, que hacen más rápidos los builds repetidos (el caso típico de un ciclo de desarrollo con agentes que compilan y corren tests en cada paso); un DSL en Kotlin tipado, con autocompletado en el IDE; y la posibilidad de agregar tareas propias con poco código, por ejemplo una que verifique que cada escenario de las specs tenga su test.
- **H2 para los tests.** No soporta lo que se usa de PostgreSQL (`DISTINCT ON`, `unaccent`, `SELECT ... FOR UPDATE` con `lock_timeout`). Un test contra otra base no prueba el comportamiento real.
- **Ejecutar contra la base de Neon provista por Cocos.** Hace que el proyecto dependa de un servicio externo para correr, y las migraciones modificarían una base que probablemente es compartida entre candidatos.

## Consecuencias

- Ejecutar el proyecto y correr los tests requiere Docker. Como docker-compose ya es necesario para la base local, no se suma una dependencia nueva, pero el README lo indica explícitamente.
- La conexión a la base se configura por variables de entorno, con valores por defecto que apuntan a la base de docker-compose. La app no debe ejecutarse contra la base provista por Cocos.
- Spring Boot 4.0 termina su soporte OSS a fin de 2026; la línea 4.1 lo tiene hasta mediados de 2027.
