# ADR-005: Cuentas y posiciones materializadas

**Estado:** Aceptado

## Contexto

El efectivo, las tenencias y las reservas se derivan del libro de órdenes, que crece sin límite. Calcularlos con un agregado sobre `orders` en cada pedido tiene tres problemas:

- **El costo crece con el historial de cada usuario.** Con un índice adecuado es tolerable para un inversor típico, pero no para usuarios muy activos.
- **El agregado corre dentro de la sección crítica** del envío de órdenes, mientras se tiene el lock ([ADR-006](ADR-006-concurrency.md)), y alarga la serialización.
- **El portfolio necesita además recorrer todas las ejecuciones** para el costo promedio, y eso no lo resuelve ningún agregado.

El enunciado tiene dos frases que conviven: pide calcular la tenencia y los pesos con los movimientos de `orders`, y también pide que al ejecutarse una orden "se actualice el listado de posiciones del usuario".

## Decisión

### El libro es la fuente de verdad; las posiciones son una proyección

El libro de órdenes sigue siendo la fuente de verdad. El efectivo, las reservas y las posiciones se **materializan** en una proyección que se actualiza en la **misma transacción** que cada orden, y que se puede reconstruir desde el libro en cualquier momento. Es la lectura que concilia las dos frases del enunciado, y es la que ya describe la spec `account-balance`: el saldo "DEBE coincidir siempre" con el libro.

### Modelo

```sql
accounts (
  id              BIGSERIAL PRIMARY KEY,
  account_number  VARCHAR(20) NOT NULL UNIQUE,     -- se mueve desde users
  user_id         BIGINT NOT NULL UNIQUE REFERENCES users(id),
  cash            NUMERIC(18,2) NOT NULL,
  reserved_cash   NUMERIC(18,2) NOT NULL CHECK (reserved_cash >= 0),
  projection_status VARCHAR(10) NOT NULL CHECK (projection_status IN ('READY', 'PENDING'))
)

positions (
  account_id         BIGINT NOT NULL REFERENCES accounts(id),
  instrument_id      BIGINT NOT NULL REFERENCES instruments(id),
  quantity           INT NOT NULL,
  reserved_quantity  INT NOT NULL CHECK (reserved_quantity >= 0),
  average_cost       NUMERIC(18,6),                -- NULL si la tenencia no es positiva
  realized_pnl       NUMERIC(18,2) NOT NULL,
  PRIMARY KEY (account_id, instrument_id)
)
```

- **La cuenta es una entidad**, relacionada 1 a 1 con el usuario ([S4](../assumptions.md)). El número de cuenta pasa de `users` a `accounts`.
- **Las órdenes siguen referenciando `user_id`.** Con la relación 1 a 1, cambiar la tabla central de la seed no aporta nada hoy. Si un usuario pudiera tener varias cuentas, `orders` pasaría a referenciar `account_id`.
- **El efectivo vive en la fila de la cuenta**, no como una posición del instrumento ARS. El libro sigue registrando los pesos como instrumento, como pide el enunciado, pero en la proyección la fila de la cuenta es el lugar natural para el lock y para las invariantes de pesos.
- **El costo promedio y el resultado realizado se materializan.** El costo promedio ponderado ([S2](../assumptions.md)) se actualiza de forma incremental con la fila actual: una compra recalcula `(cantidad × promedio + q × p) / (cantidad + q)`, una venta suma `(p − promedio) × q` al resultado realizado sin cambiar el promedio, y una tenencia que llega a cero reinicia el promedio. El promedio se guarda con seis decimales para no acumular error de redondeo entre operaciones.

### Transiciones

