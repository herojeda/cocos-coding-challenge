---
title: Búsqueda de activos
---

# Búsqueda de activos

## Purpose

Permite buscar instrumentos del mercado por ticker, por nombre o por los dos, para que el usuario encuentre el activo sobre el que quiere operar. Cubre los filtros y su criterio de coincidencia, qué instrumentos se pueden encontrar, qué datos se informan de cada uno, el orden y la paginación de los resultados.

Fuentes: [enunciado del challenge](https://github.com/cocos-capital/cocos-challenge/blob/main/backend-challenge.md) ("tiene que soportar búsqueda por ticker y/o por nombre"), [database.sql](https://github.com/cocos-capital/cocos-challenge/blob/main/database.sql), [supuesto S10](../../assumptions.md).

Fuera de alcance: precios y valuación de las tenencias del usuario (`portfolio`); envío de órdenes sobre el activo encontrado (`order-placement`).

## Requirements

### Requirement: Filtro por ticker

El sistema DEBE permitir filtrar por ticker: con el filtro de ticker, el resultado DEBE tener solo los instrumentos cuyo ticker contenga el texto del filtro, sin distinguir mayúsculas ni tildes. El filtro de ticker NO DEBE buscar en el nombre.

#### Scenario: AS-01 · Filtro por ticker exacto

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario filtra por ticker "GGAL"
- **THEN** el resultado incluye Grupo Financiero Galicia (GGAL)

#### Scenario: AS-02 · Filtro por parte del ticker

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario filtra por ticker "tgs"
- **THEN** el resultado incluye Transportadora de Gas del Sur (TGSU2)

#### Scenario: AS-17 · El filtro de ticker no busca en el nombre

- **GIVEN** el catálogo de la seed, en el que cuatro instrumentos tienen "Banco" en el nombre y ninguno en el ticker
- **WHEN** un usuario filtra por ticker "banco"
- **THEN** el resultado es una lista vacía

### Requirement: Filtro por nombre

El sistema DEBE permitir filtrar por nombre: con el filtro de nombre, el resultado DEBE tener solo los instrumentos cuyo nombre contenga el texto del filtro, sin distinguir mayúsculas ni tildes. El filtro de nombre NO DEBE buscar en el ticker.

#### Scenario: AS-03 · Filtro por parte del nombre

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario filtra por nombre "banco"
- **THEN** el resultado tiene exactamente BBAR, BHIP, BMA y BPAT

#### Scenario: AS-04 · Filtro por nombre sin tildes

- **GIVEN** el catálogo de instrumentos de la seed, que incluye "Molinos Río de la Plata" (MOLI)
- **WHEN** un usuario filtra por nombre "rio de la plata"
- **THEN** el resultado incluye MOLI

#### Scenario: AS-18 · El filtro de nombre no busca en el ticker

- **GIVEN** el catálogo de la seed, en el que BMA (Banco Macro S.A.) es el único instrumento con "bma" en el ticker y ninguno lo tiene en el nombre
- **WHEN** un usuario filtra por nombre "bma"
- **THEN** el resultado es una lista vacía

### Requirement: Combinación de filtros

Cuando el pedido tiene los dos filtros, el resultado DEBE tener solo los instrumentos que cumplen ambos. Cuando el pedido no tiene ningún filtro, el resultado DEBE ser el catálogo completo de instrumentos que se pueden buscar.

#### Scenario: AS-19 · Ticker y nombre juntos

- **GIVEN** el catálogo de la seed, con MOLA (Molinos Agro S.A.), MOLI (Molinos Río de la Plata) y SEMI (Molinos Juan Semino)
- **WHEN** un usuario filtra por ticker "mol" y por nombre "molinos"
- **THEN** el resultado es MOLA y MOLI, sin SEMI

#### Scenario: AS-20 · Sin filtros

- **GIVEN** el catálogo de la seed, con 65 instrumentos de tipo `STOCK` y el instrumento ARS de tipo `CURRENCY`
- **WHEN** un usuario busca sin filtros, con páginas de 100 resultados
- **THEN** el resultado tiene los 65 instrumentos de tipo `STOCK`, sin ARS
- **AND** informa que no hay más resultados

### Requirement: Los pesos no son un activo buscable

La búsqueda NO DEBE devolver instrumentos de tipo `CURRENCY`, con o sin filtros, porque no son activos sobre los que se pueda operar.

#### Scenario: AS-05 · Búsqueda de los pesos

- **GIVEN** el catálogo de la seed, que tiene el instrumento ARS (PESOS) de tipo `CURRENCY`
- **WHEN** un usuario filtra por nombre "pesos"
- **THEN** el resultado está vacío
- **AND** lo mismo ocurre si filtra por ticker "ars"

### Requirement: Datos de cada resultado

Cada resultado DEBE informar el identificador, el ticker, el nombre y el tipo del instrumento.

#### Scenario: AS-06 · Datos de un instrumento encontrado

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario filtra por ticker "YPFD"
- **THEN** el resultado tiene el instrumento con ticker YPFD, nombre "Y.P.F. S.A." y tipo `STOCK`
- **AND** informa su identificador

### Requirement: Orden de los resultados

El usuario DEBE poder elegir el orden entre `RELEVANCE`, `TICKER` y `NAME`, siempre ascendente. Si no lo indica, el orden DEBE ser `RELEVANCE`. Un orden fuera de esos valores DEBE rechazarse con un error de validación.

- `RELEVANCE` ordena por cercanía al filtro: primero el instrumento que coincide exactamente con el texto, después los que empiezan con el texto y por último el resto. Se calcula sobre el filtro de ticker si está presente y, si no, sobre el de nombre. Sin filtros, equivale a `TICKER`.
- `TICKER` ordena alfabéticamente por ticker.
- `NAME` ordena alfabéticamente por nombre, sin distinguir mayúsculas ni tildes.

En todos los casos, los instrumentos que empatan DEBEN quedar en orden alfabético por ticker.

#### Scenario: AS-07 · Relevancia sobre el ticker

- **GIVEN** el catálogo de la seed, con PATA y BPAT, más un instrumento de prueba con ticker PAT
- **WHEN** un usuario filtra por ticker "pat" sin indicar el orden
- **THEN** el resultado es PAT, PATA y BPAT, en ese orden

#### Scenario: AS-21 · Relevancia sobre el nombre

- **GIVEN** el catálogo de la seed, en el que seis instrumentos tienen "gas" en el nombre y solo GBAN (Gas Natural Ban S.A.) empieza con esa palabra
- **WHEN** un usuario filtra por nombre "gas" sin indicar el orden
- **THEN** el resultado es GBAN, CGPA2, DGCU2, METR, TGNO4 y TGSU2, en ese orden

#### Scenario: AS-22 · Orden por ticker

- **GIVEN** el catálogo de la seed, con GCLA (Grupo Clarin S.A.), GGAL (Grupo Financiero Galicia), SUPV (Grupo Supervielle S.A.) y OEST (Oeste Grupo Concesionario)
- **WHEN** un usuario filtra por nombre "grupo" con orden `TICKER`
- **THEN** el resultado es GCLA, GGAL, OEST y SUPV, en ese orden

#### Scenario: AS-23 · Orden por nombre

- **GIVEN** el catálogo de la seed, con seis instrumentos que tienen "gas" en el nombre
- **WHEN** un usuario filtra por nombre "gas" con orden `NAME`
- **THEN** el resultado es CGPA2 (Camuzzi Gas del Sur), DGCU2 (Distribuidora De Gas Cuyano S.A.), GBAN (Gas Natural Ban S.A.), METR (MetroGAS S.A.), TGNO4 (Transportadora de Gas del Norte) y TGSU2 (Transportadora de Gas del Sur), en ese orden

#### Scenario: AS-24 · Orden inválido

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca con un orden que no es `RELEVANCE`, `TICKER` ni `NAME`
- **THEN** el pedido se responde con un error de validación

### Requirement: Texto de los filtros

El sistema DEBE rechazar con un error de validación un filtro presente sin texto o con solo espacios; un filtro ausente no es un error. Una búsqueda válida sin coincidencias NO DEBE responderse como error. El texto de cada filtro DEBE buscarse sin los espacios del principio y del final, y como texto literal: ningún carácter del texto DEBE actuar como comodín.

#### Scenario: AS-08 · Filtro vacío

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario filtra por ticker con un texto de solo espacios
- **THEN** el pedido se responde con un error de validación
- **AND** lo mismo ocurre con el filtro de nombre

#### Scenario: AS-09 · Búsqueda sin coincidencias

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario filtra por ticker "zzz"
- **THEN** el resultado es una lista vacía

#### Scenario: AS-10 · Texto con espacios alrededor

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario filtra por nombre "  rio de la plata  "
- **THEN** el resultado incluye MOLI

#### Scenario: AS-11 · Caracteres que no son comodines

- **GIVEN** el catálogo de instrumentos de la seed, en el que ningún ticker ni nombre contiene "%" ni "_"
- **WHEN** un usuario filtra por ticker "%"
- **THEN** el resultado es una lista vacía
- **AND** lo mismo ocurre si filtra por nombre "_"

### Requirement: Paginación de los resultados

Los resultados DEBEN entregarse por páginas, en el orden pedido. Cada página DEBE informar si hay más resultados después de ella. Si el usuario no indica la página ni el tamaño, el sistema DEBE devolver la primera página con 20 resultados como máximo. El tamaño de página DEBE estar entre 1 y 100, y el número de página NO DEBE ser negativo; un valor fuera de rango DEBE rechazarse con un error de validación. Una página posterior a la última NO DEBE responderse como error.

#### Scenario: AS-12 · Primera página por defecto

- **GIVEN** el catálogo de la seed, con 65 instrumentos que se pueden buscar
- **WHEN** un usuario busca sin filtros y sin indicar página, tamaño ni orden
- **THEN** el resultado tiene 20 instrumentos, de AGRO a CVH en orden alfabético por ticker
- **AND** informa que hay más resultados

#### Scenario: AS-13 · Páginas siguientes

- **GIVEN** el catálogo de la seed, en el que el filtro por nombre "banco" encuentra BBAR, BHIP, BMA y BPAT
- **WHEN** un usuario filtra por nombre "banco" con páginas de 2 resultados
- **THEN** la primera página es BBAR y BHIP, e informa que hay más resultados
- **AND** la segunda página es BMA y BPAT, e informa que no hay más resultados

#### Scenario: AS-14 · Página posterior a la última

- **GIVEN** el catálogo de la seed, en el que el filtro por nombre "banco" encuentra 4 instrumentos
- **WHEN** un usuario pide la tercera página del filtro por nombre "banco" con páginas de 2 resultados
- **THEN** el resultado es una lista vacía
- **AND** informa que no hay más resultados

#### Scenario: AS-15 · Tamaño de página fuera de rango

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario busca con páginas de 0 resultados
- **THEN** el pedido se responde con un error de validación
- **AND** lo mismo ocurre con páginas de 101 resultados

#### Scenario: AS-16 · Número de página negativo

- **GIVEN** el catálogo de instrumentos de la seed
- **WHEN** un usuario pide la página −1
- **THEN** el pedido se responde con un error de validación
