# ADR-006: Concurrencia con lock pesimista sobre la cuenta

**Estado:** Aceptado

## Contexto

Validar una orden es un caso clásico de "leer y después decidir": dos pedidos simultáneos leen el mismo disponible, los dos deciden que alcanza y los dos se registran. Las specs exigen que eso no pase (ORD-28, ORD-29) y que una orden se cancele una sola vez (CXL-09).

Con la proyección de [ADR-005](ADR-005-materialized-accounts.md), la cuenta y sus posiciones forman una unidad de consistencia: en términos de DDD, un agregado con la cuenta como raíz. Todo cambio al efectivo, a las reservas o a las posiciones pasa por la cuenta.

## Decisión

**Lock pesimista sobre la fila de la cuenta**, en aislamiento `READ COMMITTED`. Toda transacción que modifica el agregado empieza con:

```sql
SET LOCAL lock_timeout = '2s';
SELECT ... FROM accounts WHERE user_id = ? FOR UPDATE;
```

### Envío de una orden

```
@Transactional
placeOrder(pedido):
  cuenta      = lock de la cuenta del usuario           -- si no existe → 404
  instrumento = instruments.find(id)                    -- 404; CURRENCY → 400
  cotización  = último precio del instrumento           -- puede no existir
  posición    = posición de la cuenta en el instrumento
  decisión    = OrderPlacementPolicy.decide(pedido, cuenta, posición, cotización)
  insert de la orden
  cuenta y posición aplican la decisión → se guardan
```

### Cancelación

```
@Transactional
cancelOrder(usuario, orderId):
  cuenta = lock de la cuenta del usuario
  orden  = orders.find(orderId, usuario)                -- si no existe o es de otro → 404
  orden.cancel()                                        -- si no está en NEW → 409
  update de la orden y liberación de la reserva
```

Con dos cancelaciones simultáneas de la misma orden, la segunda espera el lock y, cuando lo obtiene, encuentra la orden en `CANCELLED` y responde 409.

### Propiedades

- **Las lecturas ven los datos correctos.** En `READ COMMITTED` cada sentencia toma su propia foto de los datos. Las lecturas se ejecutan después de obtener el lock, y la transacción anterior que lo tenía ya hizo commit.
- **Un solo lock por transacción**, siempre sobre la raíz del agregado: no puede haber deadlocks entre estas operaciones. Las posiciones no necesitan lock propio porque toda escritura pasa antes por el de la cuenta.
- **La granularidad es la cuenta.** Cuentas distintas operan en paralelo; las operaciones de una misma cuenta esperan en fila durante unos pocos milisegundos.
- **El dominio no sabe nada de concurrencia.** Recibe la cuenta, decide y la modifica en memoria.

### Tiempo máximo de espera

`lock_timeout` limita la espera del lock a 2 segundos (configurable). Si vence, el pedido se responde con **409 y el código `ACCOUNT_BUSY`** ([ADR-007](ADR-007-error-handling.md)), y no se registra ni se modifica nada. El cliente sabe que puede reintentar. En la práctica no debería ocurrir, pero el comportamiento queda definido.

### Tests

Con Testcontainers y PostgreSQL real:

- **ORD-28 y ORD-29:** dos hilos que arrancan juntos con un `CountDownLatch`. Una orden queda `FILLED`, la otra `REJECTED` con su motivo, y la cuenta nunca queda con disponible negativo.
- **CXL-09:** dos cancelaciones simultáneas; una tiene éxito y la otra recibe 409.
- **Cuenta ocupada:** una transacción retiene el lock más allá del tiempo máximo y el pedido concurrente recibe `ACCOUNT_BUSY`.
- **Conciliación:** al final de cada test de concurrencia, la proyección es igual a la reconstruida desde el libro.

## Alternativas consideradas

- **Lock optimista** (columna `version` en `accounts` y reintento si el `UPDATE` no afecta filas). Muy eficiente con poca contención y sin locks mientras se decide, pero exige un ciclo de reintentos y vuelve menos directo el comportamiento de ORD-28. Es la **evolución** natural si la contención por cuenta llegara a importar.
- **Updates condicionales** (`UPDATE accounts SET cash = cash − :importe WHERE id = ? AND cash − reserved_cash >= :importe`). Lo más rápido, pero saca la regla del disponible del dominio y la pone en el `WHERE`, y el costo promedio también tendría que calcularse en SQL.
- **Aislamiento `SERIALIZABLE`.** PostgreSQL detecta el conflicto y aborta una transacción, pero exige reintentos y es el modelo más difícil de razonar y testear.
- **Lock advisory** (`pg_advisory_xact_lock(user_id)`). Funciona, pero el lock no está atado a ninguna fila y su significado queda implícito. Tenía sentido antes de que existiera la fila de la cuenta.

## Consecuencias

- Las operaciones de una misma cuenta se serializan. Es aceptable: un usuario no envía cientos de órdenes por segundo.
- Todo camino nuevo que escriba en el agregado (transferencias, ejecución de órdenes LIMIT por el mercado) tiene que tomar el mismo lock.
- Si una cancelación llega mientras se procesa una compra de la misma cuenta, la compra se resuelve primero, sin ver la reserva que se libera después. Es un error conservador: nunca permite gastar de más.
