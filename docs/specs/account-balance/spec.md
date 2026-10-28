---
title: Saldo y disponible de la cuenta
---

# Saldo y disponible de la cuenta

## Purpose

Define cómo se obtienen el efectivo, las tenencias y el disponible para operar de un usuario a partir de su libro de órdenes. Cubre el efectivo en pesos, la tenencia de cada instrumento, las reservas de las órdenes pendientes y el disponible en pesos y por instrumento. Es la base que usan el portfolio para mostrar la cuenta y el envío de órdenes para validarlas.

Fuentes: [enunciado del challenge](https://github.com/cocos-capital/cocos-challenge/blob/main/backend-challenge.md), [database.sql](https://github.com/cocos-capital/cocos-challenge/blob/main/database.sql), [supuestos S1, S3 y S12](../../assumptions.md).

Fuera de alcance: valuación de posiciones y rendimiento (`portfolio`); validación y registro de órdenes nuevas (`order-placement`); liberación de reservas al cancelar (`order-cancellation`).

## Requirements

### Requirement: El libro de órdenes es la fuente de verdad

El efectivo y las tenencias de un usuario DEBEN coincidir siempre con lo que resulta de sumar sus órdenes en estado `FILLED`. Las órdenes `NEW`, `REJECTED` y `CANCELLED` NO DEBEN modificar el efectivo ni las tenencias.

#### Scenario: ACB-01 · Órdenes no ejecutadas no mueven el efectivo

- **GIVEN** un usuario con un `CASH_IN` `FILLED` de 100.000 ARS
- **AND** una orden BUY MARKET `REJECTED` de 10 GGAL a 1.000
- **AND** una orden BUY LIMIT `CANCELLED` de 10 GGAL a 900
- **WHEN** se consulta su saldo
- **THEN** su efectivo es 100.000 ARS
- **AND** no tiene tenencia de GGAL

### Requirement: Efectivo en pesos

El efectivo DEBE calcularse como la suma de los `CASH_IN`, menos los `CASH_OUT`, menos `size × price` de cada compra, más `size × price` de cada venta, considerando solo órdenes `FILLED`. Los pesos no tienen una orden propia por cada compra o venta: el movimiento de pesos está implícito en la orden del instrumento.

#### Scenario: ACB-02 · Compras y ventas ejecutadas

- **GIVEN** un usuario con un `CASH_IN` `FILLED` de 100.000 ARS
- **AND** una compra `FILLED` de 10 GGAL a 1.000
- **AND** una venta `FILLED` de 5 GGAL a 1.200
- **WHEN** se consulta su saldo
- **THEN** su efectivo es 96.000 ARS

#### Scenario: ACB-03 · Transferencia saliente

- **GIVEN** un usuario con un `CASH_IN` `FILLED` de 100.000 ARS
- **AND** un `CASH_OUT` `FILLED` de 30.000 ARS
- **WHEN** se consulta su saldo
- **THEN** su efectivo es 70.000 ARS

### Requirement: Tenencia por instrumento

La tenencia de cada instrumento DEBE calcularse como la cantidad comprada menos la cantidad vendida, considerando solo órdenes `FILLED`. El sistema NO DEBE corregir ni ocultar una tenencia negativa que surja de los datos existentes.

#### Scenario: ACB-04 · Varias compras y una venta

- **GIVEN** un usuario con compras `FILLED` de 50 y 20 PAMP
- **AND** una venta `FILLED` de 10 PAMP
- **WHEN** se consulta su saldo
- **THEN** su tenencia de PAMP es 60

#### Scenario: ACB-05 · Posición cerrada

- **GIVEN** un usuario con una compra `FILLED` de 10 GGAL y una venta `FILLED` de 10 GGAL
- **WHEN** se consulta su saldo
- **THEN** su tenencia de GGAL es 0

#### Scenario: ACB-06 · Tenencia negativa en los datos existentes

- **GIVEN** el usuario 1 de la seed, con una compra `FILLED` de 20 BMA y una venta `FILLED` posterior de 30 BMA
- **WHEN** se consulta su saldo
- **THEN** su tenencia de BMA es −10
- **AND** el sistema NO DEBE modificar las órdenes para corregirla

Este caso viene de la seed provista y el sistema no puede generarlo con órdenes nuevas (ver `order-placement`). El tratamiento de la tenencia negativa *(pendiente: confirmar supuesto [S3](../../assumptions.md) con Cocos)*.

### Requirement: Reservas de órdenes pendientes

Cada orden BUY LIMIT en estado `NEW` DEBE reservar `size × price` de los pesos del usuario, y cada orden SELL LIMIT en estado `NEW` DEBE reservar `size` unidades del instrumento. Una orden que deja de estar en `NEW` NO DEBE seguir reservando.

#### Scenario: ACB-07 · Reserva de pesos por una compra límite

- **GIVEN** un usuario con 100.000 ARS de efectivo
- **AND** una orden BUY LIMIT `NEW` de 10 GGAL a 900
- **WHEN** se consulta su saldo
- **THEN** tiene 9.000 ARS reservados
- **AND** su efectivo sigue siendo 100.000 ARS

#### Scenario: ACB-08 · Reserva de acciones por una venta límite

- **GIVEN** un usuario con 40 PAMP
- **AND** una orden SELL LIMIT `NEW` de 15 PAMP
- **WHEN** se consulta su saldo
- **THEN** tiene 15 PAMP reservados
- **AND** su tenencia de PAMP sigue siendo 40

#### Scenario: ACB-09 · Una orden cancelada no reserva

- **GIVEN** un usuario con 100.000 ARS de efectivo
- **AND** una orden BUY LIMIT `CANCELLED` de 10 GGAL a 900
- **WHEN** se consulta su saldo
- **THEN** no tiene pesos reservados

La reserva de órdenes pendientes *(pendiente: confirmar supuesto [S1](../../assumptions.md) con Cocos)*.

### Requirement: Disponible en pesos

Los pesos disponibles para operar DEBEN calcularse como el efectivo menos los pesos reservados por órdenes pendientes. Toda validación de una compra DEBE hacerse contra este disponible y no contra el efectivo.

#### Scenario: ACB-10 · Disponible con una compra límite pendiente

- **GIVEN** un usuario con 100.000 ARS de efectivo
- **AND** una orden BUY LIMIT `NEW` de 10 GGAL a 900
- **WHEN** se consulta su saldo
- **THEN** sus pesos disponibles son 91.000 ARS

#### Scenario: ACB-11 · Disponible del usuario de la seed

- **GIVEN** el usuario 1 con las órdenes de la seed provista
- **WHEN** se consulta su saldo
- **THEN** su efectivo es 753.000 ARS
- **AND** sus pesos disponibles son 627.500 ARS, por las compras límite pendientes de 50 LOMA a 710 y 60 BMA a 1.500

### Requirement: Disponible por instrumento

Las unidades disponibles para vender de un instrumento DEBEN calcularse como la tenencia menos las unidades reservadas por ventas límite pendientes. Toda validación de una venta DEBE hacerse contra este disponible y no contra la tenencia.

#### Scenario: ACB-12 · Disponible con una venta límite pendiente

- **GIVEN** un usuario con 40 PAMP
- **AND** una orden SELL LIMIT `NEW` de 15 PAMP
- **WHEN** se consulta su saldo
- **THEN** tiene 25 PAMP disponibles para vender

#### Scenario: ACB-13 · Disponible con tenencia negativa

- **GIVEN** el usuario 1 de la seed, con tenencia de −10 BMA
- **WHEN** se consulta su saldo
- **THEN** tiene −10 BMA disponibles para vender
- **AND** cualquier venta de BMA queda sin disponible suficiente
