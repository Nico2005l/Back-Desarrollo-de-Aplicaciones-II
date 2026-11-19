# Planta Libre — Documento técnico

**Sistema de reserva de espacios de trabajo**
Universidad Argentina de la Empresa · Desarrollo de Aplicaciones II
Versión actualizada al 3.er checkpoint · 19 de noviembre de 2026. La primera versión se presentó en la entrega obligatoria N.º 1 (17 de septiembre de 2026); los cambios quedan en el historial de Git.

**Integrantes:** Berrotarán, Pedro · Checa Orihuela, Keila Nicole · Lussoro, Nicolás

**Repositorios:** [backend](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II) · [frontend](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II)
**Aplicación publicada:** https://planta-libre-ashen.vercel.app · **API:** https://planta-libre-api.onrender.com

**Documentos relacionados:**

- [Dominio de reservas](DominioReservas.md): catálogo, disponibilidad, reservas y bloqueos.
- [Arquitectura de integración](Integracion.md): cobro asincrónico, resiliencia, Idempotency-Key, ETag y Problem Details.
- [Propuestas de IA](PropuestasIA.md).
- [Plan de pruebas](PlanDePruebas.md).
- [Manual de usuario](ManualDeUsuario.md).

---

## 1. Qué es el sistema

Planta Libre es un sistema para reservar espacios de trabajo, cubículos y salas, en los edificios de las empresas adheridas.

**Recorrido del cliente:**

1. Crea su cuenta, confirma el email y carga un método de pago.
2. Recorre empresa → edificio → piso y ve el plano con el estado de cada espacio en la franja que eligió.
3. Reserva. El cobro se procesa en segundo plano y la reserva pasa de *Pendiente* a *Confirmada*.
4. Puede cancelarla. El reintegro depende de la anticipación.

**El administrador:**

- entra con segundo factor;
- ve la agenda del día y el listado de reservas;
- crea, mueve, cancela o borra reservas a nombre de un cliente, siempre con un motivo;
- bloquea espacios o pisos enteros.

---

## 2. Arquitectura general

El backend es un monolito modular en capas, en Spring Boot 3 con Java 21. Cada pedido HTTP atraviesa siempre el mismo recorrido:

```
Navegador (React)
      ↓ HTTPS /api
Filtros de seguridad  →  valida el token, límite de tasa, Idempotency-Key
      ↓
Controladores REST    →  reciben y validan el JSON, devuelven códigos HTTP y ETag
      ↓
Servicios             →  reglas de negocio y transacciones
      ↓
Repositorios (JPA)    →  consultas y escritura
      ↓
PostgreSQL
```

Además de ese recorrido síncrono, el cobro de una reserva corre en forma asincrónica: la reserva publica un mensaje en RabbitMQ y un consumidor cobra, sin que la persona espere a la pasarela. El detalle está en [Integracion.md](Integracion.md).

Las reglas de dependencia son estrictas y se cumplen en el código:

- Los controladores dependen de **interfaces** de servicio, nunca de repositorios ni de entidades.
- Los servicios concentran las reglas y abren las transacciones. Son los únicos que usan repositorios.
- Las entidades JPA no salen nunca de la capa de datos: hacia afuera viajan DTOs.
- Los sistemas externos se usan a través de un puerto propio y no con la clase del proveedor: `EmailService` para el correo, `PasarelaPagos` para los pagos y `ConsultaFiltraciones` para Pwned Passwords.
- Los servicios de negocio no conocen RabbitMQ: publican eventos de dominio de Spring, y es el paquete `mensajeria` el que los convierte en mensajes.

**Por qué un monolito modular y no microservicios.** El sistema es chico, y lo que se busca son fronteras claras, no despliegues independientes. Las fronteras están expresadas con interfaces y paquetes, así que si en el futuro un componente necesita separarse, el corte ya está marcado. El costo de operar varios servicios hoy no se justificaría.

El frontend es una aplicación React con Vite, TypeScript y Tailwind. Consume la misma API y no tiene lógica de negocio propia: valida los formularios con las mismas reglas que el backend para avisar antes de enviar, pero el backend vuelve a validar todo.

---

