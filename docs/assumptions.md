# Supuestos y decisiones de negocio

Este archivo reúne las definiciones que el enunciado del challenge no resuelve y que tomamos para poder avanzar. Cada supuesto tiene un ID que las specs referencian, así que cuando una definición cambia se sabe exactamente qué escenarios revisar.

Hay dos tipos de estado:

- **Pendiente de confirmación:** se consultó a Cocos por mail. Mientras no haya respuesta, el sistema se comporta según el supuesto.
- **Decisión de diseño:** no se consultó porque se deriva del enunciado o es una elección razonable de producto. Se documenta para que sea explícita.

Cuando Cocos responda, se actualiza el estado acá y, si la respuesta cambia el comportamiento, las specs afectadas en el mismo PR.

| ID | Tema | Estado |
|---|---|---|
| S1 | Reserva del disponible en órdenes LIMIT | Pendiente de confirmación |
| S2 | Cálculo del rendimiento | Pendiente de confirmación |
| S3 | Tenencia negativa de BMA en la seed | Pendiente de confirmación |
| S4 | Usuarios, cuentas y unicidad | Pendiente de confirmación |
| S5 | Instrumentos sin cotización | Pendiente de confirmación |
| S6 | Órdenes por monto en pesos | Pendiente de confirmación |
| S7 | Órdenes LIMIT que cruzan el precio de mercado | Decisión de diseño |
| S8 | Error de validación vs. rechazo de negocio | Decisión de diseño |
| S9 | Último precio y datos de mercado | Decisión de diseño |
| S10 | Criterio de búsqueda de activos | Decisión de diseño |
| S11 | Identificación del usuario sin autenticación | Decisión de diseño |
| S12 | Transferencias de pesos | Decisión de diseño |
| S13 | Motivo del rechazo de una orden | Decisión de diseño |

---

## S1 — Reserva del disponible en órdenes LIMIT

**Estado:** pendiente de confirmación.

**Supuesto:** una orden BUY LIMIT en estado `NEW` reserva `size × precio límite` de los pesos disponibles, y una SELL LIMIT en `NEW` reserva las acciones correspondientes. Al cancelarse, la reserva se libera. Las órdenes `FILLED`, `REJECTED` y `CANCELLED` no reservan nada.

**Fundamento:** el enunciado exige rechazar una orden por un monto mayor al disponible. Si las órdenes pendientes no reservaran, un usuario podría publicar varias órdenes límite respaldadas por los mismos pesos, y alguna no podría cubrirse al ejecutarse. La reserva se calcula al precio límite porque en una compra es el peor caso: el mercado nunca la ejecuta por encima de ese precio.

**Consecuencia sobre la seed:** el usuario 1 tiene 753.000 ARS de efectivo y 627.500 ARS disponibles, por las órdenes BUY LIMIT pendientes de LOMA (35.500) y BMA (90.000).

**Afecta a:** `account-balance`, `portfolio`, `order-placement`, `order-cancellation`.

## S2 — Cálculo del rendimiento

**Estado:** pendiente de confirmación.

**Supuesto:** el rendimiento total se calcula por posición (instrumento), no por orden, sobre el **costo promedio ponderado** (PPC): `último precio / PPC − 1`. El PPC se calcula recorriendo las órdenes ejecutadas en orden cronológico:

- Una compra recalcula el promedio ponderando la tenencia previa y la nueva.
- Una venta reduce la cantidad, pero no modifica el promedio.
- Si la tenencia vuelve a cero, el promedio se reinicia y la próxima compra arranca de cero.

Como datos complementarios se exponen el **resultado realizado** (`(precio de venta − PPC) × cantidad vendida`, acumulado por instrumento) y la **variación diaria** (`close / previousClose − 1`).

**Fundamento:** con ventas parciales, el rendimiento por orden pierde sentido porque no hay forma objetiva de saber qué acciones de qué compra se vendieron. El costo promedio ponderado es el criterio que muestran las apps de inversión retail. FIFO es la alternativa estándar y da resultados distintos: comprar 10 a 100 y 10 a 200 y vender 10 a 150 deja un costo de 150 con PPC y de 200 con FIFO.

