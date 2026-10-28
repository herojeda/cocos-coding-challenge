---
title: Envío de órdenes
---

# Envío de órdenes

## Purpose

Permite a un usuario enviar al mercado órdenes de compra o venta de acciones, de tipo MARKET o LIMIT, indicando la cantidad de acciones o un monto en pesos. Cubre la validación del pedido, el cálculo de la cantidad por monto, la ejecución inmediata de las órdenes MARKET, el alta en `NEW` de las órdenes LIMIT, el rechazo por falta de disponible y el registro de cada orden en el libro.

Fuentes: [enunciado del challenge](https://github.com/cocos-capital/cocos-challenge/blob/main/backend-challenge.md), [database.sql](https://github.com/cocos-capital/cocos-challenge/blob/main/database.sql), [supuestos S1, S3, S5, S6, S7, S8, S9, S11 y S12](../../assumptions.md).

Fuera de alcance: cálculo del disponible y de las reservas (`account-balance`); cancelación de órdenes pendientes (`order-cancellation`); ejecución de órdenes LIMIT pendientes, porque no se simula el mercado; transferencias `CASH_IN` y `CASH_OUT`, que se registran como órdenes pero no se envían desde esta capacidad.

## Requirements

### Requirement: Datos del pedido

Un pedido de orden DEBE informar el usuario, el instrumento, el lado (`BUY` o `SELL`), el tipo (`MARKET` o `LIMIT`) y exactamente una de dos opciones: la cantidad de acciones o el monto en pesos. Una orden LIMIT DEBE informar un precio mayor a cero y una orden MARKET NO DEBE informar precio. Un pedido que no cumple estas reglas DEBE responderse con un error de validación y NO DEBE registrar ninguna orden.

#### Scenario: ORD-01 · Orden LIMIT sin precio

- **GIVEN** un usuario con 100.000 ARS disponibles
- **WHEN** envía una orden BUY LIMIT de 10 GGAL sin precio
- **THEN** el pedido se responde con un error de validación
- **AND** no se registra ninguna orden

#### Scenario: ORD-02 · Orden MARKET con precio

- **GIVEN** un usuario con 100.000 ARS disponibles
- **WHEN** envía una orden BUY MARKET de 10 GGAL a 900
- **THEN** el pedido se responde con un error de validación
- **AND** no se registra ninguna orden

#### Scenario: ORD-03 · Cantidad y monto a la vez

- **GIVEN** un usuario con 100.000 ARS disponibles
- **WHEN** envía una orden BUY MARKET de GGAL informando 10 acciones y un monto de 10.000 ARS
- **THEN** el pedido se responde con un error de validación
- **AND** no se registra ninguna orden

#### Scenario: ORD-04 · Cantidad no positiva

- **GIVEN** un usuario con 100.000 ARS disponibles
- **WHEN** envía una orden BUY MARKET de 0 GGAL
- **THEN** el pedido se responde con un error de validación
- **AND** no se registra ninguna orden

#### Scenario: ORD-05 · Lado de transferencia

- **GIVEN** un usuario con 100.000 ARS disponibles
- **WHEN** envía una orden con lado `CASH_OUT`
- **THEN** el pedido se responde con un error de validación
- **AND** no se registra ninguna orden

#### Scenario: ORD-06 · Orden sobre los pesos

- **GIVEN** un usuario con 100.000 ARS disponibles
- **WHEN** envía una orden BUY MARKET de 10 unidades del instrumento ARS, de tipo `MONEDA`
- **THEN** el pedido se responde con un error de validación
- **AND** no se registra ninguna orden

#### Scenario: ORD-07 · Usuario o instrumento inexistente

- **GIVEN** que no existe un instrumento con el identificador informado
- **WHEN** un usuario envía una orden sobre ese instrumento
- **THEN** el pedido se responde con un error de recurso inexistente
- **AND** no se registra ninguna orden

Distinción entre error de validación y rechazo según el supuesto [S8](../../assumptions.md). El mismo tratamiento de recurso inexistente aplica a un usuario inexistente.

### Requirement: Cantidad calculada a partir de un monto

Cuando el pedido informa un monto en pesos, la cantidad DEBE calcularse como el entero inferior de `monto / precio`, usando el último precio en una orden MARKET y el precio límite en una orden LIMIT. La orden DEBE registrarse con la cantidad calculada y solo DEBE comprometer `cantidad × precio`: el excedente del monto NO DEBE reservarse ni debitarse. Si el monto no alcanza para una acción, el pedido DEBE responderse con un error de validación.

#### Scenario: ORD-08 · Compra MARKET por monto

- **GIVEN** un usuario con 100.000 ARS disponibles
- **AND** el último precio de GGAL es 885,80
- **WHEN** envía una orden BUY MARKET de GGAL por 10.000 ARS
- **THEN** la orden se registra `FILLED` por 11 GGAL a 885,80
- **AND** sus pesos disponibles pasan a ser 90.256,20 ARS

#### Scenario: ORD-09 · Compra LIMIT por monto

- **GIVEN** un usuario con 100.000 ARS disponibles
- **WHEN** envía una orden BUY LIMIT de GGAL a 900 por 10.000 ARS
- **THEN** la orden se registra `NEW` por 11 GGAL a 900
- **AND** sus pesos disponibles pasan a ser 90.100 ARS

#### Scenario: ORD-10 · Venta MARKET por monto

- **GIVEN** un usuario con 40 PAMP
- **AND** el último precio de PAMP es 925,85
- **WHEN** envía una orden SELL MARKET de PAMP por 5.000 ARS
- **THEN** la orden se registra `FILLED` por 5 PAMP a 925,85

#### Scenario: ORD-11 · Monto que no alcanza para una acción

- **GIVEN** un usuario con 100.000 ARS disponibles
- **AND** el último precio de GGAL es 885,80
- **WHEN** envía una orden BUY MARKET de GGAL por 500 ARS
- **THEN** el pedido se responde con un error de validación
- **AND** no se registra ninguna orden

El cálculo por monto *(pendiente: confirmar supuesto [S6](../../assumptions.md) con Cocos)*.

### Requirement: Ejecución inmediata de órdenes MARKET

Una orden MARKET con disponible suficiente DEBE registrarse en estado `FILLED`, con el último precio del instrumento como precio de ejecución. Al quedar ejecutada, DEBE impactar en el efectivo y en la tenencia del usuario.

#### Scenario: ORD-12 · Compra MARKET

- **GIVEN** un usuario con 100.000 ARS disponibles
- **AND** el último precio de GGAL es 885,80
- **WHEN** envía una orden BUY MARKET de 10 GGAL
- **THEN** la orden se registra `FILLED` por 10 GGAL a 885,80
- **AND** su efectivo baja 8.858 ARS
- **AND** su tenencia de GGAL sube 10

#### Scenario: ORD-13 · Venta MARKET

- **GIVEN** un usuario con 40 PAMP disponibles
- **AND** el último precio de PAMP es 925,85
- **WHEN** envía una orden SELL MARKET de 10 PAMP
- **THEN** la orden se registra `FILLED` por 10 PAMP a 925,85
- **AND** su efectivo sube 9.258,50 ARS
- **AND** su tenencia de PAMP pasa a ser 30

### Requirement: Alta de órdenes LIMIT

Una orden LIMIT con disponible suficiente DEBE registrarse en estado `NEW` con el precio límite informado, y DEBE reservar el disponible según `account-balance`. NO DEBE impactar en el efectivo ni en la tenencia, aunque su precio límite cruce el último precio.

#### Scenario: ORD-14 · Compra LIMIT

- **GIVEN** un usuario con 100.000 ARS disponibles
- **WHEN** envía una orden BUY LIMIT de 10 GGAL a 850
- **THEN** la orden se registra `NEW` por 10 GGAL a 850
- **AND** sus pesos disponibles pasan a ser 91.500 ARS
- **AND** su efectivo y su tenencia de GGAL no cambian

#### Scenario: ORD-15 · Compra LIMIT por encima del último precio

- **GIVEN** un usuario con 100.000 ARS disponibles
- **AND** el último precio de GGAL es 885,80
- **WHEN** envía una orden BUY LIMIT de 10 GGAL a 900
- **THEN** la orden se registra `NEW`
- **AND** el sistema NO DEBE ejecutarla

#### Scenario: ORD-16 · Venta LIMIT

- **GIVEN** un usuario con 40 PAMP disponibles
- **WHEN** envía una orden SELL LIMIT de 15 PAMP a 1.000
- **THEN** la orden se registra `NEW` por 15 PAMP a 1.000
- **AND** tiene 25 PAMP disponibles para vender
- **AND** su tenencia de PAMP sigue siendo 40

La reserva *(pendiente: confirmar supuesto [S1](../../assumptions.md) con Cocos)*. Las órdenes que cruzan el precio quedan en `NEW` según el supuesto [S7](../../assumptions.md).

### Requirement: Rechazo de compras por pesos insuficientes

Una compra cuyo importe (`cantidad × precio`, con el último precio en MARKET y el precio límite en LIMIT) supere los pesos disponibles DEBE registrarse en estado `REJECTED` y NO DEBE impactar en el efectivo, en las tenencias ni en las reservas. El rechazo es un resultado del procesamiento, no un error del pedido.

#### Scenario: ORD-17 · Compra MARKET sin pesos suficientes

- **GIVEN** un usuario con 100.000 ARS disponibles
- **AND** el último precio de GGAL es 885,80
- **WHEN** envía una orden BUY MARKET de 200 GGAL
- **THEN** la orden se registra `REJECTED`
- **AND** la respuesta informa el estado `REJECTED`
- **AND** sus pesos disponibles siguen siendo 100.000 ARS

#### Scenario: ORD-18 · Una reserva previa reduce el disponible

- **GIVEN** un usuario con 100.000 ARS de efectivo
- **AND** una orden BUY LIMIT `NEW` de 100 GGAL a 900, que reserva 90.000 ARS
- **AND** el último precio de GGAL es 885,80
- **WHEN** envía una orden BUY MARKET de 20 GGAL, por 17.716 ARS
- **THEN** la orden se registra `REJECTED`

#### Scenario: ORD-19 · Compra por exactamente el disponible

- **GIVEN** un usuario con 8.858 ARS disponibles
- **AND** el último precio de GGAL es 885,80
- **WHEN** envía una orden BUY MARKET de 10 GGAL
- **THEN** la orden se registra `FILLED`
- **AND** sus pesos disponibles pasan a ser 0 ARS

#### Scenario: ORD-20 · Compra LIMIT sin pesos suficientes

- **GIVEN** un usuario con 5.000 ARS disponibles
- **WHEN** envía una orden BUY LIMIT de 10 GGAL a 850
- **THEN** la orden se registra `REJECTED`
- **AND** no reserva pesos

### Requirement: Rechazo de ventas por acciones insuficientes

Una venta por una cantidad mayor a las acciones disponibles para vender del instrumento DEBE registrarse en estado `REJECTED` y NO DEBE impactar en el efectivo, en las tenencias ni en las reservas.

#### Scenario: ORD-21 · Venta de más acciones que la tenencia

- **GIVEN** un usuario con 40 PAMP
- **WHEN** envía una orden SELL MARKET de 50 PAMP
- **THEN** la orden se registra `REJECTED`
- **AND** su tenencia de PAMP sigue siendo 40

#### Scenario: ORD-22 · Acciones comprometidas en una venta límite

- **GIVEN** un usuario con 40 PAMP
- **AND** una orden SELL LIMIT `NEW` de 30 PAMP
- **WHEN** envía una orden SELL MARKET de 20 PAMP
- **THEN** la orden se registra `REJECTED`

#### Scenario: ORD-23 · Venta sobre una tenencia negativa

- **GIVEN** el usuario 1 de la seed, con tenencia de −10 BMA
- **WHEN** envía una orden SELL MARKET de 1 BMA
- **THEN** la orden se registra `REJECTED`

#### Scenario: ORD-24 · Venta de un instrumento que no tiene

- **GIVEN** un usuario sin tenencia de GGAL
- **WHEN** envía una orden SELL LIMIT de 5 GGAL a 900
- **THEN** la orden se registra `REJECTED`

Tratamiento de la tenencia negativa según el supuesto [S3](../../assumptions.md).

### Requirement: Orden MARKET sobre un instrumento sin cotización

Una orden MARKET sobre un instrumento que no tiene ninguna cotización DEBE registrarse en estado `REJECTED`, sin precio de ejecución, porque no hay un precio contra el cual ejecutarla. Una orden LIMIT sobre ese instrumento DEBE procesarse normalmente, porque el precio lo informa el usuario.

#### Scenario: ORD-25 · Compra MARKET sin cotización

- **GIVEN** un usuario con 100.000 ARS disponibles
- **AND** PGR no tiene ninguna cotización registrada
- **WHEN** envía una orden BUY MARKET de 10 PGR
- **THEN** la orden se registra `REJECTED` sin precio

#### Scenario: ORD-26 · Compra LIMIT sin cotización

- **GIVEN** un usuario con 100.000 ARS disponibles
- **AND** PGR no tiene ninguna cotización registrada
- **WHEN** envía una orden BUY LIMIT de 10 PGR a 50
- **THEN** la orden se registra `NEW` por 10 PGR a 50

El tratamiento de instrumentos sin cotización *(pendiente: confirmar supuesto [S5](../../assumptions.md) con Cocos)*. Qué cantidad se registra en una orden MARKET por monto rechazada por falta de cotización, ya que sin precio no se puede calcular *(pendiente: definir)*.

### Requirement: Registro de la orden

Toda orden aceptada o rechazada DEBE quedar registrada con el usuario, el instrumento, el lado, el tipo, la cantidad, el precio, el estado y la fecha y hora de envío. La respuesta DEBE devolver la orden registrada con su identificador y su estado.

#### Scenario: ORD-27 · Respuesta de una orden registrada

- **GIVEN** un usuario con 100.000 ARS disponibles
- **WHEN** envía una orden BUY LIMIT de 10 GGAL a 850
- **THEN** la respuesta devuelve la orden con su identificador, estado `NEW`, cantidad 10, precio 850 y fecha y hora de envío
- **AND** la orden se puede encontrar en el libro de órdenes del usuario

### Requirement: Órdenes simultáneas del mismo usuario

El sistema NO DEBE aceptar órdenes simultáneas de un mismo usuario que, en conjunto, superen su disponible. Cada orden DEBE validarse contra el disponible que resulta de las órdenes procesadas antes que ella.

#### Scenario: ORD-28 · Dos compras simultáneas que superan el disponible

- **GIVEN** un usuario con 10.000 ARS disponibles
- **AND** el último precio de GGAL es 885,80
- **WHEN** envía al mismo tiempo dos órdenes BUY MARKET de 10 GGAL, de 8.858 ARS cada una
- **THEN** una orden se registra `FILLED` y la otra `REJECTED`
- **AND** su efectivo NO DEBE quedar negativo

#### Scenario: ORD-29 · Dos ventas simultáneas que superan la tenencia

- **GIVEN** un usuario con 40 PAMP
- **WHEN** envía al mismo tiempo dos órdenes SELL MARKET de 30 PAMP
- **THEN** una orden se registra `FILLED` y la otra `REJECTED`
- **AND** su tenencia de PAMP NO DEBE quedar negativa
