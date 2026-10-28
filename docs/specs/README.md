---
title: Specs
---

# Specs de cocos-coding-challenge

Las specs describen **cómo se comporta** cada capacidad del sistema, como requisitos verificables con escenarios. Son la fuente de verdad de mayor prioridad para quien desarrolla, sea una persona o un agente (ver [`AGENTS.md`](../../AGENTS.md) en la raíz del repo).

- **Vivas:** cada spec refleja el comportamiento vigente, no la historia de cambios. La historia queda en git.
- **Comportamiento, no implementación:** las specs dicen qué tiene que pasar, no cómo está programado. Las decisiones técnicas viven en los [ADRs](../decisions/README.md).
- **En el mismo PR:** un cambio de comportamiento actualiza su spec en el mismo PR que el código.
- **Supuestos explícitos:** lo que el enunciado del challenge no define se resuelve con un supuesto documentado en [`assumptions.md`](../assumptions.md). Los requisitos que dependen de un supuesto sin confirmar lo marcan como *(pendiente: …)*.

El [mapa de capacidades](./capability-map.md) lista qué capacidades existen, cuáles ya tienen spec y en qué orden se construyen.

## Trazabilidad entre escenarios y tests

Cada escenario tiene un ID único con el prefijo de su capacidad (`ACB-01`, `ORD-12`, `CXL-03`). Cada escenario DEBE tener al menos un test automatizado que lo verifique y que lleve el ID en su nombre visible, por ejemplo:

```java
@Test
@DisplayName("ORD-17 · Compra MARKET sin pesos suficientes")
void rejectsMarketBuyWithInsufficientCash() { ... }
```

Así se puede comprobar que cada regla de negocio está probada buscando el ID en el código de test. Los IDs no se reutilizan: si un escenario se elimina, su ID queda libre y no se asigna a otro.

| Prefijo | Capacidad |
|---|---|
| `ACB` | `account-balance` |
| `PF` | `portfolio` |
| `AS` | `asset-search` |
| `ORD` | `order-placement` |
| `CXL` | `order-cancellation` |

## Cómo leer una spec

Cada spec dice qué tiene que hacer el sistema, sin entrar en cómo está programado. Algunas palabras quedan en inglés porque las usan las herramientas que leen estos archivos:

| En la spec | Significa |
|---|---|
| **Purpose** | Para qué existe esta parte del sistema |
| **Requirement** | Algo que el sistema tiene que cumplir |
| **Scenario** | Un ejemplo concreto que muestra el requisito en acción |
| **GIVEN** | Dado (la situación de partida) |
| **WHEN** | Cuando (lo que hace alguien) |
| **THEN** | Entonces (lo que tiene que pasar) |
| **DEBE / NO DEBE** | Obligatorio; no hay margen de interpretación |
| *(pendiente: …)* | Depende de una definición que todavía no está confirmada |

Los escenarios que mencionan "el usuario 1 de la seed" usan los datos de [`database.sql`](https://github.com/cocos-capital/cocos-challenge/blob/main/database.sql) tal como los provee Cocos. Los demás describen situaciones que se arman para el test.

Los montos se escriben con punto para los miles y coma para los decimales (`90.256,20 ARS`).

## Estructura

```
docs/
  assumptions.md          supuestos y decisiones de negocio
  decisions/              ADRs (decisiones técnicas)
  specs/
    README.md
    capability-map.md
    <capacidad>/
      spec.md
```

Una carpeta por capacidad, con nombre en inglés y en kebab-case.

## Formato de `spec.md`

Los marcadores de estructura van en inglés para que las herramientas puedan procesarlos; el contenido va en español. Los identificadores de código y los valores del contrato (`FILLED`, `BUY`, `MONEDA`) van en inglés y entre backticks.

- Cada spec tiene `## Purpose` (con `Fuentes:` y `Fuera de alcance:`) y `## Requirements`.
- Cada requisito (`### Requirement: …`) usa **DEBE** o **NO DEBE** y se verifica con al menos un escenario (`#### Scenario: <ID> · …` con **GIVEN**, **WHEN** y **THEN**).
- Un requisito que depende de una definición pendiente la marca con *(pendiente: …)* en lugar de inventarla.