| Evento | Cuenta | Posición |
|---|---|---|
| BUY MARKET `FILLED` | `cash −= q × p` | `quantity += q`, recalcula el promedio |
| SELL MARKET `FILLED` | `cash += q × p` | `quantity −= q`, `realized_pnl += (p − promedio) × q` |
| BUY LIMIT `NEW` | `reserved_cash += q × p` | — |
| SELL LIMIT `NEW` | — | `reserved_quantity += q` |
| Cancelación de BUY LIMIT | `reserved_cash −= q × p` | — |
| Cancelación de SELL LIMIT | — | `reserved_quantity −= q` |
| `REJECTED` | — | — |
| `CASH_IN` / `CASH_OUT` | `cash ± monto` | — |

Las transiciones se implementan en el dominio (`Account` y `Position`), no en SQL: el adaptador de persistencia solo guarda el estado resultante.

### Invariantes

- **En la base:** `reserved_cash >= 0` y `reserved_quantity >= 0`, que la seed cumple.
- **En el dominio:** el disponible nunca queda negativo (`cash − reserved_cash >= 0` y `quantity − reserved_quantity >= 0`). La política de órdenes rechaza todo lo que lo violaría.

No se usa un `CHECK` completo en la base porque la seed trae una tenencia de −10 BMA ([S3](../assumptions.md)): el `CHECK` no se podría validar sobre los datos existentes, y una compra legítima de 5 BMA, que deja la posición en −5, sería rechazada por la base. Corregir la seed para poder poner el `CHECK` contradice S3.

### Construcción y conciliación

Una única **función de reconstrucción** recalcula la proyección completa de una cuenta desde el libro, recorriendo sus órdenes en orden cronológico con las mismas transiciones del dominio.

#### Cuándo hace falta reconstruir

En operación normal nunca: cada orden actualiza la proyección en la misma transacción. La reconstrucción solo aparece en cuatro casos puntuales, y siempre por cuenta:

1. **Carga inicial**, al pasar del modelo con solo el libro al modelo con proyección.
2. **Recuperación de un desvío** que detectó la conciliación.
3. **Cambio en la lógica de la proyección**, por ejemplo un método de costo distinto o una columna nueva.
4. **Corrección manual del libro**, si un ajuste no pasó por el camino de escritura normal.

#### Cómo se reconstruye

- **Los cuatro casos solo marcan la cuenta como `PENDING`.** La migración `V4` crea una cuenta por usuario en ese estado.
- **La cuenta se reconstruye en la primera escritura.** Cuando una operación que escribe (envío o cancelación de una orden) toma el lock de la cuenta ([ADR-006](ADR-006-concurrency.md)) y la encuentra `PENDING`, la reconstruye dentro de la misma transacción, la marca `READY` y continúa. Ninguna escritura se aplica sobre una cuenta a medio armar y no hay condición de carrera, porque la reconstrucción ocurre bajo el mismo lock. La primera escritura de esa cuenta tarda lo que lleve recorrer su historial; las demás no se enteran.
- **Las consultas nunca toman el lock ni escriben.** La consulta del portfolio corre en una transacción de solo lectura con aislamiento `REPEATABLE READ`, para que la cuenta y sus posiciones salgan de la misma foto de los datos (en `READ COMMITTED`, una orden que se confirma entre las dos lecturas daría un total que no cierra). Si la cuenta está `READY`, lee la proyección. Si está `PENDING`, calcula la proyección en memoria con la misma función de reconstrucción, desde el libro, sin guardarla. El resultado es el mismo que la reconstrucción va a persistir, y la consulta conserva su semántica: no tiene efectos, no compite con las escrituras por el lock y podría atenderse desde una réplica de lectura.
- **Nada ocurre en el arranque de la aplicación.** Atar la reconstrucción al arranque retrasaría el readiness de cada instancia en un entorno con autoscaling, haría que varias instancias compitan por las mismas cuentas y mezclaría atender pedidos con migrar datos.
- **Si la reconstrucción falla**, la transacción se revierte, el pedido responde `INTERNAL_ERROR` ([ADR-007](ADR-007-error-handling.md)) y la cuenta sigue `PENDING`, así que se reintenta en la próxima escritura. El error queda en el log con el identificador de la ocurrencia.

