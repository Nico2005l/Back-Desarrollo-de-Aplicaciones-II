# Planta Libre — Plan de pruebas

**Versión 1 · 3.er checkpoint (20/11/2026)**
Estructura basada en ISO/IEC/IEEE 29119-3, según los Criterios de Testing de la cátedra. Complementa al [documento técnico](DocumentoTecnico.md) y a la [arquitectura de integración](Integracion.md).

> Pendiente para la versión final: cerrar los huecos de la matriz de trazabilidad (sección 8), las pruebas de punta a punta, la prueba de carga y el escaneo ZAP (sección 9).

---

## 1. Alcance

**Dentro del alcance:** la API REST (backend) y su integración con PostgreSQL, RabbitMQ y los sistemas externos (pasarela de pagos simulada, Gmail API, Pwned Passwords), más la aplicación web (frontend) en los flujos de las historias de usuario comprometidas en el diseño (06/11).

| Área | Qué se prueba |
|---|---|
| Identidad y acceso | Registro, confirmación por mail, login, segundo factor (TOTP) para administradores, renovación y cierre de sesión, recuperación de contraseña, bloqueo por intentos, JWT inválidos |
| Catálogo y disponibilidad | Empresas, edificios, pisos, plano, espacios, franjas libres, reglas de reserva |
| Reservas | Alta por el cliente y por el administrador, franja tomada con alternativas, estados (patrón State, 409 ante transición inválida), cancelación con política de reintegro |
| Cobro asincrónico | Publicación, consumo idempotente, reintentos, DLQ, barrido, broker caído |
| Tópico de eventos | Outbox transaccional, relay con confirmación del broker, broker caído, los dos eventos de reservas (`reserva.confirmada` y `reserva.cancelada`), suscriptores de la confirmación y de la auditoría, deduplicación por suscriptor, DLQ por cola |
| Medios de pago | Alta, edición, principal, baja; solo se guardan los últimos 4 dígitos |
| Bloqueos | Bloqueo de espacios con reservas afectadas |
| Resiliencia y API | Timeout de 5 s con fallback, circuit breaker, Idempotency-Key, ETag/If-Match, Problem Details |
| Seguridad | Control de acceso por rol (BFLA), acceso a recursos ajenos (BOLA/IDOR), rate limiting (429), cabeceras, cifrado de campos |

**Fuera del alcance:**

- **Funcionalidad de IA:** no se incorpora al sistema. El equipo justificó que cada propuesta se resuelve con una solución convencional ([PropuestasIA.md](PropuestasIA.md)). Sí se declara el uso de IA en el proceso de testing (eje E, sección 10).
- **Integración SOAP:** no aplica. Todos los sistemas externos exponen REST ([Integracion.md](Integracion.md), sección 2).
- **Pasarela de pagos real:** se prueba contra el simulador con las tarjetas de prueba de los entornos sandbox.

---

## 2. Niveles de prueba

| Nivel | Herramienta | Dónde | Qué cubre |
|---|---|---|---|
| Unitario | JUnit 5, AssertJ, Mockito | `src/test/java/.../domain`, `.../service`, `.../security`, `.../controller` | Reglas del dominio (estados, disponibilidad, tarifas, política de cancelación), servicios con dependencias simuladas, resiliencia de la pasarela |
| Integración | Spring Boot Test + Testcontainers (PostgreSQL 16 y RabbitMQ 3.13 reales) | `src/test/java/.../integracion` | Cada componente a través de la API (MockMvc) contra la base real migrada por Flyway: repositorios, transacciones, bloqueos, idempotencia, ETag |
| Mutación | PIT | `mvn verify` | Calidad de las pruebas del paquete de dominio (umbral 60 %) |
| API | Postman + Newman | `api-tests/PlantaLibre.postman_collection.json`, job `api` del CI | Cada endpoint con un caso de éxito y uno de error, contra la API levantada con PostgreSQL |
| Contrato | Schemathesis | job `api` del CI | Respuestas validadas contra el OpenAPI publicado (`/v3/api-docs`): sin errores 5xx, tipo de contenido y esquema |
| Análisis estático | SpotBugs + FindSecBugs | `mvn verify` | Bugs y vulnerabilidades de prioridad alta cortan el build |
| De punta a punta | *Pendiente (Playwright)* | — | 5 escenarios críticos en el pipeline (sección 9) |
| No funcional | *Pendiente (k6, OWASP ZAP, gitleaks, Lighthouse)* | — | Carga, seguridad dinámica, secretos, accesibilidad (sección 9) |

---

## 3. Entornos y datos de prueba

