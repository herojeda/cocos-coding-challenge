# ADR-010: Convenciones del contrato de la API

**Estado:** Aceptado

## Contexto

Los errores ([ADR-007](ADR-007-error-handling.md)), la idempotencia ([ADR-008](ADR-008-idempotency.md)) y el formato de las fechas ([ADR-004](ADR-004-schema-and-migrations.md)) ya están definidos, pero falta el resto del contrato: cómo se nombran los recursos y los campos del JSON, y cómo se pagina una lista. La búsqueda de activos es el primer endpoint que lo necesita: devuelve una lista de largo variable y tiene campos de varias palabras. Si cada endpoint lo resuelve por su cuenta, el contrato queda inconsistente.

## Decisión

### Recursos

- Las rutas usan los sustantivos del lenguaje de las specs, en inglés y en plural: `/instruments`, `/orders`.
- Lo que pertenece a un usuario es un subrecurso suyo, porque el usuario se identifica explícitamente en cada pedido ([S11](../assumptions.md)): `GET /users/{userId}/portfolio`. Un usuario inexistente responde `USER_NOT_FOUND`.
- Los filtros de una lista son parámetros de la query sobre el recurso, no rutas aparte, y cada uno nombra el campo por el que filtra: `GET /instruments?ticker=<texto>&name=<texto>`. Los filtros presentes se combinan con Y, y sin filtros la respuesta es la colección completa. Un filtro presente pero vacío responde `VALIDATION_ERROR`.

### Orden

- El orden de una lista se elige con el parámetro `sort`, con un catálogo cerrado de valores por recurso, en inglés y en mayúsculas como todo enumerado del contrato (`sort=TICKER`). Cada recurso define su valor por defecto. Un valor fuera del catálogo responde `VALIDATION_ERROR`.
- El orden es siempre ascendente. Cada valor de `sort` termina con una clave única como último criterio, para que el orden sea total (ver la paginación).
- En la query, cada valor de `sort` se traduce a un fragmento `ORDER BY` fijo escrito en el adaptador. El texto recibido nunca llega al SQL.
- Evolución: orden descendente (`sort=-NAME`) o varios criterios combinados, si un cliente lo necesita.

### Nombres de los campos

- Los campos del JSON, tanto en los pedidos como en las respuestas, van en **snake_case** (`has_next`, `average_cost`, `rejection_reason`). Es el mismo nombre que la columna correspondiente ([ADR-004](ADR-004-schema-and-migrations.md)): un concepto tiene un solo nombre de punta a punta, y un campo del contrato se encuentra en la base sin traducir.
- Se configura una vez, con la estrategia `SNAKE_CASE` de Jackson para toda la aplicación. Los records del adaptador web usan nombres de Java en camelCase, sin anotaciones por campo.
- Los parámetros de la query siguen la misma convención.

### Listas

Una lista se responde como un objeto, nunca como un array en la raíz. Así se pueden agregar campos (como la paginación) sin romper el contrato:

```json
{
  "instruments": [
    { "id": 47, "ticker": "PAMP", "name": "Pampa Holding S.A.", "type": "STOCK" }
  ],
  "page": { "number": 0, "size": 20, "has_next": false }
}
```

### Paginación por offset

- Parámetros `page` (desde 0) y `size` (por defecto 20, entre 1 y 100). Un valor fuera de rango responde `VALIDATION_ERROR`.
- La query usa `LIMIT size + 1 OFFSET page × size`. Si vuelve la fila adicional, `has_next` es `true`, y esa fila no se devuelve. No hay un `COUNT` aparte.
- La respuesta no informa el total. Con un cursor, que es la evolución prevista (ver abajo), el total no se puede calcular sin recorrer el resultado completo. Si no se ofrece desde el principio, ningún cliente pasa a depender de él.
- El orden de la query tiene que ser total, para que una página no repita ni saltee filas entre pedidos. Si el criterio de la spec deja empates, se agrega una clave única como último criterio de orden (en la búsqueda, el ticker es único).
- Una página posterior a la última no es un error: responde la lista vacía con `has_next: false`.

## Alternativas consideradas

- **camelCase en el JSON.** Es el default de Jackson y no necesita configuración, pero cada campo de varias palabras tendría un nombre en el contrato y otro en la base.
- **Array en la raíz.** Es más simple, pero agregar la paginación o cualquier otro dato después cambia el tipo de la respuesta y rompe a los clientes.
- **Paginación con total (`total_elements`, `total_pages`).** Cuesta un `COUNT` por pedido y deja de ser viable al pasar a un cursor.
- **Cursor desde el principio (keyset).** Es estable aunque el catálogo cambie entre pedidos y no se degrada con el offset, pero con 66 instrumentos ninguna de las dos cosas pesa todavía. El costo se paga cuando hace falta.

## Consecuencias

- Los endpoints nuevos siguen estas convenciones sin decidirlas de nuevo.
- Con offset, si el catálogo cambia entre el pedido de una página y el de la siguiente, una fila puede repetirse o perderse. Con un catálogo que casi no cambia, se acepta.
- **Evolución: cursor (keyset).** Si el volumen crece, `page` se reemplaza por un cursor opaco (`next_cursor`) que codifica la clave de orden de la última fila. La query pasa a `WHERE (clave de orden) > (cursor)` con un índice que la cubra. `size` y `has_next` se mantienen.