## 3. Componentes

Cada componente tiene sus propias clases en cada capa. Ninguno accede a los datos de otro por la base: si necesita algo, lo pide por la interfaz del servicio correspondiente.

| Componente | Presentación | Negocio | Datos |
|---|---|---|---|
| **Identidad y acceso** | `AuthController`, `JwtAuthenticationFilter`, `LimiteTasaFilter` | `AuthService`, `JwtTokenProvider`, `Sesiones`, `Totp`, `PoliticaContrasenias`, `LimitadorDeIntentos` | `UsuarioRepository`, `RefreshTokenRepository`, `TokenBlacklistRepository`, `EmailConfirmationTokenRepository`, `PasswordResetTokenRepository` |
| **Usuarios** | `UsuarioController` | `UsuarioService`, `UsuarioMapper` | `UsuarioRepository` |
| **Medios de pago** | `MedioPagoController` | `MedioPagoService` | `MedioPagoRepository` |
| **Catálogo y disponibilidad** | `CatalogoController` | `CatalogoService`, `MotorDisponibilidad`, `ReglasReserva`, `Ocupaciones` | `EmpresaRepository`, `EdificioRepository`, `PisoRepository`, `EspacioRepository`, `AreaPlanoRepository`, `ServicioPisoRepository` |
| **Reservas** | `ReservaController`, `AdminReservaController` | `ReservaService`, `AdminReservaService`, `TomaDeFranja`, `Cancelaciones`, `PoliticaCancelacion`, `EstadoReserva` | `ReservaRepository` (con `ReservaCambio`) |
| **Cobros** | consumidor `ConsumidorCobros` (cola) | `CobroService`, `Tarifario`, `BarridoCobros`, puerto `PasarelaPagos` | `CobroRepository`, `MensajeProcesadoRepository` |
| **Eventos (tópico)** | suscriptores de `SuscriptoresReservaConfirmada` | `OutboxEventos`, `RelayEventos`, puerto `ControlAcceso` | tabla `eventos_salientes` |
| **Bloqueos** | `BloqueoController` | `BloqueoService` | `BloqueoRepository` |
| **Auditoría** | `AuditoriaController` | `AuditoriaService` | `AuditoriaRepository` |
| **Avisos por mail** | suscriptor de avisos del tópico | `AvisosDeCobro`, `AvisosDeCancelacion`, `EmailService` | — |

**Identidad y acceso.** Resuelve:

- el registro y la confirmación del email;
- el inicio de sesión, con segundo factor TOTP para los administradores;
- la renovación de la sesión con refresh tokens rotativos;
- el cierre de sesión, el cambio de contraseña y la recuperación por mail;
- los límites de intentos.

**Usuarios.** Expone el perfil propio, la edición del teléfono y el listado administrativo de cuentas. El nombre y el email no se modifican, porque el email es la identidad con la que se inicia sesión.

**Medios de pago.** Administra las tarjetas del usuario: alta, edición del titular y el vencimiento, elección de la principal y baja. Tiene tres reglas propias:

- hasta tres tarjetas por usuario;
- no se aceptan tarjetas vencidas;
- siempre hay una principal, que se reasigna sola si se borra la que lo era.

No se guarda el número completo ni el código de seguridad: solo el titular y el vencimiento (cifrados) y los últimos cuatro dígitos.

**Catálogo, Reservas y Bloqueos.** Se describen en [DominioReservas.md](DominioReservas.md). **Cobros**, el **tópico de eventos** con su outbox y sus suscriptores, y los avisos, en [Integracion.md](Integracion.md).

---

## 4. Componente con estado y componentes sin estado

La API no guarda sesión del lado del servidor: Spring Security está configurado como stateless y la identidad viaja en el token en cada pedido.

**Con estado: `LimitadorDeIntentos`.** Cuenta cuántas veces cada cliente (una IP, una cuenta, un usuario con sesión) hizo una operación que puede abusarse, por ejemplo:

- fallar el login o el segundo factor;
- consultar si un email está registrado;
- pedir un link de recuperación;
- crear reservas;
- cualquier pedido, contra el límite global.

Dos llamadas idénticas pueden dar resultados distintos según lo que pasó antes.

