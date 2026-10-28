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

Si dos fuentes se contradicen, o si una tarea requiere un comportamiento que ninguna spec describe, **frená y preguntá**. No elijas vos ni completes con una suposición razonable.

## Flujo de trabajo

El desarrollo se hace por capacidad, en el orden de las olas del [mapa de capacidades](docs/specs/capability-map.md), con un ciclo de cuatro pasos:

1. **Research.** Leé la spec de la capacidad, los ADRs aceptados, los supuestos que referencia y el código existente que vas a tocar.
2. **Plan.** Proponé un plan antes de escribir código: archivos a crear o modificar, cómo se resuelve cada requisito y la lista de escenarios (por ID) que vas a cubrir con tests. Esperá la aprobación.
3. **Implement.** Escribí el código y los tests del plan aprobado. No agregues comportamiento que no esté en la spec.
4. **Verify.** Corré el build y todos los tests. Confirmá que cada escenario de la capacidad tiene su test y que pasa.

## Reglas

- **Cada escenario tiene su test.** El test lleva el ID del escenario en el nombre visible (`@DisplayName("ORD-17 · Compra MARKET sin pesos suficientes")`). Un escenario sin test es trabajo sin terminar.
- **Un cambio de comportamiento actualiza su spec en el mismo PR.** Si el código y la spec no coinciden, el PR está incompleto.
- **Un supuesto que cambia se actualiza primero en `docs/assumptions.md`** y después en las specs que lo referencian.
- **Credenciales fuera del repo.** La conexión a la base de datos se configura por variables de entorno. Nunca commitees URLs con usuario y contraseña, ni archivos `.env` con valores reales.
- **Dinero con precisión decimal exacta.** Nunca uses tipos de punto flotante para montos ni precios.
- **Idioma.** Código, identificadores, nombres de tests y mensajes de commit en inglés. Documentación (specs, ADRs, supuestos, README) en español.

## Git

- Rama por unidad de trabajo, desde `main`: `docs/<tema>`, `feat/<capacidad>`, `fix/<tema>`, `chore/<tema>`.
- Commits con [Conventional Commits](https://www.conventionalcommits.org/): `feat(order-placement): reject buys exceeding available cash`.
- PR contra `main`. La descripción indica la capacidad, los escenarios cubiertos por ID y cualquier supuesto o decisión nueva.
