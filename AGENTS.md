# AGENTS.md

Instrucciones para cualquier agente de AI (o persona) que trabaje en este repo.

## Contexto

Este repo es la resolución del [backend challenge de Cocos Capital](https://github.com/cocos-capital/cocos-challenge/blob/main/backend-challenge.md): una API para consultar el portfolio de un usuario, buscar activos y enviar y cancelar órdenes al mercado. El enunciado pide Node.js, pero Cocos pidió explícitamente resolverlo en **Java**. El proyecto tiene que poder ejecutarse localmente.

Además de funcionar, el entregable tiene que mostrar el razonamiento: las decisiones y supuestos documentados son tan importantes como el código.

## Fuentes de verdad

En orden de prioridad:

1. **Specs** en [`docs/specs/`](docs/specs/README.md): el comportamiento esperado, como requisitos y escenarios GIVEN/WHEN/THEN.
2. **ADRs aceptados** en [`docs/decisions/`](docs/decisions/README.md): las decisiones técnicas (stack, arquitectura, modelo de datos).
3. **Supuestos** en [`docs/assumptions.md`](docs/assumptions.md): las definiciones de negocio que el enunciado no resuelve.
4. **Enunciado original** y [`database.sql`](https://github.com/cocos-capital/cocos-challenge/blob/main/database.sql).

Las convenciones técnicas de cada capa (dominio, casos de uso, persistencia, web, migraciones, tests, observabilidad) están resumidas en [`.cursor/rules/`](.cursor/rules/), que se cargan según los archivos que se tocan. No agregan decisiones: condensan las de los ADRs para aplicarlas al escribir código. Si una rule y un ADR no coinciden, manda el ADR.

Si dos fuentes se contradicen, o si una tarea requiere un comportamiento que ninguna spec describe, **frená y preguntá**. No elijas vos ni completes con una suposición razonable.

## Flujo de trabajo

El desarrollo se hace por capacidad, en el orden de las olas del [mapa de capacidades](docs/specs/capability-map.md), con un ciclo de cuatro pasos:

1. **Research.** Leé la spec de la capacidad, los ADRs aceptados, los supuestos que referencia y el código existente que vas a tocar.
2. **Plan.** Proponé un plan antes de escribir código: archivos a crear o modificar, cómo se resuelve cada requisito, la lista de escenarios (por ID) que vas a cubrir con tests y, si el cambio supera el tamaño máximo de un PR (ver [Git](#git)), cómo se divide en PRs. Esperá la aprobación.
3. **Implement.** Escribí el código y los tests del plan aprobado. No agregues comportamiento que no esté en la spec.
4. **Verify.** Corré el build y todos los tests. Confirmá que cada escenario de la capacidad tiene su test y que pasa.

## Reglas

- **Cada escenario tiene su test.** El test lleva el ID del escenario en el nombre visible (`@DisplayName("ORD-17 · Compra MARKET sin pesos suficientes")`). Un escenario sin test es trabajo sin terminar.
- **Un cambio de comportamiento actualiza su spec en el mismo PR.** Si el código y la spec no coinciden, el PR está incompleto.
- **Un supuesto que cambia se actualiza primero en `docs/assumptions.md`** y después en las specs que lo referencian.
- **Credenciales fuera del repo.** La conexión a la base de datos se configura por variables de entorno. Nunca commitees URLs con usuario y contraseña, ni archivos `.env` con valores reales.
- **Dinero con precisión decimal exacta.** Nunca uses tipos de punto flotante para montos ni precios.
- **Fechas y horas con zona horaria.** Un momento del tiempo es `TIMESTAMPTZ` en la base e `Instant` en Java, y se expone en la API en ISO-8601 con UTC (`Z`). Una fecha de calendario del mercado (por ejemplo, la fecha de una cotización) es `DATE` y `LocalDate`. Nunca uses `TIMESTAMP` sin zona ni `LocalDateTime`, y nunca dependas de la zona horaria por defecto de la JVM ni de la sesión de la base. Ver [ADR-004](docs/decisions/ADR-004-schema-and-migrations.md).
- **Idioma.** Código, identificadores, nombres de tests, mensajes de commit y títulos de PR en inglés. Documentación (specs, ADRs, supuestos, README) y descripciones de PR en español. El título de un PR sigue Conventional Commits y va en inglés porque, con squash merge, se convierte en el mensaje del commit en `main`.
- **Valores de enumerados en inglés.** Todo valor de un enumerado va en inglés y en mayúsculas, igual en la base de datos, en el código y en el contrato de la API (`BUY`, `FILLED`, `MARKET`). La seed provista no cumple esta regla en `instruments.type`: esos valores se normalizan con una migración de base de datos (`ACCIONES` → `STOCK`, `MONEDA` → `CURRENCY`), sin modificar el `database.sql` original. Las specs ya usan los valores en inglés, y ningún código nuevo debe depender de los valores en español.

## Git

- Rama por unidad de trabajo, desde `main`: `docs/<tema>`, `feat/<capacidad>`, `fix/<tema>`, `chore/<tema>`.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/): `feat(order-placement): reject buys exceeding available cash`.
- PR contra `main`. La descripción indica la capacidad, los escenarios cubiertos por ID y cualquier supuesto o decisión nueva.
- **Un PR tiene que poder revisarse de una vez: como máximo unas 300 líneas cambiadas** (agregadas más eliminadas), contando código de producción, tests, SQL y documentación. No cuentan los archivos generados o copiados textualmente, como el wrapper de Gradle o la seed provista por Cocos en la migración `V1`; si un PR los incluye, la descripción los señala para que el revisor sepa qué no necesita leer.
- **Una capacidad grande se divide en varios PRs**, cada uno con un conjunto de requisitos completo: el código y los tests de los escenarios de esos requisitos. Cada PR deja el build y todos los tests en verde, y su descripción lista los escenarios que cubre y los que quedan para el siguiente.
- **Si un cambio no se puede dividir sin dejar el sistema en un estado inconsistente**, puede superar el límite, y la descripción explica por qué.
- **Los PRs se mergean con squash.** Si un PR se arma sobre otro que todavía no se mergeó, después del merge su rama se rebasa sobre `main` (`git rebase --onto main <rama-base>`) antes de cambiar la base del PR, para que no arrastre los commits individuales del PR anterior.
