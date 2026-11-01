# ADR-009: Observabilidad con Micrometer y OpenTelemetry

**Estado:** Aceptado

## Contexto

La API tiene que poder operarse: saber cuánto tarda cada endpoint, cuántas órdenes se rechazan y por qué, y seguir un pedido de punta a punta. Además, varias decisiones de diseño se apoyan en supuestos que solo se pueden confirmar midiendo: que la contención por cuenta es baja ([ADR-006](ADR-006-concurrency.md)) y que la proyección no se desvía del libro ([ADR-005](ADR-005-materialized-accounts.md)).

La instrumentación no debe atar el código a un proveedor de telemetría (Grafana, Datadog, New Relic, Elastic), ni romper las reglas de dependencia de la arquitectura hexagonal ([ADR-003](ADR-003-hexagonal-architecture.md)).

## Decisión

### Dos abstracciones complementarias

- **Micrometer (Observation API)** es la abstracción en el código. La aplicación instrumenta contra una fachada neutral, y una sola observación produce una métrica y un span.
- **OpenTelemetry (OTLP)** es la abstracción en la red: el protocolo estándar para enviar métricas y trazas a cualquier backend sin cambiar el código.

Se usa **`spring-boot-starter-opentelemetry`**, el starter de Spring Boot 4 que combina las dos: la Observation API de Micrometer, el puente de Micrometer Tracing hacia OpenTelemetry, el registro de métricas OTLP y la configuración automática del SDK.

### Qué se instrumenta

**Automático, sin código:**

- Métricas HTTP del servidor (`http.server.requests`): latencia, throughput y errores por endpoint y status.
- Pool de conexiones (HikariCP) y JVM.
- Trazas de cada pedido HTTP, con propagación W3C Trace Context.
- **Un span por cada operación JDBC**, con la librería [`datasource-micrometer`](https://github.com/jdbc-observations/datasource-micrometer), que la documentación de Spring Boot recomienda para observar JDBC. Permite ver en la traza cuánto tarda cada query, incluida la espera del `SELECT ... FOR UPDATE` sobre la cuenta.

**Métricas de negocio:**

| Métrica | Tipo | Tags | Para qué |
|---|---|---|---|
| `trading.orders` | Contador | `type`, `side`, `status`, `rejection_reason` | Tasa de rechazo y sus causas |
| `trading.account.lock.wait` | Timer | — | Valida el supuesto de baja contención de ADR-006; es la señal para evaluar el lock optimista |
| `trading.account.busy` | Contador | `operation` | Pedidos que vencieron el `lock_timeout` (`ACCOUNT_BUSY`) |
| `trading.idempotency.replays` | Contador | `result` (`replayed`, `key_reused`) | Volumen de reintentos de los clientes |
| `trading.projection.rebuilds` | Timer | `outcome` (`success`, `failure`) | Reconstrucciones de cuentas `PENDING`; una en operación normal indica un desvío |

**Ningún tag lleva el usuario, la cuenta, el instrumento ni la clave de idempotencia.** Son valores de alta cardinalidad, que multiplican las series del sistema de métricas, y en el caso del usuario exponen datos de clientes. Esos datos, cuando hacen falta, van en los logs y en los atributos de los spans.

### Métricas de negocio detrás de un puerto

Los casos de uso informan los hechos de negocio a través de un puerto de salida, **`TradingMetricsPort`**, definido en `application` (por ejemplo, `orderProcessed(type, side, status, rejectionReason)`). Su implementación con Micrometer vive en un adaptador nuevo, `adapter/out/observability`. El caso de uso no sabe que existe Micrometer, las reglas de ArchUnit de ADR-003 no cambian, y en los tests el puerto se reemplaza por un doble que registra las llamadas.

La espera del lock se mide en el adaptador de persistencia, que es el que ejecuta el `SELECT ... FOR UPDATE`.

### Logs

- **JSON estructurado por consola**, con el soporte nativo de Spring Boot (`logging.structured.format.console`). La app escribe a stdout y el entorno de ejecución los recolecta.
- Cada línea lleva el trace id y el span id, que Spring Boot agrega cuando Micrometer Tracing está presente. El identificador `instance` de Problem Details ([ADR-007](ADR-007-error-handling.md)) se registra junto con ellos, así que un error reportado por un cliente lleva a la traza completa.
- Los logs no se exportan por OTLP: requiere un appender de Logback que hoy está en versión alfa.

### Ejecución local

- **La exportación OTLP está apagada por defecto**, para que la app arranque sin ningún colector y el exportador no llene el log de errores de conexión.
- **Perfil `observability`:** docker-compose tiene un perfil opcional que levanta `grafana/otel-lgtm`, un solo contenedor con colector de OpenTelemetry, Prometheus, Tempo, Loki y Grafana. La app, con el perfil de Spring `observability`, exporta métricas y trazas a ese contenedor.
- El muestreo de trazas es del 100% en ese perfil; en producción se ajustaría por configuración.

## Alternativas consideradas

- **Agente de Java de OpenTelemetry.** Instrumenta sin código, pero depende de que su versión coincida con la de cada librería instrumentada, complica las imágenes nativas y el cache AOT, y puede chocar con otros agentes. El equipo de Spring lo considera la opción más frágil.
- **Starter de OpenTelemetry mantenido por el proyecto OpenTelemetry.** Sus propios autores recomiendan el agente y lo plantean como alternativa cuando no se puede usar; depende de librerías en versión alfa.
- **Micrometer con un registro de un proveedor específico** (por ejemplo, Datadog o Prometheus directo). Funciona, pero ata la exportación a ese proveedor. OTLP permite cambiar de backend por configuración.
- **`@Observed` en los casos de uso**, como segunda excepción a las reglas de ADR-003. Menos código, pero una anotación solo mide tiempos y conteos de invocación: el motivo de un rechazo o el estado de la orden igual requerirían código de Micrometer dentro del caso de uso.
- **Sin spans de JDBC.** Evita una dependencia externa, pero deja las trazas sin el detalle de la base de datos, que es donde está el costo de cada pedido y donde ocurre la espera del lock.
- **Exportar los logs por OTLP.** Unifica las tres señales en el mismo protocolo, pero hoy depende de un appender alfa.

## Consecuencias

- Cambiar de backend de telemetría es un cambio de configuración, no de código.
- Las specs no cambian: la observabilidad no es comportamiento visible para el usuario.
- La base (starter, `datasource-micrometer`, logs JSON y perfil de docker-compose) entra en un PR propio, después del esqueleto de la arquitectura. Cada métrica de negocio entra con la capacidad que la produce.
- `datasource-micrometer` es una dependencia de un tercero; su versión tiene que acompañar la de Spring Boot.
- Los tests de integración no exportan nada: el registro de métricas se puede inspeccionar en memoria cuando un test necesita verificar una métrica.
