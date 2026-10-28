# ADR-007: Errores con Problem Details

**Estado:** Aceptado

## Contexto

La API tiene que comunicar errores de forma que el cliente pueda reaccionar programáticamente, sin interpretar texto. Además, una respuesta de error es un lugar donde es fácil exponer información de más: valores ingresados por el usuario (riesgo de XSS reflejado si un cliente los inserta en una página sin escapar) o detalles internos (mensajes de excepciones, SQL, stack traces).

Por [S8](../assumptions.md), una orden `REJECTED` **no es un error**: es un resultado normal del procesamiento y se responde con la orden, su estado y su motivo ([S13](../assumptions.md)). Lo que sigue aplica solo a los pedidos que no llegan a procesarse.

## Decisión

### Formato: Problem Details (RFC 9457)

Las respuestas de error usan `application/problem+json`, con soporte nativo de Spring (`ProblemDetail`). Un `@RestControllerAdvice` en `adapter/in/web` extiende `ResponseEntityExceptionHandler`, que ya convierte en Problem Details las excepciones propias de Spring MVC, y agrega el mapeo de las excepciones del dominio. El dominio lanza excepciones que no saben nada de HTTP; la traducción a status y formato vive solo en el adaptador web.

```json
{
  "type": "urn:cocos-challenge:problem:order-not-cancellable",
  "title": "Order cannot be cancelled",
  "status": 409,
  "code": "ORDER_NOT_CANCELLABLE",
  "detail": "Only orders in status NEW can be cancelled",
  "instance": "urn:uuid:5b1f0c9e-2d7a-4f43-9c1e-8f0a2b6d4e11",
  "orderId": 42,
  "currentStatus": "FILLED"
}
```

- **`status` es numérico**, como exige el RFC, y coincide con el status HTTP. La redundancia con el header es intencional: el body sigue siendo interpretable aunque un intermediario reescriba el status o un log guarde solo el body.
- **`type`** identifica la clase de problema con una URN. No es una URL que se pueda abrir, porque no hay un sitio de documentación publicado; el catálogo vive en este ADR.
- **`code`** es un campo propio, que el RFC permite, con un valor de un catálogo cerrado. Va uno a uno con `type`: `type` es lo que entiende cualquier herramienta de Problem Details y `code` es lo cómodo para que el cliente haga un `switch`. Los códigos nombran el problema que ve el cliente, no la causa técnica interna.

### Catálogo de códigos

| `code` | Status | Cuándo |
|---|---|---|
| `MALFORMED_REQUEST` | 400 | El body no se puede leer (JSON inválido, tipos incompatibles) |
| `VALIDATION_ERROR` | 400 | El pedido no cumple las reglas de la spec; incluye la lista `errors` con el campo y el motivo de cada falla |
| `UNSUPPORTED_REQUEST` | 404, 405 o 415 | Ruta inexistente, método o media type no soportado |
| `USER_NOT_FOUND` | 404 | El usuario no existe |
| `INSTRUMENT_NOT_FOUND` | 404 | El instrumento no existe |
| `ORDER_NOT_FOUND` | 404 | La orden no existe o pertenece a otro usuario ([S11](../assumptions.md)) |
| `ORDER_NOT_CANCELLABLE` | 409 | La orden no está en estado `NEW` |
| `ACCOUNT_BUSY` | 409 | Venció el tiempo máximo de espera del lock de la cuenta ([ADR-006](ADR-006-concurrency.md)); se puede reintentar |
| `INTERNAL_ERROR` | 500 | Cualquier error no previsto |

El catálogo es parte del contrato de la API: un código nuevo requiere actualizar este ADR y la spec correspondiente.

### Seguridad

- **`detail` siempre con texto controlado por nosotros.** Nunca se usa el mensaje de una excepción de un framework, porque algunos mensajes por defecto de Spring repiten el valor recibido (por ejemplo, el valor que no pudo convertirse a número). Los errores de validación informan el campo y el motivo, nunca el valor rechazado.
- **`instance` identifica la ocurrencia, no la URL.** Es un `urn:uuid:` que también se escribe en el log junto con el error, para correlacionar lo que reporta un cliente con lo que pasó en el servidor. El RFC define `instance` justamente como un identificador de esa ocurrencia puntual.
- **El handler de errores no previstos** responde `INTERNAL_ERROR` con un texto genérico y registra la excepción completa solo en el log.
- **Header `X-Content-Type-Options: nosniff`** en todas las respuestas, agregado por un filtro, para que ningún navegador interprete una respuesta como algo distinto de su `Content-Type`. Spring Security lo agregaría solo, pero el proyecto no lo usa porque no hay autenticación.

## Alternativas consideradas

- **Formato de error propio** (`{ "error": "...", "code": 123 }`). Reinventa un estándar que Spring ya soporta y que entienden los clientes y las herramientas.
- **`status` como string semántico** (`"status": "INVALID_ORDER_STATUS"`). Deja de cumplir el RFC: los clientes y librerías de Problem Details, incluido el `RestClient` de Spring, esperan un número. La necesidad de un identificador semántico se cubre con `type` y `code`.
- **Solo `type`, sin `code`.** Cumple el estándar sin redundancia, pero hacer un `switch` sobre URNs es incómodo para el cliente.
- **`instance` con la ruta del pedido**, que es el valor por defecto de Spring. Refleja input del usuario y no ayuda a correlacionar con el log.

## Consecuencias

- El cliente puede reaccionar a cualquier error por `code` y a cualquier rechazo de orden por su motivo, sin interpretar texto.
- Los errores no exponen input del usuario ni detalles internos.
- Los tests de cada escenario de error verifican el status y el `code`.