**Afecta a:** `portfolio`.

## S3 — Tenencia negativa de BMA en la seed

**Estado:** pendiente de confirmación.

**Supuesto:** la seed registra una venta `FILLED` de 30 BMA cuando el usuario 1 tenía 20, lo que deja la tenencia en −10. No se corrigen los datos: el libro de órdenes es la fuente de verdad y el sistema lo refleja tal cual. La posición se informa con cantidad −10 y valor de mercado negativo, que resta del valor total. El costo promedio y el rendimiento de esa posición quedan sin calcular. La validación de venta impide que el sistema genere este estado con órdenes nuevas.

**Fundamento:** en un sistema financiero, corregir u ocultar una inconsistencia del libro es peor que exponerla. Excluir la posición haría que el valor total no cierre con el libro de órdenes.

**Afecta a:** `account-balance`, `portfolio`, `order-placement`.

## S4 — Usuarios, cuentas y unicidad

**Estado:** pendiente de confirmación.

**Supuesto:** la relación entre usuario y cuenta es 1 a 1, como en el modelo actual: la cuenta no se modela como una entidad aparte. Se agrega unicidad sobre `accountNumber` y sobre el email (sin distinguir mayúsculas). Como el sistema opera sobre un único mercado, también se agrega unicidad sobre el ticker de los instrumentos.

**Fundamento:** en un broker real, quien tiene los activos es la cuenta comitente, que puede tener varios titulares, y un usuario puede tener varias cuentas. Modelarlo requiere cambiar la referencia de las órdenes, lo que excede el alcance. El ticker tampoco es un identificador global en el mercado real (el mismo ticker existe en distintos mercados y plazos); en un escenario multi-mercado el identificador sería el ISIN o el par ticker-mercado.

**Afecta a:** el modelo de datos (ver ADRs). No cambia el comportamiento descripto en las specs.

## S5 — Instrumentos sin cotización

**Estado:** pendiente de confirmación.

**Supuesto:** PGR e IRCP no tienen registros en `marketdata`. Una orden MARKET sobre un instrumento sin cotización se registra como `REJECTED`, porque no hay precio contra el cual ejecutarla. Las órdenes LIMIT se aceptan, porque el precio lo informa el usuario. Si un usuario tuviera tenencia en uno de estos instrumentos, el portfolio informa la cantidad, pero el valor de mercado y el rendimiento quedan sin calcular y no suman al valor total.

**Afecta a:** `portfolio`, `order-placement`.

## S6 — Órdenes por monto en pesos

**Estado:** pendiente de confirmación.

**Supuesto:** la cantidad se calcula como el entero inferior de `monto / precio`, usando el último precio en órdenes MARKET y el precio límite en órdenes LIMIT. La orden se registra con la cantidad calculada. Solo se compromete `cantidad × precio`: en MARKET es lo que se debita y en LIMIT lo que se reserva. El excedente no se reserva y sigue disponible. Si el monto no alcanza para una acción, el pedido se responde con un error de validación y la orden no se registra.

**Fundamento:** el enunciado no admite fracciones de acciones. Una orden por 0 acciones no llega a ser una orden enviable al mercado, así que no corresponde registrarla como rechazada.

**Afecta a:** `order-placement`.

## S7 — Órdenes LIMIT que cruzan el precio de mercado

**Estado:** decisión de diseño.

**Decisión:** toda orden LIMIT válida y con disponible suficiente queda en `NEW`, aunque su precio límite cruce el último precio (por ejemplo, una compra límite por encima del último precio). El sistema no la ejecuta.

**Fundamento:** en un mercado real esa orden se ejecutaría en el momento, pero el enunciado indica que las LIMIT quedan en `NEW` y que no hace falta simular el mercado. La ejecución de órdenes pendientes queda fuera del alcance.

**Afecta a:** `order-placement`.

## S8 — Error de validación vs. rechazo de negocio

**Estado:** decisión de diseño.

**Decisión:** se distinguen dos tipos de fallo al enviar una orden:

