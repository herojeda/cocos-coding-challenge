---
title: Cancelación de órdenes
---

# Cancelación de órdenes

## Purpose

Permite a un usuario cancelar una orden propia que todavía está pendiente en el mercado. Cubre qué órdenes se pueden cancelar, la liberación de lo que la orden tenía reservado, la pertenencia de la orden y las cancelaciones simultáneas.

Fuentes: [enunciado del challenge](https://github.com/cocos-capital/cocos-challenge/blob/main/backend-challenge.md), [supuestos S1 y S11](../../assumptions.md), [ADR-006](../../decisions/ADR-006-concurrency.md).

Fuera de alcance: cálculo de reservas y disponible (`account-balance`); alta de órdenes (`order-placement`); modificación de una orden pendiente (precio o cantidad), que el enunciado no pide.

## Requirements

### Requirement: Cancelación de una orden pendiente

Un usuario DEBE poder cancelar una orden propia en estado `NEW`. La orden DEBE pasar a estado `CANCELLED` y lo que tenía reservado DEBE volver a estar disponible.

#### Scenario: CXL-01 · Cancelación de una compra límite

- **GIVEN** un usuario con 100.000 ARS de efectivo
- **AND** una orden BUY LIMIT `NEW` de 10 GGAL a 850, que deja 91.500 ARS disponibles
- **WHEN** cancela esa orden
- **THEN** la orden queda en estado `CANCELLED`
- **AND** sus pesos disponibles vuelven a ser 100.000 ARS

#### Scenario: CXL-02 · Cancelación de una venta límite

- **GIVEN** un usuario con 40 PAMP
- **AND** una orden SELL LIMIT `NEW` de 15 PAMP, que deja 25 PAMP disponibles para vender
- **WHEN** cancela esa orden
- **THEN** la orden queda en estado `CANCELLED`
- **AND** vuelve a tener 40 PAMP disponibles para vender

#### Scenario: CXL-03 · Cancelación de una orden de la seed

- **GIVEN** el usuario 1 de la seed, con la orden BUY LIMIT `NEW` de 50 LOMA a 710 y 627.500 ARS disponibles
- **WHEN** cancela esa orden
- **THEN** la orden queda en estado `CANCELLED`
- **AND** sus pesos disponibles pasan a ser 663.000 ARS

La liberación de reservas depende del supuesto [S1](../../assumptions.md) *(pendiente: confirmar con Cocos)*.

### Requirement: Solo se cancelan órdenes pendientes

Una orden que no está en estado `NEW` NO DEBE poder cancelarse. El pedido DEBE responderse con un error de conflicto con el estado de la orden, y la orden NO DEBE modificarse.

#### Scenario: CXL-04 · Orden ejecutada

- **GIVEN** un usuario con una orden BUY MARKET `FILLED` de 10 GGAL
- **WHEN** intenta cancelarla
- **THEN** el pedido se responde con un error de conflicto
- **AND** la orden sigue en estado `FILLED`

#### Scenario: CXL-05 · Orden rechazada

- **GIVEN** un usuario con una orden SELL MARKET `REJECTED` de 50 PAMP
- **WHEN** intenta cancelarla
- **THEN** el pedido se responde con un error de conflicto
- **AND** la orden sigue en estado `REJECTED`

#### Scenario: CXL-06 · Orden ya cancelada

- **GIVEN** un usuario con una orden BUY LIMIT `CANCELLED` de 10 GGAL a 850
- **WHEN** intenta cancelarla otra vez
- **THEN** el pedido se responde con un error de conflicto
- **AND** la orden sigue en estado `CANCELLED`

### Requirement: Pertenencia de la orden

Un usuario NO DEBE poder cancelar una orden de otro usuario. El pedido DEBE responderse igual que si la orden no existiera, para no revelar que existe.

#### Scenario: CXL-07 · Orden de otro usuario

- **GIVEN** el usuario 2, sin órdenes propias
- **AND** la orden BUY LIMIT `NEW` de 50 LOMA a 710 del usuario 1
- **WHEN** el usuario 2 intenta cancelar esa orden
- **THEN** el pedido se responde con un error de recurso inexistente
- **AND** la orden sigue en estado `NEW`

#### Scenario: CXL-08 · Orden inexistente

- **GIVEN** que no existe una orden con el identificador informado
- **WHEN** un usuario intenta cancelarla
- **THEN** el pedido se responde con un error de recurso inexistente

Identificación del usuario según el supuesto [S11](../../assumptions.md).

### Requirement: Cancelaciones simultáneas

Si llegan al mismo tiempo dos pedidos para cancelar la misma orden, el sistema DEBE cancelarla una sola vez: un pedido DEBE tener éxito y el otro DEBE responderse con un error de conflicto.

#### Scenario: CXL-09 · Dos cancelaciones de la misma orden

- **GIVEN** un usuario con una orden BUY LIMIT `NEW` de 10 GGAL a 850
- **WHEN** envía al mismo tiempo dos pedidos para cancelarla
- **THEN** un pedido deja la orden en estado `CANCELLED`
- **AND** el otro se responde con un error de conflicto

### Requirement: Cuenta ocupada

Si otra operación sobre la misma cuenta impide procesar la cancelación dentro del tiempo máximo de espera, el pedido DEBE responderse con un error de conflicto que indique que la cuenta está ocupada y que se puede reintentar. En ese caso la orden NO DEBE modificarse.

#### Scenario: CXL-10 · La cuenta queda ocupada más allá del tiempo máximo

- **GIVEN** un usuario con una orden BUY LIMIT `NEW` de 10 GGAL a 850
- **AND** otra operación sobre su cuenta que se demora más que el tiempo máximo de espera
- **WHEN** intenta cancelar la orden
- **THEN** el pedido se responde con un error de conflicto con el código `ACCOUNT_BUSY`
- **AND** la orden sigue en estado `NEW`

El tiempo máximo de espera es configurable, con 2 segundos por defecto. Mecanismo según [ADR-006](../../decisions/ADR-006-concurrency.md).