| Entorno | Cómo se levanta | Uso |
|---|---|---|
| Local completo | `docker compose up --build` (README) | Demo en vivo, pruebas manuales y exploratorias. Base, broker con TLS, API y front |
| Pruebas automatizadas | `mvn verify` (necesita Docker para Testcontainers) | Unitarias, integración, mutación, análisis estático |
| CI | GitHub Actions, `.github/workflows/ci.yml` | Todo lo anterior más Newman y Schemathesis, en cada push y pull request |

**Datos de prueba:**

- **Pruebas de integración:** generan sus propios datos con `Escenario`. Cada tipo de prueba usa su propio espacio y su propia ventana de fechas, así no chocan entre sí. Los usuarios tienen emails aleatorios.
- **Colección de Postman:** parte de `api-tests/preparar-datos.sh`, que crea el entorno con valores aleatorios en cada corrida.
- **Secretos:** se generan al levantar el sistema (servicio `init` del compose) o por corrida en el CI. Ninguno está en el repositorio.
- **Tarjetas de la pasarela simulada:**

| Tarjeta | Resultado |
|---|---|
| `4242 4242 4242 4242` (o cualquier otra) | Aprobada |
| `4000 0000 0000 0002` | Rechazada por el emisor |
| `4000 0000 0000 0119` | Falla técnica de la pasarela |
| `4000 0000 0000 0077` | La pasarela no responde (30 s): corta el timeout de 5 s |

---

## 4. Criterios de entrada y de salida

**Entrada:** el código compila; `docker compose up` levanta el sistema; las migraciones de Flyway corren sobre una base vacía; las HU a probar tienen criterios de aceptación en Jira.

**Salida (por entrega):**

- Pipeline de CI en verde sobre el tag.
- Cobertura de la capa de negocio (`domain` y `service`): 80 % o más de líneas y 70 % o más de ramas. JaCoCo corta el build por debajo de eso.
- Mutation score del dominio: 60 % o más. PIT corta el build por debajo de eso.
- 0 hallazgos de prioridad alta en SpotBugs/FindSecBugs.
- Quality Gate de SonarCloud aprobado (sección 9).
- 0 defectos Bloqueantes o Críticos abiertos; como máximo 3 Mayores, con workaround documentado.
- 100 % de las HU del alcance ejecutadas y 95 % o más de los criterios pasados.
- Suite estable: 3 corridas seguidas con el mismo resultado.

---

## 5. Riesgos y cómo se cubren

Probabilidad (P) e impacto (I) de 1 a 3; la prioridad es P × I.

| # | Riesgo | P | I | P×I | Cómo se cubre |
|---|---|---|---|---|---|
| R1 | Dos personas reservan la misma franja (doble reserva) | 3 | 3 | 9 | Bloqueo pesimista del espacio y restricción de no solapamiento en la base. `ReservasClienteIntegracionTest`, `ReservaRepositoryIntegracionTest`, caso "Reservar una franja tomada" (Newman) |
| R2 | Cobro duplicado (mensaje repetido o doble clic) | 2 | 3 | 6 | Consumidor idempotente (`mensajes_procesados`, `UNIQUE` en `cobros.reserva_id`), Idempotency-Key en el alta. `CobroServiceImplTest`, `MensajeriaIntegracionTest`, `IdempotenciaIntegracionTest` |
| R3 | La pasarela no responde y deja hilos o reservas colgadas | 2 | 3 | 6 | Timeout de 5 s, circuit breaker, DLQ y barrido. `PasarelaPagosResilienteTest`, `PasarelaLentaIntegracionTest`, `BarridoCobrosTest` |
| R4 | Se pierde un mensaje con el broker caído | 2 | 3 | 6 | Cobro: pedido registrado en la transacción de la reserva y barrido. Tópico: outbox transaccional y relay con confirmación del broker. `BarridoCobrosTest`, `MensajeriaIntegracionTest.conElBrokerCaidoLosEventosEsperanYSalenEnOrdenCuandoVuelve`; prueba manual apagando el broker (sección 7) |
| R5 | Un usuario ve o modifica datos de otro (BOLA/IDOR) | 2 | 3 | 6 | El usuario sale siempre del token. `MediosPagoIntegracionTest.laTarjetaDeOtroNoSePuedeTocar`, `ReservasClienteIntegracionTest.laReservaDeOtroClienteNoSeVe`, `EtagIntegracionTest.laTarjetaDeOtroNoDaSuEtag` |
| R6 | Un cliente usa un endpoint de administrador (BFLA) | 2 | 3 | 6 | `@PreAuthorize` por permiso. `SeguridadTest`, casos "como cliente" de Newman (403) |
| R7 | Transición de estado inválida de una reserva | 2 | 2 | 4 | Patrón State. `EstadoReservaTest` (40 casos parametrizados), `TransicionesReservaIntegracionTest` (409) |
| R8 | Dos administradores editan la misma reserva y uno pisa al otro | 2 | 2 | 4 | ETag/If-Match con la fila bloqueada. `EtagIntegracionTest` |
| R9 | Fuerza bruta sobre el login | 2 | 2 | 4 | Límite de intentos y rate limit (429). `LimitadorDeIntentosTest`, `LimiteTasaIntegracionTest`, `AutenticacionIntegracionTest` |
| R10 | La respuesta no respeta el contrato publicado | 2 | 2 | 4 | `ContratoApiIntegracionTest`, Schemathesis en el CI |
| R11 | Reintegro incorrecto al cancelar | 1 | 3 | 3 | `PoliticaCancelacionTest`, `CancelacionesTest` |
| R12 | Falla de un servicio de mail o de Pwned Passwords bloquea el flujo | 1 | 2 | 2 | Timeout de 5 s y fallback. `GmailApiEmailServiceTest` |
| R13 | Se avisa una confirmación que no ocurrió, o un suscriptor la procesa dos veces | 1 | 2 | 2 | El evento se escribe antes del commit, en la misma transacción; deduplicación por suscriptor. `MensajeriaIntegracionTest`: `elEventoSoloQuedaEnElOutboxSiLaTransaccionConfirma`, `unEventoRepublicadoConElMismoIdSeProcesaUnaSolaVez` |

