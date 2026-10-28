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

Todavía no hay ADRs. Se crean en la sesión de arquitectura.

Decisiones ya tomadas que tienen que registrarse como ADR:

- **Convención de valores de enumerados:** todo valor de enumerado en inglés y en mayúsculas, con la normalización de `instruments.type` de la seed (`ACCIONES` → `STOCK`, `MONEDA` → `CURRENCY`) mediante una migración (ver la regla en [`AGENTS.md`](../../AGENTS.md)).
- **Stack:** Java 25, Gradle con Kotlin DSL, Spring Boot 4.1, PostgreSQL local con docker-compose, JUnit con Testcontainers y springdoc-openapi. Sin Lombok ni MapStruct.
- **Acceso a datos:** SQL explícito con `JdbcClient` en lugar de JPA o jOOQ.
- **Estructura:** arquitectura hexagonal en un solo módulo de Gradle (`domain`, `application`, `adapter/in/web`, `adapter/out/persistence`, `config`), con las reglas de dependencia verificadas con ArchUnit.
- **Migraciones:** Flyway, con la seed original como V1, el renombre a snake_case (solo separadores) como V2 y constraints, índices, `unaccent` y normalización de enumerados como V3. La app no debe ejecutarse contra la base provista por Cocos.
- **Errores:** Problem Details (RFC 9457) con `status` numérico, `type` y un campo `code` de un catálogo cerrado; `detail` siempre con texto controlado (sin reflejar input ni mensajes internos), `instance` como identificador de la ocurrencia (`urn:uuid:…`) registrado en el log, y header `X-Content-Type-Options: nosniff`.
- **Motivo del rechazo:** el libro de órdenes guarda el motivo de cada orden `REJECTED` (supuesto S13), lo que requiere una columna nueva en la migración del modelo de datos.