- **Dónde viven los contadores:** en una `AlmacenIntentos`. En producción es la base de datos (tabla `limites_intentos`), así que los límites se comparten entre instancias y sobreviven a un reinicio. En las pruebas se usa una implementación en memoria.
- **Ciclo de vida:** lo administra el contenedor de Spring. Crea una única instancia y llama a su método anotado con `@PostConstruct`, que levanta la tarea que descarta los contadores vencidos y lo registra en el log con el prefijo `[ciclo de vida]`. Al apagar la aplicación llama al método anotado con `@PreDestroy`, que detiene esa tarea.

**Sin estado: los servicios de negocio y el de correo.** Los servicios reciben en cada llamada todo lo que necesitan; lo que tienen que recordar está en la base de datos. Sus atributos son dependencias y configuración fijadas al arrancar, no datos de un usuario. Por eso da igual qué instancia atienda cada pedido y pueden atender varios a la vez.

El servicio de correo también lo crea y lo inicializa el contenedor, con un método anotado con `@PostConstruct` que avisa en el log si el envío quedó activo o no.

---

## 5. Patrones de diseño aplicados

### State

**Dónde:** `EstadoReserva`. Cada estado (`PENDIENTE`, `CONFIRMADA`, `CANCELADA`, `FINALIZADA`) implementa `puedePasarA`.
**Por qué:** las transiciones válidas de una reserva viven en el propio estado y no en condicionales repartidos por los servicios. Toda transición pasa por `validarPaso`, y una inválida responde `409` antes de cualquier efecto: por ejemplo, antes de pedir un reintegro. Lo prueban `EstadoReservaTest`, con 40 casos parametrizados, y `TransicionesReservaIntegracionTest`.

### Observer (eventos de dominio)

**Dónde:** en dos niveles.

- **Dentro de la aplicación:** los eventos de dominio `ReservaCreada`, `ReservaConfirmada`, `CobroResuelto`, `CobroReencolado` y `ReservaCancelada`. Los escuchan `PublicadorCobros`, `OutboxEventos`, `AvisosDeCobro` y `AvisosDeCancelacion` con `@TransactionalEventListener`.
- **Entre componentes:** el tópico `planta-libre.eventos`, con tres suscriptores de `reserva.confirmada` (avisos, control de acceso y auditoría).
**Por qué:** quien crea, confirma o cancela una reserva no sabe quién reacciona: el mail, la cola de cobros, el control de acceso. Los oyentes corren **después del commit**, o escriben en el outbox **antes del commit** y dentro de la misma transacción, así que nunca sale un mail ni un mensaje por una reserva que hizo rollback. Agregar una reacción nueva no toca al servicio que publica.

### Decorator

**Dónde:** `PasarelaPagosResiliente`, que envuelve al adaptador de la pasarela.
**Por qué:** agrega el límite de 5 s y el circuit breaker sin que el adaptador ni sus usuarios (`CobroServiceImpl`, `Cancelaciones`) cambien. El adaptador real de un proveedor se envolverá igual.

### Repository (DAO)

**Dónde:** los repositorios del paquete `domain.repository`.
**Por qué:** concentra el acceso a datos y deja los servicios escritos en términos del dominio, sin SQL ni detalles de JPA. Las consultas se declaran por su nombre; las que bloquean una fila (`findBloqueadaById`) lo dicen en su firma. Si una consulta cambia, se cambia en un solo lugar.

### Facade

**Dónde:** `AuthServiceImpl`.
**Por qué:** registrar un usuario implica, en este orden:

1. validar que el email no exista;
2. verificar que la contraseña no esté filtrada;
3. guardar la cuenta como pendiente, con la contraseña cifrada;
4. dar de alta la tarjeta, si vino una;
5. generar el token de confirmación y mandar el mail.

El controlador no debería conocer ese orden. La fachada lo encapsula detrás de una operación y define el límite de la transacción.

### Strategy

**Dónde:**

- `EmailService`, con `SmtpEmailService` y `GmailApiEmailService`;
- `AlmacenIntentos`, con una implementación en la base y otra en memoria.