- **Error de validación:** el pedido está mal formado o no se puede interpretar (falta el precio en una LIMIT, se informan cantidad y monto a la vez, la cantidad no es positiva, el instrumento no existe, el monto no alcanza para una acción). El pedido se responde con un error y **no se registra ninguna orden**.
- **Rechazo de negocio:** el pedido es válido, pero el mercado no lo acepta (disponible insuficiente, instrumento sin cotización en una MARKET). La orden **se registra** con estado `REJECTED` y la respuesta informa ese estado como un resultado normal del procesamiento.

**Fundamento:** el enunciado pide guardar las órdenes rechazadas por falta de disponible. Un pedido inválido, en cambio, nunca llegó a ser una orden.

**Afecta a:** `order-placement`.

## S9 — Último precio y datos de mercado

**Estado:** decisión de diseño.

**Decisión:** las cotizaciones son diarias (de cierre), sin datos intradiarios. El último precio de un instrumento es el `close` de su cotización con fecha más reciente, no la cotización "de hoy" (los datos son de julio de 2023). Una cotización con `open`, `high` y `low` vacíos significa que el instrumento no operó ese día y su `close` es el último precio arrastrado; como solo se usan `close` y `previousClose`, esos valores vacíos no afectan ningún cálculo.

**Afecta a:** `portfolio`, `order-placement`.

## S10 — Criterio de búsqueda de activos

**Estado:** decisión de diseño.

**Decisión:** la búsqueda encuentra instrumentos cuyo ticker o nombre contengan el texto buscado, sin distinguir mayúsculas ni tildes. Los pesos (instrumento de tipo `CURRENCY`) no aparecen en los resultados, porque no son un activo sobre el que se pueda operar. Los resultados se ordenan por relevancia: primero el ticker que coincide exactamente, después los tickers que empiezan con el texto y después el resto, cada grupo por ticker en orden alfabético.

**Afecta a:** `asset-search`.

## S11 — Identificación del usuario sin autenticación

**Estado:** decisión de diseño.

**Decisión:** como el enunciado no pide autenticación, cada pedido identifica explícitamente al usuario. Aun así, las reglas de pertenencia se respetan: un usuario no puede cancelar una orden de otro, y esa orden se trata como inexistente para no revelar que existe.

**Afecta a:** `portfolio`, `order-placement`, `order-cancellation`.

## S12 — Transferencias de pesos

**Estado:** decisión de diseño.

**Decisión:** las transferencias (`CASH_IN` y `CASH_OUT`) se modelan como órdenes y se tienen en cuenta en todos los cálculos, pero no se pueden originar desde la API: el enunciado no las pide. Si se implementaran, un `CASH_OUT` debería validarse contra el disponible, igual que una compra.

**Limitación conocida:** la cantidad de una orden es un número entero, así que no se pueden registrar transferencias con centavos.

**Afecta a:** `account-balance`, `order-placement`.

## S13 — Motivo del rechazo de una orden

**Estado:** decisión de diseño.

**Decisión:** toda orden que el sistema rechaza registra y devuelve el motivo, con un valor de un catálogo cerrado:

| Motivo | Cuándo |
|---|---|
| `INSUFFICIENT_CASH` | Una compra supera los pesos disponibles |
| `INSUFFICIENT_HOLDINGS` | Una venta supera las acciones disponibles para vender |
| `NO_MARKET_PRICE` | Una orden MARKET sobre un instrumento sin cotización |

Si una orden MARKET no tiene cotización, el motivo es `NO_MARKET_PRICE` aunque además falte disponible, porque sin precio no se puede calcular el importe a validar. Las órdenes en otro estado no tienen motivo. Las órdenes `REJECTED` que ya existen en la seed no tienen motivo registrado y se informan sin él.

**Fundamento:** el estado `REJECTED` dice que la orden no se aceptó, pero no por qué. Con un motivo semántico, el cliente puede reaccionar sin interpretar texto (por ejemplo, ofrecer ingresar pesos ante `INSUFFICIENT_CASH`), lo que mejora la experiencia del usuario. Los valores son parte del contrato de la API, así que el catálogo es cerrado y se amplía solo con un cambio de spec.

**Afecta a:** `order-placement`, y el modelo de datos: el libro de órdenes necesita guardar el motivo (ver ADRs).
