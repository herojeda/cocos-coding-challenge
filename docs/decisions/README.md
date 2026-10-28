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
