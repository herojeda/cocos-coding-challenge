---
title: Mapa de capacidades
---

# Mapa de capacidades

Lista de las capacidades del sistema, cada una con la carpeta donde vive su spec. Sirve para saber qué existe, qué está especificado y en qué orden se construye.

Una capacidad es un comportamiento del sistema que se puede describir y verificar por separado. No es lo mismo que un endpoint: el saldo disponible no tiene un endpoint propio, pero lo usan el portfolio y el envío de órdenes, y por eso se especifica una sola vez. La fuente principal es el [enunciado del challenge](https://github.com/cocos-capital/cocos-challenge/blob/main/backend-challenge.md); lo que el enunciado no define está en los [supuestos](../assumptions.md).

## Cuenta

Lo que el usuario tiene y puede usar para operar.

| Capacidad | Carpeta | Qué cubre | Fuentes | Ola | Spec |
|---|---|---|---|---|---|
| Saldo y disponible de la cuenta | `account-balance` | Efectivo, tenencias, reservas de órdenes pendientes, disponible en pesos y por instrumento | Enunciado, S1, S3, S12 | 1 | [En spec](./account-balance/spec.md) |
| Portfolio | `portfolio` | Valor total, pesos disponibles, posiciones, valuación, rendimiento sobre costo promedio, datos complementarios | Enunciado, S1, S2, S3, S5, S9, S11 | 2 | [En spec](./portfolio/spec.md) |

## Mercado

Información de los instrumentos que se pueden operar.

| Capacidad | Carpeta | Qué cubre | Fuentes | Ola | Spec |
|---|---|---|---|---|---|
| Búsqueda de activos | `asset-search` | Búsqueda por ticker o nombre, exclusión de la moneda, datos y orden de los resultados | Enunciado, S10 | 2 | [En spec](./asset-search/spec.md) |

## Órdenes

Operatoria del usuario sobre el mercado.

| Capacidad | Carpeta | Qué cubre | Fuentes | Ola | Spec |
|---|---|---|---|---|---|
| Envío de órdenes | `order-placement` | Validación del pedido, cantidad por monto, ejecución MARKET, alta LIMIT, rechazos, registro, concurrencia | Enunciado, S1, S3, S5–S9, S11, S12 | 3 | [En spec](./order-placement/spec.md) |
| Cancelación de órdenes | `order-cancellation` | Cancelación de órdenes `NEW`, liberación de reservas, pertenencia, concurrencia | Enunciado, S1, S11 | 3 | [En spec](./order-cancellation/spec.md) |

## Olas

- **Ola 1 — Base de la cuenta.** `account-balance`, porque todas las demás capacidades que leen o mueven dinero dependen de ella.
- **Ola 2 — Consultas.** `portfolio` y `asset-search`. No escriben en el libro de órdenes y se pueden construir en paralelo.
- **Ola 3 — Operatoria.** `order-placement` y después `order-cancellation`. Son las que escriben en el libro y concentran las reglas de consistencia y concurrencia.

## Cómo se actualiza este mapa

- Si al construir una capacidad conviene partirla o unirla con otra, se cambia acá en el mismo PR.
- Una capacidad nueva se agrega acá antes de crear su carpeta.
