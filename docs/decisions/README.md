# Decisiones de arquitectura (ADRs)

Cada decisión técnica relevante se registra como un ADR (*Architecture Decision Record*): qué se decidió, en qué contexto, qué alternativas se descartaron y qué consecuencias tiene. Las specs dicen **qué** hace el sistema; los ADRs dicen **cómo** está construido y **por qué**.

## Formato

Un archivo por decisión, numerado y con nombre en kebab-case: `ADR-001-<tema>.md`.

```markdown
# ADR-001: <Título>

**Estado:** Propuesto | Aceptado | Reemplazado por ADR-00X

## Contexto
<Qué problema hay que resolver y qué restricciones existen.>

## Decisión
<Qué se decidió.>

## Alternativas consideradas
<Qué otras opciones había y por qué se descartaron.>

## Consecuencias
<Qué se gana, qué se resigna y qué queda abierto.>
```

Solo los ADRs en estado **Aceptado** son vinculantes para la implementación. Un ADR no se edita para cambiar la decisión: se crea uno nuevo que lo reemplaza.

## Índice

| ADR | Decisión | Estado |
|---|---|---|
| [ADR-001](ADR-001-stack.md) | Stack tecnológico: Java 25, Spring Boot 4.1, PostgreSQL, Testcontainers | Aceptado |
| [ADR-002](ADR-002-data-access.md) | Acceso a datos con SQL explícito (`JdbcClient`) | Aceptado |
| [ADR-003](ADR-003-hexagonal-architecture.md) | Arquitectura hexagonal en un solo módulo, verificada con ArchUnit | Aceptado |
| [ADR-004](ADR-004-schema-and-migrations.md) | Convenciones del esquema y migraciones con Flyway | Aceptado |
| [ADR-005](ADR-005-materialized-accounts.md) | Cuentas y posiciones materializadas como proyección del libro | Aceptado |
| [ADR-006](ADR-006-concurrency.md) | Concurrencia con lock pesimista sobre la cuenta | Aceptado |
| [ADR-007](ADR-007-error-handling.md) | Errores con Problem Details y catálogo de códigos | Aceptado |
| [ADR-008](ADR-008-idempotency.md) | Idempotencia en el envío de órdenes con `Idempotency-Key` | Aceptado |
