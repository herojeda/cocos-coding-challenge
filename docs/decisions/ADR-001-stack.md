# ADR-001: Stack tecnológico

**Estado:** Aceptado

## Contexto

El enunciado del challenge pide Node.js, pero Cocos pidió explícitamente resolverlo en Java. El proyecto tiene que poder ejecutarse localmente y el evaluador tiene que poder leerlo, correrlo y entender las decisiones sin fricción. El repo arrancó con un scaffold de Gradle (Kotlin DSL, wrapper 9.3) cuyo `Main.java` ya usa características de Java 25.

## Decisión

| Aspecto | Elección |
|---|---|
| Lenguaje | Java 25 (LTS) |
| Build | Gradle con Kotlin DSL |
| Framework | Spring Boot 4.1 |
| Base de datos | PostgreSQL, levantado localmente con docker-compose |
| Migraciones | Flyway (ver [ADR-004](ADR-004-schema-and-migrations.md)) |
| Tests | JUnit con AssertJ, y Testcontainers con PostgreSQL real para los tests de integración |
| Documentación de la API | springdoc-openapi (Swagger UI) y un archivo `.http` con ejemplos de invocación |

No se usan Lombok ni MapStruct: los records de Java cubren los objetos de valor y los DTOs, y el mapeo es explícito.

## Alternativas consideradas

- **Java 21.** También es LTS, pero no hay razón para elegir una versión anterior en un proyecto nuevo, y el scaffold ya usa Java 25.
- **Quarkus, Micronaut o Javalin.** Son más livianos y mejoran el tiempo de arranque y el consumo de memoria (compilación anticipada, imágenes nativas). En esta API el costo está en la base de datos y no en el framework, así que esas ventajas no cambian el resultado. Spring Boot es lo que un evaluador lee sin fricción, elimina boilerplate y, si el arranque llegara a importar, ofrece AOT y CDS.
- **Maven.** Igual de válido; cambiar el build del scaffold no aporta nada.
- **H2 para los tests.** No soporta lo que se usa de PostgreSQL (`DISTINCT ON`, `unaccent`, `SELECT ... FOR UPDATE` con `lock_timeout`). Un test contra otra base no prueba el comportamiento real.
- **Ejecutar contra la base de Neon provista por Cocos.** Hace que el proyecto dependa de un servicio externo para correr, y las migraciones modificarían una base que probablemente es compartida entre candidatos.

## Consecuencias

- Ejecutar el proyecto y correr los tests requiere Docker. Como docker-compose ya es necesario para la base local, no se suma una dependencia nueva, pero el README lo indica explícitamente.
- La conexión a la base se configura por variables de entorno, con valores por defecto que apuntan a la base de docker-compose. La app no debe ejecutarse contra la base provista por Cocos.
- Spring Boot 4.0 termina su soporte OSS a fin de 2026; la línea 4.1 lo tiene hasta mediados de 2027.