**Por qué:** el hosting gratuito donde corre la API bloquea los puertos de SMTP, así que ahí los mails salen por la API de Gmail sobre HTTPS, mientras que en desarrollo conviene el SMTP de siempre. La forma activa se elige por configuración (`MAIL_PROVIDER`). El resto del sistema pide "enviá este mail" y no sabe cuál está activa.

### Template Method

**Dónde:** `AbstractEmailService`, clase base de los dos servicios de mail, y `SuscriptorEventos`, clase base de los suscriptores del tópico.
**Por qué:** los textos de los mails, la decisión de escribir el link en el log cuando no hay credenciales y el manejo de errores son iguales para las dos formas de enviar; lo único distinto es el envío en sí. La clase base fija esos pasos y deja un único hueco que cada implementación completa. Con los suscriptores pasa lo mismo: la base fija verificar la firma, convertir el mensaje y deduplicarlo en una transacción, y cada suscriptor solo define qué hace con el evento.

### Adapter

**Dónde:** `CustomUserDetailsService`, `UsuarioMapper` y `PasarelaPagosSimulada`.
**Por qué:**

- Spring Security exige su propia interfaz de usuario: el primero traduce el `Usuario` del dominio, con su rol y sus permisos, sin contaminar el modelo con clases del framework.
- El segundo, generado con MapStruct, traduce la entidad al DTO que ve el cliente, y es lo que evita que el hash de la contraseña salga en una respuesta.
- El tercero implementa el puerto `PasarelaPagos`; un proveedor real es otro adaptador del mismo puerto.

### Builder

**Dónde:** las entidades del dominio, mediante Lombok.
**Por qué:** varias se crean con muchos campos, algunos opcionales y otros con valor por defecto. Construirlas paso a paso y con el nombre de cada campo evita constructores largos donde es fácil equivocar el orden de dos parámetros del mismo tipo.

---

## 6. Seguridad

Los criterios SEC-01 a SEC-10 están implementados. Sus pruebas (`SeguridadTest`, `SesionesTest`, `LimitadorDeIntentosTest`, `AutenticacionIntegracionTest`, `MensajeriaIntegracionTest`) se cruzan criterio por criterio en la [matriz de trazabilidad](MatrizTrazabilidad.md). Todos tienen prueba automatizada, con dos excepciones: el rechazo de `guest` y de AMQP sin TLS (SEC-07) se verifica en la configuración del broker, y el escaneo de secretos del historial (SEC-10) espera a gitleaks.

