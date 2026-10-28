# ADR-004: Convenciones del esquema y migraciones

**Estado:** Aceptado

## Contexto

Cocos provee la base con un [`database.sql`](https://github.com/cocos-capital/cocos-challenge/blob/main/database.sql) y permite modificarla si los cambios se justifican. El esquema provisto tiene varios problemas:

- **Los nombres en camelCase no existen en la base.** PostgreSQL convierte a minúsculas todo identificador sin comillas, así que `accountNumber` se guardó como `accountnumber` y `previousClose` como `previousclose`.
- **Los valores de enumerados mezclan idiomas:** `instruments.type` usa `ACCIONES` y `MONEDA`, mientras que las órdenes usan `BUY`, `FILLED` o `MARKET`.
- **No hay constraints ni índices** más allá de las claves primarias y foráneas: no hay `NOT NULL`, ni unicidad de ticker, email o número de cuenta, ni `CHECK` sobre los enumerados.
- **Los identificadores son `INTEGER`** (`SERIAL` y FKs `INT`), con un máximo de 2.147.483.647 valores. Para `orders`, un libro que crece con cada operación de cada usuario y nunca se depura, es un límite alcanzable en un broker real; y migrar el tipo de una clave primaria con la tabla ya grande es una operación costosa, que bloquea la tabla mientras la reescribe.
- **Falta lo que las specs necesitan:** la extensión `unaccent` para la búsqueda y una columna para el motivo del rechazo ([S13](../assumptions.md)).

## Decisión

### Convenciones

- **Nombres en snake_case**, que es la convención estándar en PostgreSQL porque es lo único que sobrevive sin comillas. Solo cambian los separadores, sin renombrar conceptos: `datetime` sigue siendo `datetime`, para no perder la correspondencia con el enunciado.
- **Identificadores `BIGINT`** en todas las claves primarias y foráneas, con secuencias `BIGINT`: el equivalente a `BIGSERIAL`. Se aplica a todas las tablas y no solo a `orders`, para que cada clave foránea tenga el mismo tipo que la clave que referencia y el código use un único tipo de identificador (`long`).
- **Valores de enumerados en inglés y en mayúsculas**, iguales en la base, en el código y en el contrato de la API. `instruments.type` se normaliza: `ACCIONES` → `STOCK`, `MONEDA` → `CURRENCY`.

### Migraciones con Flyway

El `database.sql` original no se modifica. Cada cambio sobre el modelo provisto es una migración separada, para que quede trazable qué se cambió y por qué:

| Migración | Contenido |
|---|---|
| `V1__cocos_seed` | El `database.sql` provisto, sin cambios |
| `V2__snake_case_naming` | Renombre de tablas y columnas: `marketdata` → `market_data`, `instrumentid` → `instrument_id`, `userid` → `user_id`, `accountnumber` → `account_number`, `previousclose` → `previous_close`. Son cambios de metadatos, instantáneos |
| `V3__constraints_and_indexes` | Identificadores `BIGINT`, normalización de `instruments.type`, `NOT NULL` donde corresponde, `CHECK` sobre los enumerados, unicidad, índices, extensión `unaccent` y la columna `orders.rejection_reason` |
| `V4__accounts_and_positions` | Tablas `accounts` y `positions`, alta de una cuenta por usuario en estado `PENDING` y traspaso de `account_number`; la proyección se persiste en la primera escritura de cada cuenta (ver [ADR-005](ADR-005-materialized-accounts.md)) |

### Contenido de `V3`

- **Identificadores:** `ALTER COLUMN ... TYPE BIGINT` en las claves primarias de `users`, `instruments`, `orders` y `market_data`, y en las foráneas `orders.user_id`, `orders.instrument_id` y `market_data.instrument_id`. Cambiar el tipo de la columna no alcanza: la secuencia que creó `SERIAL` también es `INTEGER` y tiene su propio máximo, así que cada una se modifica con `ALTER SEQUENCE ... AS BIGINT`. A diferencia del renombre de `V2`, este cambio reescribe las tablas; con la seed es instantáneo, y por eso se hace ahora y no con datos de producción.
- **Enumerados:** `CHECK` sobre `instruments.type` (`STOCK`, `CURRENCY`), `orders.side` (`BUY`, `SELL`, `CASH_IN`, `CASH_OUT`), `orders.type` (`MARKET`, `LIMIT`) y `orders.status` (`NEW`, `FILLED`, `REJECTED`, `CANCELLED`). Se eligió `CHECK` sobre `VARCHAR` en lugar de tipos `ENUM` de PostgreSQL porque agregar un valor a un `CHECK` es una migración trivial y reversible.
- **Unicidad:** `instruments.ticker` (el sistema opera un único mercado, [S4](../assumptions.md)), `users.email` sin distinguir mayúsculas (índice único sobre `lower(email)`) y `market_data(instrument_id, date)`, una cotización por instrumento y día.
- **Motivo del rechazo:** `orders.rejection_reason`, con `CHECK` sobre `INSUFFICIENT_CASH`, `INSUFFICIENT_HOLDINGS` y `NO_MARKET_PRICE`, y un `CHECK` que impide un motivo en una orden que no esté `REJECTED`. La orden `REJECTED` preexistente de la seed queda sin motivo.
- **Índices:**
  - `market_data(instrument_id, date DESC)`, cubierto por la unicidad, para obtener el último precio de cada instrumento.
  - `orders(user_id, datetime, id)`, para reconstruir la proyección de una cuenta en orden cronológico.
- **Búsqueda:** `CREATE EXTENSION unaccent`. Con 66 instrumentos no hace falta un índice de búsqueda; si el catálogo creciera, el camino es `pg_trgm` con un índice GIN sobre una función inmutable que envuelva `unaccent`.

Todos los constraints se verificaron contra la seed: no hay tickers, emails ni cotizaciones duplicadas.

## Alternativas consideradas

- **Mantener los nombres originales.** Evita diferencias con el esquema provisto, pero deja nombres pegados (`instrumentid`) y obliga a depender de la coincidencia sin distinguir mayúsculas en el mapeo. Las migraciones separadas hacen que el cambio sea fácil de seguir.
- **Renombrar también los conceptos** (`datetime` → `created_at`, `date` → `trade_date`). Más expresivo, pero pierde la correspondencia obvia con el enunciado.
- **Corregir el `database.sql`** en lugar de migrar. Pierde la trazabilidad de qué se cambió sobre el modelo provisto.
- **`BIGINT` solo en `orders`.** Es la única tabla que hoy puede acercarse al límite, pero deja tipos distintos entre tablas y obliga a convertir entre `int` y `long` en el código. El costo de `BIGINT` en el resto es de 4 bytes por fila y por índice, irrelevante en tablas chicas.
- **Columnas de identidad** (`GENERATED ALWAYS AS IDENTITY`), la forma recomendada en PostgreSQL moderno en lugar de `SERIAL`: impiden insertar un id manual por error y la secuencia queda ligada a la columna. Convertir las columnas existentes es más invasivo que cambiar su tipo, y el beneficio no cambia ningún comportamiento del sistema; las tablas nuevas de [ADR-005](ADR-005-materialized-accounts.md) siguen la misma convención que las existentes.
- **UUID como identificador.** Evita exponer identificadores secuenciales y permite generarlos fuera de la base, pero cambia el contrato de todas las entidades provistas y los índices son más grandes y menos eficientes para inserciones (salvo con UUIDv7). No hay un requisito que lo justifique.
- **Liquibase.** Más potente, pero más verboso para un esquema chico.

## Consecuencias

- El esquema final difiere del provisto; el README y este ADR lo explican.
- **La app no debe ejecutarse contra la base de Neon provista por Cocos:** Flyway aplicaría estas migraciones sobre una base que probablemente es compartida entre candidatos. La configuración por defecto apunta a la base de docker-compose.
- Los identificadores se manejan como `long` en el código y en el contrato de la API.
- Agregar un valor a un enumerado requiere una migración que actualice su `CHECK`.
- **Las migraciones se ejecutan al arrancar la aplicación solo por comodidad en local.** En un entorno productivo con varias instancias, Flyway corre como un paso separado del despliegue (un job o un init container), para que el arranque de cada instancia no dependa de migrar datos ni compita con otras instancias por hacerlo.
