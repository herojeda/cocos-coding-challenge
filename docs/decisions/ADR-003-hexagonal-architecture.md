# ADR-003: Arquitectura hexagonal en un solo módulo

**Estado:** Aceptado

## Contexto

Las reglas de negocio del challenge (reservas, rechazos con motivo, cálculo por monto, costo promedio) son lo que más interesa testear de forma aislada, y varias dependen de supuestos que estamos validando con Cocos. A la vez, son cuatro casos de uso: la estructura no debe convertirse en ceremonia.

## Decisión

Arquitectura hexagonal en **un solo módulo de Gradle**, separada por paquetes:

```
com.cocoscap.challenge
  domain/                 sin Spring ni JDBC: Order y su máquina de estados, Account,
                          Position, OrderPlacementPolicy, CostBasisCalculator, dinero
  application/            casos de uso (PlaceOrder, CancelOrder, GetPortfolio,
                          SearchAssets) y los puertos que necesitan
  adapter/in/web/         controllers, DTOs del contrato, Problem Details
  adapter/out/persistence/ implementación de los puertos con JdbcClient
  adapter/out/observability/ implementación del puerto de métricas con Micrometer (ADR-009)
  config/                 wiring de Spring
```

Las reglas de dependencia se verifican con **ArchUnit**, como parte de la suite de tests:

- `domain` no depende de ningún otro paquete del proyecto ni de Spring, JDBC o Jackson.
- `application` depende solo de `domain`. La única dependencia de Spring permitida es `@Transactional` (ver abajo).
- Los adaptadores no dependen entre sí.
- Los subpaquetes de `domain` no tienen ciclos entre sí.

### Subpaquetes por concepto

Dentro de cada capa, las clases se agrupan en un subpaquete por concepto del dominio, con los mismos nombres en todas las capas:

```
domain/
  account/                 Account (raíz del agregado), Position, AccountId,
                           AccountSnapshot, LedgerEntry, excepciones de la cuenta
  costbasis/               CostBasisCalculator, CostBasisState,
                           WeightedAverageCostBasisCalculator
  order/                   Order, OrderId, enumerados, OrderPlacementPolicy,
                           motivo de rechazo, excepciones de la orden
  instrument/              Instrument, InstrumentId, InstrumentType
application/<concepto>/    casos de uso y sus puertos (instrument/SearchInstruments)
adapter/in/web/<concepto>/ controllers y DTOs (error/ queda para Problem Details)
adapter/out/persistence/<concepto>/ adaptadores con JdbcClient y sus filas
```

- **Un agregado es un paquete.** La raíz, sus entidades internas, sus value objects y sus excepciones viven juntos. Las entidades internas exponen sus mutadores como package-private, así que solo la raíz puede modificarlas: en Java, el paquete es la única frontera de visibilidad entre clases, y tiene que coincidir con la frontera del agregado. Por ejemplo, una `Position` solo cambia a través de su `Account`.
- **Un concepto con API propia es otro paquete.** El costo promedio no depende de la cuenta, tiene una interfaz pública y lo usan la cuenta y el portfolio, así que vive en `costbasis`.
- **No se agrupa por tipo de clase** (`entity/`, `valueobject/`, `service/`). Separaría un agregado de sus entidades internas y obligaría a hacer públicos sus mutadores, y dispersaría un mismo concepto en varias carpetas. El rol de cada clase se lee en su nombre y su forma: `*Id` y los `record` son value objects, `*Policy` y `*Calculator` son servicios de dominio, y `*Exception` son excepciones.
- **Un tipo que comparten varios conceptos** va en un paquete con el nombre de lo que es, como `application/pagination/`. Nunca en `common`, `shared` ni `util`.
- **Cuando un paquete supera unas diez clases,** se busca un subconcepto con responsabilidad propia para separarlo. Nunca se parte por tipo de clase.

### Excepción explícita: `@Transactional` en `application`

La transacción y el lock pertenecen al caso de uso: enviar una orden es una unidad atómica de negocio. Se permite `org.springframework.transaction.annotation.Transactional` en `application` como **única** excepción, declarada en el test de ArchUnit para que quede visible que es una decisión y no un descuido. El dominio queda completamente libre de Spring.

### Puntos de variación aislados

Los supuestos pendientes de confirmación quedan cada uno en una sola pieza, para que una respuesta distinta de Cocos sea cambiar una pieza y no reescribir el flujo:

- La reserva del disponible ([S1](../assumptions.md)) en `Account`.
- El método de costo ([S2](../assumptions.md)) detrás de la interfaz `CostBasisCalculator`, con la implementación de costo promedio ponderado.

## Alternativas consideradas

- **Multi-módulo de Gradle** (un módulo `domain` que físicamente no puede depender de Spring). Es la versión más estricta, pero para cuatro casos de uso es ceremonia; ArchUnit da la misma garantía con menos estructura.
- **Feature primero** (`instrument/{domain,application,adapter}`). Cada carpeta tendría un flujo completo, pero los casos de uso cruzan agregados: enviar una orden usa la cuenta, la orden, el instrumento y la cotización. Las features no quedan independientes, y las reglas de dependencia entre capas serían más difíciles de expresar. Las capas primero, con subpaquetes por concepto, dan el mismo orden por responsabilidad.
- **Por capas clásico** (controller, service, repository). Más simple, pero mezcla las reglas de negocio con el acceso a datos y obliga a testear contra la base lo que debería ser un test unitario.
- **Puerto `UnitOfWork`** en lugar de `@Transactional`: una interfaz en `application` implementada con `TransactionTemplate` en un adaptador. Deja `application` libre de Spring, a costa de más código para un beneficio que hoy es teórico. Es la **evolución** natural si la capa de aplicación tuviera que ejecutarse fuera de Spring o con otro mecanismo transaccional.

## Consecuencias

- Los escenarios que son reglas de negocio se prueban con tests unitarios puros del dominio. Con Testcontainers quedan los que involucran la base: queries, concurrencia y el flujo de punta a punta.
- El test de ArchUnit falla si alguien (persona o agente) rompe las reglas de dependencia, lo que convierte la arquitectura en algo verificable y no solo declarado.
