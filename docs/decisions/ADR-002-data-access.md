# ADR-002: Acceso a datos con SQL explícito

**Estado:** Aceptado

## Contexto

Las specs imponen requisitos concretos sobre la capa de datos:

- Un lock explícito sobre la cuenta al enviar o cancelar órdenes (ver [ADR-006](ADR-006-concurrency.md)).
- El último precio por instrumento, con `DISTINCT ON (instrument_id) ... ORDER BY date DESC`, que es específico de PostgreSQL.
- La búsqueda sin tildes con la extensión `unaccent`.
- Actualizaciones de la proyección de cuentas y posiciones en la misma transacción que el alta de la orden (ver [ADR-005](ADR-005-materialized-accounts.md)).

El modelo es un libro de órdenes más una proyección que se actualiza con reglas precisas. No es un grafo de entidades que se cargan y modifican.

## Decisión

Se usa **SQL explícito con `JdbcClient`** (Spring Framework 6.1+). Cada query vive en un adaptador de persistencia, a la vista, y se puede defender línea por línea.

El mapeo de filas a objetos usa `SimplePropertyRowMapper`, que liga columnas a los parámetros del constructor de un record (o a setters) y convierte tipos con el `ConversionService` de Spring. Con los nombres de columna en snake_case ([ADR-004](ADR-004-schema-and-migrations.md)), `instrument_id` se liga a `instrumentId` sin ambigüedad. Cuando una query necesita otra forma, se usa un alias explícito o un `RowMapper` propio.

## Alternativas consideradas

- **JPA / Hibernate.** Es lo más común, pero trabaja en contra de este modelo. Los locks, `DISTINCT ON` y `unaccent` terminan en queries nativas igual, y el ORM agrega comportamiento implícito (dirty checking, carga perezosa, caché de primer nivel) que deja la persistencia atada a la implementación del ORM y hay que explicar en cada caso.
- **jOOQ.** SQL tipado y verificado al compilar, la opción más elegante. Su generación de código necesita la base disponible durante el build (o un paso extra con Testcontainers), lo que complica "clonar y ejecutar" para un beneficio que en cuatro tablas es chico.

## Consecuencias

- El mapeo es manual, acotado a pocas tablas.
- El SQL queda encerrado en `adapter/out/persistence`; el dominio no sabe que existe ([ADR-003](ADR-003-hexagonal-architecture.md)).
- Las queries se prueban contra PostgreSQL real con Testcontainers.
