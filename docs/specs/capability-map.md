---
title: Mapa de capacidades
---

# Mapa de capacidades

Lista de las capacidades del sistema, cada una con la carpeta donde vive su spec. Sirve para saber qué existe, qué está especificado, en qué orden se construye y qué ya está implementado. Una capacidad está **implementada** cuando cada uno de sus escenarios tiene un test que pasa.

Una capacidad es un comportamiento del sistema que se puede describir y verificar por separado. No es lo mismo que un endpoint: el saldo disponible no tiene un endpoint propio, pero lo usan el portfolio y el envío de órdenes, y por eso se especifica una sola vez. La fuente principal es el [enunciado del challenge](https://github.com/cocos-capital/cocos-challenge/blob/main/backend-challenge.md); lo que el enunciado no define está en los [supuestos](../assumptions.md).

## Cuenta

Lo que el usuario tiene y puede usar para operar.

| Capacidad | Carpeta | Qué cubre | Fuentes | Ola | Spec | Estado |
|---|---|---|---|---|---|---|
| Saldo y disponible de la cuenta | `account-balance` | Efectivo, tenencias, reservas de órdenes pendientes, disponible en pesos y por instrumento | Enunciado, S1, S3, S12 | 1 | [En spec](./account-balance/spec.md) | Implementada |
| Portfolio | `portfolio` | Valor total, pesos disponibles, posiciones, valuación, rendimiento sobre costo promedio, datos complementarios | Enunciado, S1, S2, S3, S5, S9, S11 | 2 | [En spec](./portfolio/spec.md) | Implementada |

## Mercado

Información de los instrumentos que se pueden operar.

| Capacidad | Carpeta | Qué cubre | Fuentes | Ola | Spec | Estado |
|---|---|---|---|---|---|---|
| Búsqueda de activos | `asset-search` | Filtros por ticker y por nombre, exclusión de la moneda, datos, orden elegible y paginación de los resultados | Enunciado, S10 | 2 | [En spec](./asset-search/spec.md) | Implementada |

## Órdenes

Operatoria del usuario sobre el mercado.

| Capacidad | Carpeta | Qué cubre | Fuentes | Ola | Spec | Estado |
|---|---|---|---|---|---|---|
| Envío de órdenes | `order-placement` | Validación del pedido, cantidad por monto, ejecución MARKET, alta LIMIT, rechazos con motivo, idempotencia, registro, concurrencia, cuenta ocupada | Enunciado, S1, S3, S5–S9, S11–S13 | 3 | [En spec](./order-placement/spec.md) | Implementada |
| Cancelación de órdenes | `order-cancellation` | Cancelación de órdenes `NEW`, liberación de reservas, pertenencia, concurrencia, cuenta ocupada | Enunciado, S1, S11 | 3 | [En spec](./order-cancellation/spec.md) | Implementada |

## Olas

- **Ola 1 — Base de la cuenta.** `account-balance`, porque todas las demás capacidades que leen o mueven dinero dependen de ella.
- **Ola 2 — Consultas.** `portfolio` y `asset-search`. No escriben en el libro de órdenes y se pueden construir en paralelo.
- **Ola 3 — Operatoria.** `order-placement` y después `order-cancellation`. Son las que escriben en el libro y concentran las reglas de consistencia y concurrencia.

## Cómo se actualiza este mapa

- Si al construir una capacidad conviene partirla o unirla con otra, se cambia acá en el mismo PR.
- Una capacidad nueva se agrega acá antes de crear su carpeta.
- El estado pasa a **Implementada** en el PR que agrega el test del último escenario de la capacidad.