#### Conciliación

- **Test de conciliación.** Aplica secuencias de órdenes y verifica que la proyección incremental sea igual a la reconstruida desde cero. Se ejecuta también al final de los tests de concurrencia.
- **Chequeo bajo demanda**, que compara la proyección de una cuenta con su reconstrucción. En un broker real, la conciliación diaria es práctica estándar.

#### Evolución documentada

- **Job de reconstrucción en segundo plano.** Procesa las cuentas `PENDING` de forma proactiva, para que el costo no lo pague el primer pedido de cada cuenta. En un entorno con varias instancias, cada una toma cuentas con `SELECT ... FOR UPDATE SKIP LOCKED`, sin coordinación adicional. Con los cuatro usuarios de la seed no aporta nada medible, por eso no se implementa.
- **Estado de error por cuenta.** Si una reconstrucción falla de forma persistente, la cuenta pasaría a un estado `FAILED` y respondería un error específico hasta que alguien intervenga, en lugar de reintentar en cada acceso.

## Alternativas consideradas

- **Agregado sobre `orders` en cada pedido** (SQL agrega, Java aplica las reglas). Más simple y sin riesgo de desvío, pero no escala con el historial y alarga la sección crítica.
- **Proyección asincrónica** (eventos, outbox). Consistencia eventual: entre la orden y la actualización, el usuario podría gastar dos veces. No sirve para validar un disponible.
- **Vista materializada de PostgreSQL.** `REFRESH` recalcula todo y no se actualiza con cada escritura.
- **Snapshots periódicos más el delta posterior.** Reduce el costo de lectura pero mantiene el agregado, con más complejidad.
- **Construir la proyección en una migración Java de Flyway** con el calculador del dominio. Acopla una migración, que debe ser inmutable, a código que va a evolucionar.
- **Reconstruir las cuentas pendientes al arrancar la aplicación**, antes de aceptar pedidos. Bloquea el readiness, hace competir a las instancias entre sí y solo resuelve la carga inicial, no los otros tres casos.
- **Reconstruir y persistir también desde las consultas**, tomando el lock. Haría que una lectura tenga efectos: rompe la semántica de una consulta HTTP segura, hace competir las lecturas con las escrituras por el lock e impide atender consultas desde una réplica de lectura.
- **Bloquear las cuentas pendientes hasta que un job las procese.** Unifica los cuatro casos, pero agrega un estado visible para el usuario (la cuenta no puede operar) y una latencia que depende de la frecuencia del job. La reconstrucción en la primera escritura, junto con el cálculo en memoria para las consultas, logra lo mismo sin bloquear.
- **Efectivo como posición del instrumento ARS.** Más uniforme, pero separa las invariantes de pesos de la fila que se bloquea.

## Consecuencias

- El envío de órdenes y el portfolio dejan de depender del tamaño del historial: el portfolio lee las posiciones de la cuenta y los últimos precios.
- **Riesgo nuevo: desvío entre la proyección y el libro.** Se mitiga con la función única de reconstrucción, el test de conciliación y el chequeo bajo demanda. Una cuenta con desvío se marca `PENDING` y se reconstruye en su próxima escritura.
- La cancelación ya no es un `UPDATE` aislado: libera reservas en la cuenta y por eso también toma el lock ([ADR-006](ADR-006-concurrency.md)).
- El costo promedio ponderado es incremental; **FIFO no**. Si Cocos respondiera que el método es FIFO, haría falta una tabla de lotes por posición.
- Cualquier camino que escriba en el libro (por ejemplo, transferencias o la ejecución futura de órdenes LIMIT por el mercado) tiene que actualizar la proyección en la misma transacción. Una orden LIMIT ejecutada liberaría su reserva al precio límite y debitaría el importe al precio de ejecución.