| Criterio | Qué hace |
|---|---|
| SEC-01 | Cabeceras en todas las respuestas: CSP, HSTS, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy` y `Permissions-Policy` |
| SEC-02 | Token de acceso JWT de 15 minutos y refresh token rotativo de 7 días en cookie HttpOnly. Reusar un refresh ya gastado revoca toda la familia de la sesión. Un token emitido antes de un cambio de contraseña deja de valer |
| SEC-03 | Segundo factor TOTP obligatorio para los administradores |
| SEC-04 | Contraseñas filtradas rechazadas, contra una lista local y contra Have I Been Pwned con k-anonimato. Bloqueo de intentos de login persistido en la base |
| SEC-05 | Bitácora de auditoría con usuario, fecha, IP, user-agent y resultado, consultable por los administradores (`/api/admin/auditoria`) |
| SEC-06 | Límite de tasa global y para crear reservas, compartido entre instancias. Al superarlo responde `429` con `Retry-After` |
| SEC-07 | Broker solo con TLS, sin la cuenta `guest`, con usuario y vhost propios y permisos mínimos |
| SEC-08 | Mensajes de la cola firmados con HMAC, deduplicación persistente y alerta cuando llega un mensaje a la DLQ |
| SEC-09 | Teléfono, titular, vencimiento y secreto TOTP cifrados con AES-GCM, con claves por entorno y rotación |
| SEC-10 | Secretos obligatorios sin valores por defecto: la API no arranca si faltan o son débiles. Ningún secreto está en el repositorio |

**Autorización declarativa.** `SecurityConfig` declara qué rutas son públicas: registro, login, confirmación, recuperación, renovación de sesión, el chequeo de salud y el contrato OpenAPI. Todo lo demás exige un token válido. Sobre eso, cada operación exige un permiso con `@PreAuthorize`, por ejemplo `RESERVAS_CREAR`, `RESERVAS_GESTIONAR` o `BLOQUEOS_GESTIONAR`. Un usuario sin el permiso recibe `403`, y uno sin token, `401`. El usuario de cada operación sale siempre del token, nunca del pedido, así que nadie puede ver ni tocar recursos ajenos: responde `404`.

**Cuidados adicionales.**

- Las cuentas nacen pendientes y no pueden iniciar sesión hasta confirmar el email. Esa verificación se hace después de comprobar la contraseña, para que un intento con contraseña incorrecta no revele que la cuenta existe.
- "Olvidé mi contraseña" responde siempre lo mismo, exista o no la cuenta. El envío del mail ocurre en segundo plano, para que el tiempo de respuesta tampoco delate esa diferencia.
- De los links enviados por mail solo se guarda su hash, igual que con las contraseñas.
- Los errores responden en formato Problem Details (RFC 9457), con un mensaje entendible y el código HTTP que corresponde. Las fallas inesperadas se registran en el log y hacia afuera responden un mensaje genérico, sin stack trace.

---

## 7. Decisiones de diseño y sus motivos

Las decisiones principales tienen su ADR, con el contexto y las alternativas evaluadas, en [adr/](adr/README.md). La tabla las resume junto con otras decisiones menores.

| Decisión | Alternativa descartada | Motivo |
|---|---|---|
| Token de acceso corto (15 min) y refresh rotativo | Un solo JWT de larga duración | El backend puede reiniciarse o multiplicarse sin perder sesiones. Un token robado sirve poco tiempo, y cerrar sesión o cambiar la contraseña revocan de verdad la sesión |
| Confirmación de email obligatoria | Permitir usar la cuenta sin confirmar | Asegura que la dirección existe y es de quien se registra, que es justamente el dato con el que después se recupera la contraseña |
| Link de confirmación de un solo uso y sin vencimiento | Link con vencimiento corto | Se priorizó que nadie quede afuera por abrir el mail tarde. Que sirva una sola vez evita que quede como una llave permanente |
| Link de recuperación con 30 minutos de vida | Sin vencimiento | Cambia la contraseña, una operación más sensible: si el mail se filtra más tarde, el link ya no sirve |
| Tarjetas guardadas enmascaradas | Guardar el número completo | El cobro lo hace la pasarela; un proveedor real trabaja con la tarjeta tokenizada. Guardar menos datos sensibles elimina el riesgo de perderlos |
| Cobro asincrónico por cola | Cobrar dentro del `POST /api/reservas` | Una pasarela lenta no deja a la persona esperando ni mantiene bloqueado el espacio. Ver [Integracion.md](Integracion.md) |
| Migraciones con Flyway y esquema validado, no generado | Dejar que el ORM cree las tablas | El esquema queda versionado y es igual en cada máquina y en producción. El ORM no puede modificarlo por accidente |
| Un solo `application.yml` con valores por defecto locales | Un archivo de configuración por ambiente | Cada valor que cambia entre ambientes se lee de una variable de entorno. Los secretos no tienen valor por defecto (SEC-10) |
| Los servicios se usan a través de interfaces | Llamar directo a las implementaciones | Permite tener dos implementaciones del envío de mails, decorar la pasarela y probar un componente sin levantar sus dependencias |
| Validación repetida en el frontend y el backend | Validar solo en el backend | El backend es el que manda, porque la API es pública. La validación del frontend existe para avisar antes de enviar |
| Errores en Problem Details, con `message` y `code` | Devolver solo el código HTTP | Formato estándar, y la interfaz puede mostrar el error en el campo que corresponde y reaccionar a casos puntuales, como una franja tomada o una cuenta sin confirmar |

---

## 8. Datos

El esquema tiene 21 tablas, agrupadas por componente:

| Grupo | Tablas |
|---|---|
| Identidad y acceso | `usuarios`, `refresh_tokens`, `token_blacklist`, `email_confirmation_tokens`, `password_reset_tokens`, `limites_intentos` |
| Medios de pago | `medios_pago` |
| Catálogo | `empresas`, `edificios`, `pisos`, `espacios`, `areas_plano`, `servicios_piso` |
| Reservas y bloqueos | `reservas`, `reserva_cambios`, `bloqueos` |
| Cobros y mensajería | `cobros`, `mensajes_procesados`, `eventos_salientes` (outbox) |
| Transversales | `auditoria`, `claves_idempotencia` |

El esquema se construye con 29 migraciones de Flyway que se aplican solas al arrancar. Cada cambio de esquema es una migración nueva: ninguna se edita después de haberse aplicado, porque eso rompería las bases donde ya corrió.

- **Outbox:** `eventos_salientes` guarda los eventos del tópico en la misma transacción que el cambio que anuncian, hasta que el broker confirma que los recibió.
- **Usuarios y tarjetas:** un usuario tiene muchas tarjetas y muchos tokens. Las tarjetas se dan de baja lógicamente, para no perder el historial.
- **Superposición de reservas:** `reservas` y `bloqueos` tienen una restricción de exclusión de PostgreSQL sobre espacio y rango de tiempo, que hace imposible que dos ocupaciones se superpongan.
- **Versión para el ETag:** `reservas`, `medios_pago` y `usuarios` tienen una columna `version` que incrementa un trigger en cada UPDATE.

---

## 9. Despliegue

**Local, todo con un comando.** `docker compose up --build` levanta base, broker, API y front, y genera los secretos al arrancar (ver el [README](../README.md)). Es el entorno de la demo y de las pruebas manuales.

**Publicado:**

| Parte | Plataforma | Notas |
|---|---|---|
| Aplicación web | Vercel | Sirve la interfaz y reenvía las llamadas a `/api` hacia la API, con lo que el navegador ve un solo origen |
| API | Render | Imagen Docker construida en dos etapas: una compila con Maven y la final solo lleva el Java de ejecución y el artefacto. La plataforma verifica que esté viva con un endpoint de salud. El cobro asincrónico queda apagado hasta configurar un broker en la nube (`RABBITMQ_URL`) |
| Base de datos | Neon | PostgreSQL administrado, en la misma región que la API |

La configuración sensible se carga como variables de entorno en la plataforma.

**Una restricción del hosting cambió un diseño.** El plan gratuito de Render bloquea la salida por los puertos de SMTP, así que el envío por SMTP, que funciona en desarrollo, no sirve en producción. En vez de cambiar de hosting o de pagar, se agregó una segunda implementación que manda por la API de Gmail sobre HTTPS y se eligió con una variable. El resto del sistema no se enteró del cambio, que es exactamente para lo que se había puesto la interfaz.

---

## 10. Verificación

La estrategia, los niveles, los riesgos y las métricas están en el [plan de pruebas](PlanDePruebas.md).

**Corrida del 19/11/2026:**

- 255 pruebas de JUnit, unitarias y de integración contra PostgreSQL y RabbitMQ reales con Testcontainers.
- Cobertura de la capa de negocio: 94,6 % de líneas y 78,9 % de ramas.
- Mutation score del dominio: 74 %.
- 0 hallazgos de SpotBugs/FindSecBugs.
- 99 pedidos de Postman, corridos con Newman en el CI.
- Contrato OpenAPI validado con Schemathesis.

---

## 11. Límites conocidos y próximos pasos

1. **El listado administrativo de usuarios no tiene paginado.** Con pocos usuarios no molesta; conviene resolverlo antes de que crezca. El de reservas y el de auditoría sí están paginados.
2. **Sistemas externos simulados:** la pasarela de pagos y el control de acceso del edificio son simuladores detrás de sus puertos; falta el adaptador de un proveedor real ([Integracion.md](Integracion.md), sección 7).
3. **Límites del dominio de reservas:** no hay check-in ni liberación por no presentarse, ni reservas recurrentes ([DominioReservas.md](DominioReservas.md), sección 10).
4. **Pruebas pendientes para la entrega final:** punta a punta, carga, ZAP y escaneo de secretos ([PlanDePruebas.md](PlanDePruebas.md), sección 9).