---

## 6. Inventario de pruebas automatizadas

255 pruebas de JUnit (unitarias e integración) y 99 pedidos de Postman. Datos de la corrida del 19/11/2026.

| Clase | Nivel | Pruebas |
|---|---|---|
| `domain.enums.EstadoReservaTest` | Unitario | 40 |
| `domain.disponibilidad.MotorDisponibilidadTest` | Unitario | 11 |
| `domain.disponibilidad.ReglasReservaTest` | Unitario | 8 |
| `domain.cobro.PoliticaCancelacionTest` | Unitario | 5 |
| `domain.cobro.TarifarioTest` | Unitario | 3 |
| `service.impl.CobroServiceImplTest` | Unitario | 9 |
| `service.impl.CancelacionesTest` | Unitario | 6 |
| `service.impl.PasarelaPagosResilienteTest` | Unitario | 6 |
| `service.impl.PasarelaPagosSimuladaTest` | Unitario | 5 |
| `service.impl.BarridoCobrosTest` | Unitario | 4 |
| `service.impl.GmailApiEmailServiceTest` | Unitario | 2 |
| `service.impl.ControlAccesoSimuladoTest` | Unitario | 2 |
| `mensajeria.MonitorDlqTest` | Unitario | 4 |
| `service.PrecondicionTest` | Unitario | 3 |
| `controller.EtagsTest` | Unitario | 12 |
| `security.SeguridadTest` | Unitario | 9 |
| `security.LimitadorDeIntentosTest` | Unitario | 6 |
| `security.SesionesTest` | Unitario | 3 |
| `integracion.TransicionesReservaIntegracionTest` | Integración | 15 |
| `integracion.AdminReservasIntegracionTest` | Integración | 14 |
| `integracion.CatalogoIntegracionTest` | Integración | 11 |
| `integracion.ContratoApiIntegracionTest` | Integración | 10 |
| `integracion.ReservasClienteIntegracionTest` | Integración | 9 |
| `integracion.AutenticacionIntegracionTest` | Integración | 10 |
| `integracion.BloqueosIntegracionTest` | Integración | 7 |
| `integracion.MediosPagoIntegracionTest` | Integración | 6 |
| `integracion.EtagIntegracionTest` | Integración | 6 |
| `integracion.IdempotenciaIntegracionTest` | Integración | 6 |
| `integracion.ReservaRepositoryIntegracionTest` | Integración | 5 |
| `integracion.MensajeriaIntegracionTest` (RabbitMQ real) | Integración | 10 |
| `integracion.PasarelaLentaIntegracionTest` | Integración | 1 |
| `integracion.LimiteTasaIntegracionTest` | Integración | 4 |
| `integracion.CabecerasSeguridadIntegracionTest` (HTTP real, puerto aleatorio) | Integración | 3 |

**Métricas actuales** (`mvn verify` del 19/11/2026):

| Métrica | Valor | Umbral |
|---|---|---|
| Pruebas JUnit | 255 ejecutadas, 255 pasadas | 100 % |
| Cobertura de líneas, capa de negocio | 94,6 % | 80 % |
| Cobertura de ramas, capa de negocio | 78,9 % | 70 % |
| Mutation score (dominio) | 74 % (115 de 155 mutantes) | 60 % |
| SpotBugs/FindSecBugs, prioridad alta | 0 | 0 |

---

## 7. Pruebas manuales de resiliencia (demo)

