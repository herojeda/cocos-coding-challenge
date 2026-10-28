# ADR-008: Idempotencia en el envío de órdenes

**Estado:** Aceptado

## Contexto

Enviar una orden no es una operación idempotente. Si el cliente envía una compra, la orden se ejecuta y la respuesta se pierde (timeout, corte de red, reinicio de un balanceador), el cliente no sabe si se procesó y reintenta. Sin un mecanismo de deduplicación, el sistema registra una segunda orden y debita el efectivo dos veces.

Ni el lock de la cuenta ([ADR-006](ADR-006-concurrency.md)) ni las validaciones lo evitan: para el sistema son dos órdenes legítimas. En trading el problema es tan conocido que forma parte del protocolo de la industria: en FIX, cada orden lleva un `ClOrdID` asignado por el cliente, que el mercado usa para detectar duplicados.

La cancelación no tiene este problema: su efecto ya es idempotente. Una segunda cancelación no cambia nada y responde con un error de conflicto (CXL-06); el cliente que reintenta puede consultar el estado de la orden para confirmar el resultado.

## Decisión

### Clave en el header `Idempotency-Key`

`POST /orders` exige el header `Idempotency-Key`, con el nombre y la semántica del borrador de estándar de la IETF (*The Idempotency-Key HTTP Header Field*). El valor lo genera el cliente: entre 1 y 64 caracteres ASCII visibles, con un UUID como formato recomendado. Un pedido sin clave o con una clave inválida responde `VALIDATION_ERROR` ([ADR-007](ADR-007-error-handling.md)). El valor de la clave nunca se repite en una respuesta de error.

La clave es **obligatoria**: si fuera opcional, el cliente que se olvida de enviarla queda expuesto justo en el escenario que la clave resuelve. La cancelación no la usa.

### La orden es el registro de idempotencia

No hay una tabla aparte. La clave se guarda en la propia orden, junto con una huella del pedido:

```sql
ALTER TABLE orders
  ADD COLUMN idempotency_key VARCHAR(64),
  ADD COLUMN request_hash    CHAR(64),        -- SHA-256 en hexadecimal
  ADD CONSTRAINT orders_idempotency_key_unique UNIQUE (user_id, idempotency_key),
  ADD CONSTRAINT orders_idempotency_complete
    CHECK ((idempotency_key IS NULL) = (request_hash IS NULL));
```

- **La unicidad es por usuario**: dos usuarios pueden usar la misma clave sin interferir.
- **Las claves no vencen.** Con UUIDs no hay riesgo práctico de colisión, y como la clave vive en la orden, no hay registros que depurar.
- **Las órdenes de la seed quedan sin clave.** PostgreSQL considera distintos los valores `NULL` en una restricción de unicidad, así que no entran en conflicto.
- **La huella** es un SHA-256 de los campos del pedido que definen la orden: instrumento, lado, tipo, cantidad o monto (indicando cuál de los dos), y precio. Los decimales se normalizan antes de calcularla, para que `850`, `850.0` y `850.00` produzcan la misma huella.

Las columnas se agregan en la migración `V3` ([ADR-004](ADR-004-schema-and-migrations.md)).

### Flujo

Dentro de la transacción de envío, inmediatamente después de tomar el lock de la cuenta:

```
@Transactional
placeOrder(pedido, idempotencyKey):
  cuenta = lock de la cuenta del usuario
  previa = orders.findByUserAndKey(usuario, idempotencyKey)
  si existe previa:
    si previa.requestHash == hash(pedido) → devolver previa (replay)
    si no                                 → IDEMPOTENCY_KEY_REUSED
  ... flujo normal de ADR-006, guardando la clave y la huella en la orden
```

| Caso | Respuesta |
|---|---|
| Clave nueva | Se procesa normalmente |
| Clave usada, mismo pedido | La orden registrada, con el status de la respuesta original y el header `Idempotent-Replayed: true` |
| Clave usada, pedido distinto | 422 `IDEMPOTENCY_KEY_REUSED`, sin registrar nada |
| Dos pedidos simultáneos con la misma clave | El lock de la cuenta los serializa: el segundo espera, encuentra la orden del primero y la devuelve como replay |

### Propiedades

- **La deduplicación ocurre bajo el lock de la cuenta**, así que dos pedidos con la misma clave no pueden pasar la búsqueda a la vez. La restricción de unicidad en la base es la red de seguridad: si por un error de programación se violara, la transacción falla y no se registra un duplicado.
- **Solo las órdenes registradas consumen la clave.** Una orden `REJECTED` se registra, así que su reintento devuelve el mismo rechazo aunque mientras tanto haya cambiado el disponible: la respuesta corresponde a lo que ocurrió la primera vez. Un error de validación, un recurso inexistente o `ACCOUNT_BUSY` no registran nada, así que el reintento se procesa normalmente; es exactamente el caso para el que existe `ACCOUNT_BUSY`.
- **El replay devuelve la orden en su estado actual**, no una copia de la respuesta original. Si una orden LIMIT se canceló después, el reintento la muestra `CANCELLED`. Es una consecuencia de usar la orden como registro, y le da al cliente información más útil que una respuesta congelada.
- **El status del replay es el de la respuesta original**: toda orden registrada respondió `201 Created`, así que el replay también responde `201`.

## Alternativas consideradas

- **Clave opcional.** Más cómoda para quien prueba la API a mano, pero deja sin protección al cliente que no la envía, que es justamente el que más la necesita.
- **Tabla de idempotencia separada** que guarda la respuesta serializada (el enfoque de Stripe). Es genérica y sirve para cualquier endpoint, pero duplica datos que ya están en la orden, necesita una política de vencimiento y depuración, y obliga a mantener sincronizadas la respuesta guardada y la orden.
- **Identificador de orden del cliente en el body** (equivalente al `ClOrdID` de FIX). Es un concepto de dominio y no de transporte, pero mezcla en el contrato de la orden un dato cuyo único fin es deduplicar. El header separa esa responsabilidad y sigue un estándar en curso.
- **Deduplicar por contenido** (rechazar un pedido igual a uno reciente). No distingue un reintento de dos compras iguales intencionales, que en trading son habituales.
- **Vencimiento de las claves** (Stripe las descarta a las 24 horas). Tiene sentido cuando las claves viven en una tabla que crece aparte; acá viven en la orden y no hay nada que depurar.
- **Clave también en la cancelación.** Haría que un reintento reciba la respuesta original en lugar de un 409, pero el efecto de la cancelación ya es idempotente y el estado se puede consultar.

## Consecuencias

- Ningún reintento de un envío puede duplicar una orden, incluso con pedidos simultáneos.
- Cada ejemplo de invocación de `POST /orders` (Swagger, archivo `.http`, tests) tiene que incluir el header.
- El catálogo de errores suma `IDEMPOTENCY_KEY_REUSED` ([ADR-007](ADR-007-error-handling.md)).
- El header `Idempotent-Replayed` tiene que exponerse en la configuración de CORS si algún día la API se consume desde un navegador.
