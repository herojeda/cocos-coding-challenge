# ADR-004: Convenciones del esquema y migraciones

**Estado:** Aceptado

## Contexto

Cocos provee la base con un [`database.sql`](https://github.com/cocos-capital/cocos-challenge/blob/main/database.sql) y permite modificarla si los cambios se justifican. El esquema provisto tiene varios problemas:

- **Los nombres en camelCase no existen en la base.** PostgreSQL convierte a minúsculas todo identificador sin comillas, así que `accountNumber` se guardó como `accountnumber` y `previousClose` como `previousclose`.
- **Los valores de enumerados mezclan idiomas:** `instruments.type` usa `ACCIONES` y `MONEDA`, mientras que las órdenes usan `BUY`, `FILLED` o `MARKET`.
- **No hay constraints ni índices** más allá de las claves primarias y foráneas: no hay `NOT NULL`, ni unicidad de ticker, email o número de cuenta, ni `CHECK` sobre los enumerados.
- **Los identificadores son `INTEGER`** (`SERIAL` y FKs `INT`), con un máximo de 2.147.483.647 valores. Para `orders`, un libro que crece con cada operación de cada usuario y nunca se depura, es un límite alcanzable en un broker real; y migrar el tipo de una clave primaria con la tabla ya grande es una operación costosa, que bloquea la tabla mientras la reescribe.
- **`orders.size` es `INTEGER` y mezcla dos semánticas:** la cantidad de acciones de una compra o venta, que siempre es entera, y el monto en pesos de un `CASH_IN` o `CASH_OUT`, que no lo es. Con `INTEGER`, una transferencia no puede tener centavos ni superar los 2.147 millones de pesos.
- **`orders.datetime` es `TIMESTAMP` sin zona horaria:** guarda una hora de reloj sin decir de qué zona, así que no representa un momento del tiempo. Si las órdenes nuevas se registran en UTC y las de la seed están en hora local, el orden cronológico del libro (del que dependen el costo promedio y la reconstrucción de [ADR-005](ADR-005-materialized-accounts.md)) se rompe sin que nada falle.
- **Falta lo que las specs necesitan:** la extensión `unaccent` para la búsqueda y una columna para el motivo del rechazo ([S13](../assumptions.md)).

## Decisión

### Convenciones

- **Nombres en snake_case**, que es la convención estándar en PostgreSQL porque es lo único que sobrevive sin comillas. Solo cambian los separadores, sin renombrar conceptos: `datetime` sigue siendo `datetime`, para no perder la correspondencia con el enunciado.
- **Identificadores `BIGINT`** en todas las claves primarias y foráneas, con secuencias `BIGINT`: el equivalente a `BIGSERIAL`. Se aplica a todas las tablas y no solo a `orders`, para que cada clave foránea tenga el mismo tipo que la clave que referencia y el código use un único tipo de identificador (`long`).
- **`orders.size` como `NUMERIC(18,2)`**, para que el libro pueda registrar montos en pesos con centavos y sin el límite de 32 bits. La regla de que una cantidad de acciones es entera no se pierde: se mueve a un `CHECK` de la propia fila y al dominio.
- **Fechas y horas:** un momento del tiempo es `TIMESTAMPTZ` en la base e `Instant` en el código, y se expone en la API en ISO-8601 con UTC (`Z`). `TIMESTAMPTZ` no guarda una zona horaria: guarda un instante y convierte solo al leerlo o escribirlo como texto, así que el valor no depende del entorno. Una fecha de calendario del mercado, como la fecha de una cotización, sigue siendo `DATE` (`LocalDate`): "la rueda del 14/07/2023" no es un instante, y convertirla a uno la desplazaría de día según la zona. No se usa `TIMESTAMP` sin zona ni `LocalDateTime`, y el código no depende de la zona por defecto de la JVM ni de la sesión.
- **Valores de enumerados en inglés y en mayúsculas**, iguales en la base, en el código y en el contrato de la API. `instruments.type` se normaliza: `ACCIONES` → `STOCK`, `MONEDA` → `CURRENCY`.

### Migraciones con Flyway

El `database.sql` original no se modifica. Cada cambio sobre el modelo provisto es una migración separada, para que quede trazable qué se cambió y por qué:

| Migración | Contenido |
|---|---|
| `V1__cocos_seed` | El `database.sql` provisto, sin cambios |
| `V2__snake_case_naming` | Renombre de tablas y columnas: `marketdata` → `market_data`, `instrumentid` → `instrument_id`, `userid` → `user_id`, `accountnumber` → `account_number`, `previousclose` → `previous_close`. Son cambios de metadatos, instantáneos |
| `V3__constraints_and_indexes` | Identificadores `BIGINT`, `orders.size` como `NUMERIC(18,2)`, normalización de `instruments.type`, `NOT NULL` donde corresponde, `CHECK` sobre los enumerados, unicidad, índices, extensión `unaccent` la columna `orders.rejection_reason` y las columnas de idempotencia (`orders.idempotency_key`, `orders.request_hash`) |
| `V4__accounts_and_positions` | Tablas `accounts` y `positions`, alta de una cuenta por usuario en estado `PENDING` y traspaso de `account_number`; la proyección se persiste en la primera escritura de cada cuenta (ver [ADR-005](ADR-005-materialized-accounts.md)) |
| `V5__timestamptz` | `orders.datetime` como `TIMESTAMPTZ`, interpretando los valores existentes como hora de Buenos Aires ([S14](../assumptions.md)) |
| `V6__market_data_positive_prices` | `CHECK (close > 0 AND previous_close > 0)` en `market_data` |

### Contenido de `V3`

- **Identificadores:** `ALTER COLUMN ... TYPE BIGINT` en las claves primarias de `users`, `instruments`, `orders` y `market_data`, y en las foráneas `orders.user_id`, `orders.instrument_id` y `market_data.instrument_id`. Cambiar el tipo de la columna no alcanza: la secuencia que creó `SERIAL` también es `INTEGER` y tiene su propio máximo, así que cada una se modifica con `ALTER SEQUENCE ... AS BIGINT`. A diferencia del renombre de `V2`, este cambio reescribe las tablas; con la seed es instantáneo, y por eso se hace ahora y no con datos de producción.
- **Cantidad de las órdenes:** `ALTER COLUMN size TYPE NUMERIC(18,2)`, con dos `CHECK`: `size > 0`, y `side IN ('CASH_IN', 'CASH_OUT') OR size = trunc(size)`, que exige una cantidad entera en compras y ventas. El segundo `CHECK` se puede expresar sobre la fila porque el lado de la orden alcanza para distinguir una transferencia de una operación con acciones. Igual que el cambio de identificadores, reescribe la tabla, y por eso se hace sobre la seed.
- **Enumerados:** `CHECK` sobre `instruments.type` (`STOCK`, `CURRENCY`), `orders.side` (`BUY`, `SELL`, `CASH_IN`, `CASH_OUT`), `orders.type` (`MARKET`, `LIMIT`) y `orders.status` (`NEW`, `FILLED`, `REJECTED`, `CANCELLED`). Se eligió `CHECK` sobre `VARCHAR` en lugar de tipos `ENUM` de PostgreSQL porque agregar un valor a un `CHECK` es una migración trivial y reversible.
- **Unicidad:** `instruments.ticker` (el sistema opera un único mercado, [S4](../assumptions.md)), `users.email` sin distinguir mayúsculas (índice único sobre `lower(email)`) y `market_data(instrument_id, date)`, una cotización por instrumento y día.
- **Motivo del rechazo:** `orders.rejection_reason`, con `CHECK` sobre `INSUFFICIENT_CASH`, `INSUFFICIENT_HOLDINGS` y `NO_MARKET_PRICE`, y un `CHECK` que impide un motivo en una orden que no esté `REJECTED`. La orden `REJECTED` preexistente de la seed queda sin motivo.
- **Idempotencia:** `orders.idempotency_key` y `orders.request_hash`, con unicidad sobre `(user_id, idempotency_key)` y un `CHECK` que exige las dos columnas juntas. Las órdenes de la seed quedan sin clave (ver [ADR-008](ADR-008-idempotency.md)).
- **Índices:**
  - `market_data(instrument_id, date DESC)`, cubierto por la unicidad, para obtener el último precio de cada instrumento.
  - `orders(user_id, datetime, id)`, para reconstruir la proyección de una cuenta en orden cronológico.
- **Búsqueda:** `CREATE EXTENSION unaccent`. Con 66 instrumentos no hace falta un índice de búsqueda; si el catálogo creciera, el camino es `pg_trgm` con un índice GIN sobre una función inmutable que envuelva `unaccent`.

### Contenido de `V5`

`ALTER TABLE orders ALTER COLUMN datetime TYPE TIMESTAMPTZ USING datetime AT TIME ZONE 'America/Argentina/Buenos_Aires'`. El `USING` es obligatorio: sin él, PostgreSQL interpreta los valores existentes según la zona horaria de la sesión, que cambia de un entorno a otro, y la misma migración daría instantes distintos. Va en una migración nueva porque `V3` y `V4` ya están aplicadas. El índice `orders(user_id, datetime, id)` se reconstruye con el cambio de tipo.

### Contenido de `V6`

`ALTER TABLE market_data ADD CONSTRAINT market_data_prices_positive CHECK (close > 0 AND previous_close > 0)`. La variación diaria divide por `previous_close` y la valuación multiplica por `close`: un precio en cero o negativo no es una cotización válida, y la base lo impide en lugar de que cada cálculo lo contemple. Ninguna cotización de la seed lo viola. El dominio valida lo mismo al construir una cotización.

Todos los constraints se verificaron contra la seed: no hay tickers, emails ni cotizaciones duplicadas.

## Alternativas consideradas

- **Mantener los nombres originales.** Evita diferencias con el esquema provisto, pero deja nombres pegados (`instrumentid`) y obliga a depender de la coincidencia sin distinguir mayúsculas en el mapeo. Las migraciones separadas hacen que el cambio sea fácil de seguir.
- **Renombrar también los conceptos** (`datetime` → `created_at`, `date` → `trade_date`). Más expresivo, pero pierde la correspondencia obvia con el enunciado.
- **Corregir el `database.sql`** en lugar de migrar. Pierde la trazabilidad de qué se cambió sobre el modelo provisto.
- **`BIGINT` solo en `orders`.** Es la única tabla que hoy puede acercarse al límite, pero deja tipos distintos entre tablas y obliga a convertir entre `int` y `long` en el código. El costo de `BIGINT` en el resto es de 4 bytes por fila y por índice, irrelevante en tablas chicas.
- **Columnas de identidad** (`GENERATED ALWAYS AS IDENTITY`), la forma recomendada en PostgreSQL moderno en lugar de `SERIAL`: impiden insertar un id manual por error y la secuencia queda ligada a la columna. Convertir las columnas existentes es más invasivo que cambiar su tipo, y el beneficio no cambia ningún comportamiento del sistema; las tablas nuevas de [ADR-005](ADR-005-materialized-accounts.md) siguen la misma convención que las existentes.
- **Separar las transferencias de las órdenes** en una tabla propia con un monto `NUMERIC`. Es el modelo más limpio, pero el enunciado pide modelar las transferencias como órdenes y calcular el saldo desde `orders`.
- **Agregar una columna `amount`** para los montos en pesos y dejar `size` solo para acciones. Evita mezclar semánticas, pero deja dos columnas mutuamente excluyentes y obliga a que cada cálculo sobre el libro elija cuál leer según el lado.
- **Mantener `TIMESTAMP` y asumir que todo está en UTC.** Funciona mientras todo el código escriba en UTC, pero es una convención que la base no hace cumplir, y la seed no está en UTC.
- **Interpretar la seed como UTC.** Varias órdenes habrían ocurrido antes de la apertura del mercado ([S14](../assumptions.md)).
- **`TIMESTAMPTZ` también para la fecha de las cotizaciones.** Convierte una fecha de calendario en un instante y la desplaza de día según la zona horaria.
- **UUID como identificador.** Evita exponer identificadores secuenciales y permite generarlos fuera de la base, pero cambia el contrato de todas las entidades provistas y los índices son más grandes y menos eficientes para inserciones (salvo con UUIDv7). No hay un requisito que lo justifique.
- **Liquibase.** Más potente, pero más verboso para un esquema chico.

## Consecuencias

- El esquema final difiere del provisto; el README y este ADR lo explican.
- **La app no debe ejecutarse contra la base de Neon provista por Cocos:** Flyway aplicaría estas migraciones sobre una base que probablemente es compartida entre candidatos. La configuración por defecto apunta a la base de docker-compose.
- Los identificadores se manejan como `long` en el código y en el contrato de la API.
- En el dominio, la cantidad de una compra o venta se representa como un entero y el monto de una transferencia como un `BigDecimal`. El adaptador de persistencia convierte desde la columna `NUMERIC`.
- La hora actual se obtiene de un `Clock` inyectado, nunca con `Instant.now()` en el dominio, para que los tests puedan fijarla.
- Agregar un valor a un enumerado requiere una migración que actualice su `CHECK`.
- **Las migraciones se ejecutan al arrancar la aplicación solo por comodidad en local.** En un entorno productivo con varias instancias, Flyway corre como un paso separado del despliegue (un job o un init container), para que el arranque de cada instancia no dependa de migrar datos ni compita con otras instancias por hacerlo.
