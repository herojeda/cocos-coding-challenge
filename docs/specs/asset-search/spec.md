---
title: Búsqueda de activos
---

# Búsqueda de activos

## Purpose

Permite buscar instrumentos del mercado por ticker o por nombre, para que el usuario encuentre el activo sobre el que quiere operar. Cubre el criterio de coincidencia, qué instrumentos se pueden encontrar, qué datos se informan de cada uno y el orden de los resultados.

Fuentes: [enunciado del challenge](https://github.com/cocos-capital/cocos-challenge/blob/main/backend-challenge.md), [database.sql](https://github.com/cocos-capital/cocos-challenge/blob/main/database.sql), [supuesto S10](../../assumptions.md).

Fuera de alcance: precios y valuación de las tenencias del usuario (`portfolio`); envío de órdenes sobre el activo encontrado (`order-placement`).

## Requirements

### Requirement: Búsqueda por ticker o por nombre

El sistema DEBE devolver los instrumentos cuyo ticker o nombre contengan el texto buscado, sin distinguir mayúsculas ni tildes.

#### Scenario: AS-01 · Búsqueda por ticker exacto

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca "GGAL"
- **THEN** el resultado incluye Grupo Financiero Galicia (GGAL)

#### Scenario: AS-02 · Búsqueda por parte del ticker

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca "tgs"
- **THEN** el resultado incluye Transportadora de Gas del Sur (TGSU2)

#### Scenario: AS-03 · Búsqueda por parte del nombre

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca "banco"
- **THEN** el resultado tiene exactamente BBAR, BHIP, BMA y BPAT

#### Scenario: AS-04 · Búsqueda sin tildes

- **GIVEN** el catálogo de instrumentos de la seed, que incluye "Molinos Río de la Plata" (MOLI)
- **WHEN** un usuario busca "rio de la plata"
- **THEN** el resultado incluye MOLI

### Requirement: Los pesos no son un activo buscable

La búsqueda NO DEBE devolver instrumentos de tipo `MONEDA`, porque no son activos sobre los que se pueda operar.

#### Scenario: AS-05 · Búsqueda de los pesos

- **GIVEN** el catálogo de la seed, que tiene el instrumento ARS (PESOS) de tipo `MONEDA`
- **WHEN** un usuario busca "pesos"
- **THEN** el resultado está vacío

### Requirement: Datos de cada resultado

Cada resultado DEBE informar el identificador, el ticker, el nombre y el tipo del instrumento.

#### Scenario: AS-06 · Datos de un instrumento encontrado

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca "YPFD"
- **THEN** el resultado tiene el instrumento con ticker YPFD, nombre "Y.P.F. S.A." y tipo `ACCIONES`
- **AND** informa su identificador

### Requirement: Orden de los resultados

Los resultados DEBEN ordenarse por relevancia: primero el instrumento cuyo ticker coincide exactamente con el texto, después los que tienen un ticker que empieza con el texto y por último el resto. Dentro de cada grupo, el orden DEBE ser alfabético por ticker.

#### Scenario: AS-07 · Coincidencia exacta de ticker primero

- **GIVEN** el catálogo de la seed, con MOLI (Molinos Río de la Plata), MOLA (Molinos Agro S.A.) y SEMI (Molinos Juan Semino)
- **WHEN** un usuario busca "moli"
- **THEN** el resultado es MOLI, MOLA y SEMI, en ese orden

### Requirement: Texto de búsqueda

El sistema DEBE rechazar con un error de validación una búsqueda sin texto o con solo espacios. Una búsqueda válida sin coincidencias NO DEBE responderse como error.

#### Scenario: AS-08 · Búsqueda vacía

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca un texto con solo espacios
- **THEN** el pedido se responde con un error de validación

#### Scenario: AS-09 · Búsqueda sin coincidencias

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca "zzz"
- **THEN** el resultado es una lista vacía

La cantidad máxima de resultados por búsqueda *(pendiente: definir un límite)*.