| Escenario | Pasos | Resultado esperado |
|---|---|---|
| Broker apagado: cobro | 1. `docker compose stop rabbitmq`. 2. Reservar desde la web. 3. `docker compose start rabbitmq`. 4. Esperar hasta 3 minutos | La reserva se crea *Pendiente*. Al volver el broker, el barrido pide el cobro y pasa a *Confirmada*. Ningún cobro perdido |
| Broker apagado: tópico | 1. `docker compose stop rabbitmq`. 2. Como administrador, confirmar a mano una reserva pendiente. 3. Ver el evento pendiente en `eventos_salientes`. 4. `docker compose start rabbitmq` | La API responde normalmente. A los pocos segundos de volver el broker, el relay publica el evento y aparecen la habilitación del control de acceso (log) y `RESERVA_CONFIRMADA` en la auditoría |
| Pasarela sin respuesta en el cobro | Reservar con `4000 0000 0000 0077` | En el log, 3 cortes a los 5 s; el mensaje queda en `planta-libre.cobros.dlq` (consola de RabbitMQ) |
| Pasarela sin respuesta en el reintegro | Levantar con `PASARELA_DEMORA_REINTEGRO_MS=8000` y cancelar una reserva confirmada | 503 a los 5 s con `Retry-After`; la reserva sigue *Confirmada* |
| Mensaje inválido | Publicar a mano en la cola un mensaje sin firma desde la consola | Va directo a la DLQ y queda registrado en la auditoría |
| Doble clic al reservar | Reenviar el mismo POST con la misma `Idempotency-Key` (Postman: "Reservar otra vez con la misma clave") | Misma respuesta, `Idempotent-Replayed: true`, una sola reserva |
| Edición concurrente | Dos pestañas de administrador editan la misma reserva; guardar en una y después en la otra | La segunda recibe "Alguien modificó estos datos…" (412) |

---

## 8. Matriz de trazabilidad

La matriz está en [MatrizTrazabilidad.md](MatrizTrazabilidad.md). Cruza cada criterio de aceptación de las historias del tablero del equipo (GitHub Projects) con su caso, la prueba automatizada, el resultado, la evidencia del CI y el defecto que la motivó.

| Resultado | Criterios |
|---|---|
| Pasa | 104 de 127 (81,9 %) |
| Parcial, Sin prueba o Manual | 21 (9 son de la interfaz, para Playwright) |
| No implementado | 2 (B-9 paginado de usuarios; B-20 evento de reserva cancelada) |

Los casos negativos y de borde son el 56,7 %. La matriz lista además los huecos a cubrir antes de la entrega final (sección 7) y las diferencias entre el texto de las historias y lo implementado (sección 8).

---

## 9. Pendiente para la entrega final

| Ítem | Criterio de la cátedra | Estado |
|---|---|---|
| 5 escenarios de punta a punta (Playwright) en el pipeline | Eje A | Pendiente |
| Prueba de carga: 50 usuarios, 5 min, p95 menor a 500 ms y errores debajo del 1 % en 3 endpoints críticos (k6) | Eje C | Pendiente |
| OWASP ZAP baseline y activo autenticado, 0 alertas High | Eje D | Pendiente |
| Escaneo de secretos en todo el historial (gitleaks) | G6 | Pendiente |
| Dependencias sin CVE críticas o altas (OWASP Dependency-Check o Trivy) | Eje D | Pendiente |
| Quality Gate de SonarCloud: el job existe, pero corre solo con las variables `SONAR_*` configuradas en el repositorio | Eje B | Pendiente de configurar |
| Accesibilidad con Lighthouse/axe 90 o más; responsive a 360, 768 y 1280 px | Eje A | Pendiente |
| 40 % o más de casos negativos o de borde en la especificación de casos | Entregable 3 | Cumple: 56,7 % en la matriz |
| Pruebas para los criterios de la API que siguen sin prueba o parciales (mails de cancelación, búsqueda por nombre, motivo, orden del listado) | Matriz, sección 7 | Pendiente |
| Suite 3 veces seguidas sin diferencias (flaky) | Eje B | Pendiente de medir en el CI |

---

## 10. Uso de IA en el testing (declaración preliminar)

| Herramienta | Para qué | Qué se validó a mano |
|---|---|---|
| Claude (Anthropic) | Implementación y pruebas de resiliencia (timeout, circuit breaker, Idempotency-Key, ETag), del tópico con outbox, pruebas de los huecos de seguridad de la matriz (cabeceras, límite de tasa, auditoría, alerta de la DLQ), redacción de este plan y de la matriz de trazabilidad | Las pruebas se corrieron en `mvn verify` (255 pasadas). Los umbrales se contrastaron con el documento de criterios. Falta que cada autor revise y firme sus pruebas y verifique a mano que fallen al romper el código que prueban |

> Completar con el resto de las herramientas del equipo, un prompt representativo por herramienta y al menos un caso en que la IA propuso algo incorrecto y cómo se detectó (sección 8 de los criterios).
