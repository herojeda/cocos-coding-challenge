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

La búsqueda NO DEBE devolver instrumentos de tipo `CURRENCY`, porque no son activos sobre los que se pueda operar.

#### Scenario: AS-05 · Búsqueda de los pesos

- **GIVEN** el catálogo de la seed, que tiene el instrumento ARS (PESOS) de tipo `CURRENCY`
- **WHEN** un usuario busca "pesos"
- **THEN** el resultado está vacío

### Requirement: Datos de cada resultado

Cada resultado DEBE informar el identificador, el ticker, el nombre y el tipo del instrumento.

#### Scenario: AS-06 · Datos de un instrumento encontrado

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca "YPFD"
- **THEN** el resultado tiene el instrumento con ticker YPFD, nombre "Y.P.F. S.A." y tipo `STOCK`
- **AND** informa su identificador

### Requirement: Orden de los resultados

Los resultados DEBEN ordenarse por relevancia: primero el instrumento cuyo ticker coincide exactamente con el texto, después los que tienen un ticker que empieza con el texto y por último el resto. Dentro de cada grupo, el orden DEBE ser alfabético por ticker.

#### Scenario: AS-07 · Coincidencia exacta de ticker primero

- **GIVEN** el catálogo de la seed, con MOLI (Molinos Río de la Plata), MOLA (Molinos Agro S.A.) y SEMI (Molinos Juan Semino)
- **WHEN** un usuario busca "moli"
- **THEN** el resultado es MOLI, MOLA y SEMI, en ese orden

### Requirement: Texto de búsqueda

El sistema DEBE rechazar con un error de validación una búsqueda sin texto o con solo espacios. Una búsqueda válida sin coincidencias NO DEBE responderse como error. El texto DEBE buscarse sin los espacios del principio y del final, y como texto literal: ningún carácter del texto DEBE actuar como comodín.

#### Scenario: AS-08 · Búsqueda vacía

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca un texto con solo espacios
- **THEN** el pedido se responde con un error de validación

#### Scenario: AS-09 · Búsqueda sin coincidencias

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca "zzz"
- **THEN** el resultado es una lista vacía

#### Scenario: AS-10 · Texto con espacios alrededor

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca "  moli  "
- **THEN** el resultado es MOLI, MOLA y SEMI, en ese orden

#### Scenario: AS-11 · Caracteres que no son comodines

- **GIVEN** el catálogo de instrumentos de la seed, en el que ningún ticker ni nombre contiene "%" ni "_"
- **WHEN** un usuario busca "%"
- **THEN** el resultado es una lista vacía
- **AND** lo mismo ocurre si busca "_"

### Requirement: Paginación de los resultados

Los resultados DEBEN entregarse por páginas, en el orden de relevancia. Cada página DEBE informar si hay más resultados después de ella. Si el usuario no indica la página ni el tamaño, el sistema DEBE devolver la primera página con 20 resultados como máximo. El tamaño de página DEBE estar entre 1 y 100, y el número de página NO DEBE ser negativo; un valor fuera de rango DEBE rechazarse con un error de validación. Una página posterior a la última NO DEBE responderse como error.

#### Scenario: AS-12 · Primera página por defecto

- **GIVEN** el catálogo de la seed, en el que 59 instrumentos contienen "a" en el ticker o en el nombre
- **WHEN** un usuario busca "a" sin indicar página ni tamaño
- **THEN** el resultado tiene 20 instrumentos, de AGRO a CVH en el orden de relevancia
- **AND** informa que hay más resultados

#### Scenario: AS-13 · Páginas siguientes

- **GIVEN** el catálogo de la seed, en el que la búsqueda "banco" encuentra BBAR, BHIP, BMA y BPAT
- **WHEN** un usuario busca "banco" con páginas de 2 resultados
- **THEN** la primera página es BBAR y BHIP, e informa que hay más resultados
- **AND** la segunda página es BMA y BPAT, e informa que no hay más resultados

#### Scenario: AS-14 · Página posterior a la última

- **GIVEN** el catálogo de la seed, en el que la búsqueda "banco" encuentra 4 instrumentos
- **WHEN** un usuario pide la tercera página de "banco" con páginas de 2 resultados
- **THEN** el resultado es una lista vacía
- **AND** informa que no hay más resultados

#### Scenario: AS-15 · Tamaño de página fuera de rango

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca "banco" con páginas de 0 resultados
- **THEN** el pedido se responde con un error de validación
- **AND** lo mismo ocurre con páginas de 101 resultados

#### Scenario: AS-16 · Número de página negativo

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario pide la página −1 de "banco"
- **THEN** el pedido se responde con un error de validación
