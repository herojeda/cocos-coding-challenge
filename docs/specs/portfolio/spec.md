---
title: Portfolio
---

# Portfolio

## Purpose

Expone la foto de la cuenta de un usuario: el valor total, los pesos disponibles para operar y las posiciones en activos, cada una con su cantidad, su valor de mercado y su rendimiento. Cubre la valuación al último precio, el rendimiento sobre costo promedio ponderado, los datos complementarios de cada posición y los casos de datos incompletos o inconsistentes.

Fuentes: [enunciado del challenge](https://github.com/cocos-capital/cocos-challenge/blob/main/backend-challenge.md), [database.sql](https://github.com/cocos-capital/cocos-challenge/blob/main/database.sql), [supuestos S1, S2, S3, S5, S9 y S11](../../assumptions.md).

Fuera de alcance: cálculo del efectivo, las tenencias, las reservas y el disponible (`account-balance`); envío y cancelación de órdenes (`order-placement`, `order-cancellation`); rendimiento del portfolio en su conjunto (rendimiento ponderado en el tiempo o tasa interna de retorno), que el enunciado no pide y requeriría valuaciones históricas.

## Requirements

### Requirement: Contenido del portfolio

El portfolio de un usuario DEBE informar el valor total de la cuenta, los pesos disponibles para operar y el listado de posiciones. Cada posición DEBE informar el ticker y el nombre del instrumento, la cantidad, el valor de mercado y el rendimiento total.

#### Scenario: PF-01 · Portfolio del usuario de la seed

- **GIVEN** el usuario 1 con las órdenes de la seed provista
- **AND** últimos precios de 925,85 para PAMP, 229,50 para METR y 1.502,80 para BMA
- **WHEN** consulta su portfolio
- **THEN** sus pesos disponibles son 627.500 ARS
- **AND** sus posiciones son PAMP (40), METR (500) y BMA (−10)
- **AND** el valor total de su cuenta es 889.756 ARS

#### Scenario: PF-02 · Usuario sin movimientos

- **GIVEN** un usuario sin órdenes registradas
- **WHEN** consulta su portfolio
- **THEN** el valor total es 0 ARS
- **AND** los pesos disponibles son 0 ARS
- **AND** el listado de posiciones está vacío

#### Scenario: PF-03 · Usuario inexistente

- **GIVEN** que no existe un usuario con el identificador informado
- **WHEN** se consulta su portfolio
- **THEN** el pedido se responde con un error de recurso inexistente

### Requirement: Valor total de la cuenta

El valor total DEBE calcularse como el efectivo en pesos más la suma del valor de mercado de las posiciones. Los pesos reservados por órdenes pendientes siguen siendo del usuario, así que NO DEBEN descontarse del valor total.

#### Scenario: PF-04 · Una reserva no reduce el valor total

- **GIVEN** un usuario con 100.000 ARS de efectivo y sin posiciones
- **AND** una orden BUY LIMIT `NEW` de 10 GGAL a 900
- **WHEN** consulta su portfolio
- **THEN** sus pesos disponibles son 91.000 ARS
- **AND** el valor total de su cuenta es 100.000 ARS

### Requirement: Valuación al último precio

El valor de mercado de una posición DEBE calcularse como la cantidad por el último precio del instrumento, que es el `close` de su cotización con fecha más reciente.

#### Scenario: PF-05 · Se usa la cotización más reciente

- **GIVEN** un usuario con 40 PAMP
- **AND** cotizaciones de PAMP con `close` 921,80 el 13/07/2023 y 925,85 el 14/07/2023
- **WHEN** consulta su portfolio
- **THEN** el valor de mercado de su posición en PAMP es 37.034 ARS

Criterio de último precio según el supuesto [S9](../../assumptions.md).

### Requirement: Posiciones que se listan

El listado DEBE incluir solo los instrumentos con tenencia distinta de cero. Los pesos NO DEBEN aparecer como una posición: se informan como pesos disponibles.

#### Scenario: PF-06 · Una posición cerrada no se lista

- **GIVEN** un usuario que compró y después vendió 10 GGAL, ambas órdenes `FILLED`
- **WHEN** consulta su portfolio
- **THEN** GGAL no aparece en el listado de posiciones

#### Scenario: PF-07 · Los pesos no son una posición

- **GIVEN** un usuario con 100.000 ARS de efectivo y sin acciones
- **WHEN** consulta su portfolio
- **THEN** el listado de posiciones está vacío
- **AND** los pesos se informan solo como pesos disponibles

### Requirement: Rendimiento total sobre costo promedio ponderado

El rendimiento total de una posición DEBE calcularse como `último precio / costo promedio − 1`, expresado como porcentaje. El costo promedio DEBE calcularse recorriendo las órdenes `FILLED` del instrumento en orden cronológico: una compra recalcula el promedio ponderado, una venta reduce la cantidad sin modificar el promedio, y una tenencia que vuelve a cero reinicia el promedio.

#### Scenario: PF-08 · Compras a distintos precios

- **GIVEN** un usuario que compró 10 acciones a 100 y después 10 a 200
- **AND** el último precio del instrumento es 180
- **WHEN** consulta su portfolio
- **THEN** el costo promedio de la posición es 150
- **AND** el rendimiento total es 20%

#### Scenario: PF-09 · Una venta parcial no cambia el costo promedio

- **GIVEN** un usuario que compró 10 acciones a 100, después 10 a 200 y después vendió 10 a 150
- **AND** el último precio del instrumento es 180
- **WHEN** consulta su portfolio
- **THEN** la posición tiene 10 acciones con costo promedio 150
- **AND** el rendimiento total es 20%

#### Scenario: PF-10 · Una posición que vuelve a cero reinicia el costo

- **GIVEN** un usuario que compró 10 acciones a 100, las vendió todas a 120 y después compró 10 a 200
- **AND** el último precio del instrumento es 180
- **WHEN** consulta su portfolio
- **THEN** el costo promedio de la posición es 200
- **AND** el rendimiento total es −10%

#### Scenario: PF-11 · Rendimiento de PAMP en la seed

- **GIVEN** el usuario 1 de la seed, que compró 50 PAMP a 930 y vendió 10 a 940
- **AND** el último precio de PAMP es 925,85
- **WHEN** consulta su portfolio
- **THEN** la posición en PAMP tiene 40 acciones con costo promedio 930
- **AND** el rendimiento total es −0,45%

El método de costo y el redondeo de los porcentajes *(pendiente: confirmar supuesto [S2](../../assumptions.md) con Cocos y definir la precisión de los porcentajes)*.

### Requirement: Datos complementarios de cada posición

Cada posición DEBE informar, además, el costo promedio, el resultado realizado y la variación diaria. El resultado realizado DEBE acumular `(precio de venta − costo promedio vigente) × cantidad vendida` de cada venta, calculado solo sobre la cantidad vendida que estaba en tenencia: si una venta supera la tenencia, el excedente NO DEBE generar resultado realizado, porque esas acciones nunca formaron parte de la tenencia. La variación diaria DEBE calcularse como `close / previousClose − 1` de la cotización más reciente.

#### Scenario: PF-12 · Resultado realizado de una venta parcial

- **GIVEN** un usuario que compró 10 acciones a 100, después 10 a 200 y después vendió 10 a 180
- **WHEN** consulta su portfolio
- **THEN** el resultado realizado de la posición es 300 ARS

#### Scenario: PF-13 · Datos complementarios de PAMP en la seed

- **GIVEN** el usuario 1 de la seed, que compró 50 PAMP a 930 y vendió 10 a 940
- **AND** la cotización más reciente de PAMP tiene `close` 925,85 y `previousClose` 921,80
- **WHEN** consulta su portfolio
- **THEN** el resultado realizado de PAMP es 100 ARS
- **AND** la variación diaria de PAMP es 0,44%

#### Scenario: PF-16 · Venta que supera la tenencia

- **GIVEN** el usuario 1 de la seed, que compró 20 BMA a 1.540 y después vendió 30 a 1.530
- **WHEN** consulta su portfolio
- **THEN** el resultado realizado de BMA es −200 ARS, calculado sobre las 20 acciones que tenía
- **AND** las 10 acciones vendidas de más NO DEBEN generar resultado realizado

### Requirement: Posición con tenencia negativa

Una posición con tenencia negativa DEBE listarse con su cantidad y su valor de mercado, que es negativo y resta del valor total. El costo promedio y el rendimiento de esa posición NO DEBEN calcularse: se informan sin valor. Una compra sobre una tenencia negativa DEBE compensar primero el faltante, sin generar costo ni resultado realizado; solo la cantidad que queda por encima de cero forma el costo promedio.

#### Scenario: PF-14 · BMA negativo en la seed

- **GIVEN** el usuario 1 de la seed, con tenencia de −10 BMA
- **AND** el último precio de BMA es 1.502,80
- **WHEN** consulta su portfolio
- **THEN** la posición en BMA tiene cantidad −10 y valor de mercado −15.028 ARS
- **AND** el costo promedio y el rendimiento total de BMA se informan sin valor

#### Scenario: PF-17 · Compra sobre una tenencia negativa

- **GIVEN** el usuario 1 de la seed, con tenencia de −10 BMA y resultado realizado de −200 ARS en BMA
- **AND** una compra `FILLED` posterior de 15 BMA a 1.500
- **WHEN** consulta su portfolio
- **THEN** la posición en BMA tiene cantidad 5 y costo promedio 1.500
- **AND** el resultado realizado de BMA sigue siendo −200 ARS

El tratamiento de la tenencia negativa sigue el supuesto [S3](../../assumptions.md), confirmado por Cocos.

### Requirement: Posición sin cotización

Una posición en un instrumento sin ninguna cotización DEBE listarse con su cantidad, pero su valor de mercado, su rendimiento y su variación diaria NO DEBEN calcularse, y la posición NO DEBE sumar al valor total.

#### Scenario: PF-15 · Tenencia en un instrumento sin cotizaciones

- **GIVEN** un usuario con 100.000 ARS de efectivo y 100 PGR
- **AND** PGR no tiene ninguna cotización registrada
- **WHEN** consulta su portfolio
- **THEN** la posición en PGR tiene cantidad 100
- **AND** su valor de mercado, su rendimiento y su variación diaria se informan sin valor
- **AND** el valor total de su cuenta es 100.000 ARS

El tratamiento de instrumentos sin cotización sigue el supuesto [S5](../../assumptions.md), confirmado por Cocos.
