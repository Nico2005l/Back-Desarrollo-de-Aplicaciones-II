# Planta Libre — Matriz de trazabilidad

**Versión 1 · 19/11/2026** · Sección 8 del [plan de pruebas](PlanDePruebas.md).

Cruza cada criterio de aceptación de las historias de usuario con su caso de prueba, la prueba automatizada que lo ejecuta, el resultado y la evidencia. Las historias salen del tablero del equipo (GitHub Projects, «Planta Libre»), que reúne los issues de los dos repositorios.

## Cómo se lee

- **ID de la historia:** `B-n` es el issue n del [backend](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues) y `F-n` el issue n del [frontend](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues). Cada repositorio numera por separado.
- **Criterio:** las historias SEC traen Dado/Cuando/Entonces y se parte una fila por cada «Entonces». En las demás, el criterio está escrito en la descripción y se parte en criterios atómicos (`B-13.a`, `B-13.b`, …).
- **Tipo de caso:** P (positivo), N (negativo: entrada inválida, acceso indebido o falla) y B (borde: límites, concurrencia, repetición).
- **Prueba automatizada:** clase y método JUnit (`src/test/java`), o pedido de la colección de Postman ([api-tests](../api-tests)) con el prefijo «Newman».
- **Resultado:**
  - *Pasa*: la prueba corre en el CI y está en verde.
  - *Parcial*: la prueba cubre solo una parte del criterio, y la parte que falta se aclara en la fila.
  - *Sin prueba*: el criterio está implementado, pero ninguna prueba automatizada lo verifica.
  - *Manual*: es un criterio de la interfaz, sin prueba automatizada porque el front todavía no tiene pruebas de punta a punta. Falta ejecutarlo y guardar la captura.
  - *No implementado*: el criterio no está en el sistema.
- **Evidencia:**
  - E1: job «Build, tests y análisis estático» del [run 44585045716](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/actions/runs/44585045716) (19/11/2026, commit `22fdae4`), con los reportes de Surefire y JaCoCo.
  - E2: job «Pruebas de API (Newman y Schemathesis)» del mismo run, artefacto `pruebas-api`.
  - E3: ejecución manual con captura, pendiente.
  - E4: prueba agregada después de ese run. Pasa en `mvn verify` local del 19/11/2026 y queda en el próximo run del CI.
- **Defecto:** bug del tablero que motivó la prueba, que queda como prueba de regresión.

## Alcance

**Incluidas:**
- Las historias funcionales y de seguridad.
- Las tareas que describen comportamiento verificable: B-3, B-8, F-21, F-30, F-31 y F-40.

**Excluidas:**
- **Historias de diseño:** F-2, F-3, F-4, F-5, F-6, F-7 y F-10, que son wireframes y el sistema de diseño. Son entregables de diseño, no comportamiento del sistema, y se verifican por revisión.
- **Tareas de proyecto:** relevamiento, contratos, despliegue y documentos.
- **Spikes:** F-11 y F-15, porque sus decisiones se prueban en las historias que las implementan.

---

## 1. Identidad y acceso

Historias: [B-1](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/1) Confirmación de la cuenta por email · [F-18](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/18) Registro · [F-19](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/19) Login · [F-20](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/20) Refresco y cierre de sesión · [F-21](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/21) Middleware de autenticación y rol · [B-8](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/8) Invalidar sesiones al cambiar la contraseña · [F-22](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/22) Pantalla de login · [F-23](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/23) Pantalla de registro · [F-24](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/24) Recuperación de contraseña · [F-25](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/25) Perfil · [B-4](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/4) Permisos por rol · [B-3](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/3) Límite de intentos por IP

| Criterio | Caso | Tipo | Prueba automatizada | Resultado | Evid. | Defecto |
|---|---|---|---|---|---|---|
| **F-18.a** El alta con datos válidos crea la cuenta | Registro con nombre, email, teléfono y contraseña válidos: 201 | P | `AutenticacionIntegracionTest.registroConfirmacionEInicioDeSesion`<br>Newman «Registro» | Pasa | E1, E2 | — |
| **F-18.b** El email es único | Registro con un email ya usado: 409 | N | `AutenticacionIntegracionTest.registroConfirmacionEInicioDeSesion`<br>Newman «Registro con email repetido» | Pasa | E1, E2 | — |
| **F-18.c** La contraseña cumple la política | Registro con contraseña filtrada: 400 | N | `SeguridadTest.unaContrasenaFiltradaSeRechaza` | Parcial: la contraseña corta o sin número no tiene prueba | E1 | — |
| **B-1.a** Sin confirmar no se inicia sesión | Login de una cuenta pendiente: 403 `EMAIL_NO_CONFIRMADO` | N | `AutenticacionIntegracionTest.registroConfirmacionEInicioDeSesion` | Pasa | E1 | — |
| **B-1.b** El link confirma la cuenta e inicia sesión | Confirmar con el link del mail: 200 con token | P | `AutenticacionIntegracionTest.registroConfirmacionEInicioDeSesion`<br>Newman «Confirmar email» | Pasa | E1, E2 | — |
| **B-1.c** El link es de un solo uso | Reusar el link: 400 `LINK_USADO`; link inexistente: 400 `LINK_INVALIDO` | B | `AutenticacionIntegracionTest.registroConfirmacionEInicioDeSesion`<br>Newman «Confirmar email con link inválido» | Pasa | E1, E2 | — |
| **B-1.d** Se puede reenviar el link | Reenviar confirmación: 200; sin email: 400 | P | `AutenticacionIntegracionTest.registroConfirmacionEInicioDeSesion`<br>Newman «Reenviar confirmación», «Reenviar confirmación sin email» | Pasa | E1, E2 | — |
| **F-19.a** Las credenciales correctas inician sesión | Login de cliente: 200 con token | P | Newman «Login de cliente» | Pasa | E2 | — |
| **F-19.b** Respuesta única ante credenciales incorrectas | Contraseña incorrecta: 401 | N | `AutenticacionIntegracionTest.unaClaveIncorrectaNoInicia`<br>Newman «Login con clave incorrecta», «Login sin email» | Parcial: el email inexistente no tiene prueba | E1, E2 | — |
| **F-19.c** Tope de 10 intentos fallidos cada 15 minutos | El fallo 11 se bloquea y un acierto reinicia la cuenta; el bloqueo vence con la ventana | B | `LimitadorDeIntentosTest.elLoginSeTrabaRecienDespuesDeDiezFallosYSeLiberaAlAcertar`, `.elBloqueoDelLoginVenceConLaVentana` | Pasa | E1 | [B-15](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/15) |
| **F-20.a** El refresh rota en cada uso | Renovar la sesión: nuevo par, el refresh anterior queda gastado | P | `SesionesTest.rotarGastaElTokenYEmiteOtroDeLaMismaFamilia`<br>`AutenticacionIntegracionTest.laSesionSeRenuevaYUnRefreshReusadoSeRechaza`<br>Newman «Renovar la sesión» | Pasa | E1, E2 | — |
| **F-20.b** Reusar un refresh revoca la familia | Reusar el refresh gastado: 401 y la sesión queda revocada | N | `SesionesTest.reutilizarUnTokenGastadoRevocaTodaLaFamilia`, `.unTokenDesconocidoEs401`<br>Newman «Renovar sin sesión» | Pasa | E1, E2 | — |
| **F-20.c** Cerrar sesión invalida el token | Logout y después pedido con el mismo token: 401 | P | `AutenticacionIntegracionTest.cerrarSesionInvalidaElToken`<br>Newman «Cerrar sesión», «El token cerrado ya no sirve» | Pasa | E1, E2 | — |
| **F-21.a / B-8** Se rechaza un token anterior al cambio de contraseña | Cambiar la contraseña y usar el token viejo: 401 | N | `AutenticacionIntegracionTest.cambiarLaClaveCierraLasDemasSesiones` | Pasa | E1 | — |
| **F-21.b** Token inválido o ausente | Sin token o con un JWT mal formado: 401 | N | `AutenticacionIntegracionTest.unaClaveIncorrectaNoInicia`<br>`ContratoApiIntegracionTest.losErroresDeSeguridadTambienSiguenProblemDetails`<br>Newman «Empresas sin sesión», «Edificios con token inválido» | Pasa | E1, E2 | — |
| **B-4.a** Solo el administrador lista las cuentas | Cliente en `GET /api/usuarios`: 403 | N | `AutenticacionIntegracionTest.unClienteNoVeElListadoDeUsuariosNiLaAuditoria`<br>Newman «Listado de usuarios como cliente» | Pasa | E1, E2 | — |
| **B-4.b** El administrador lista las cuentas | Administrador en `GET /api/usuarios`: 200 | P | `AutenticacionIntegracionTest.elAdminEntraConSegundoFactor`<br>Newman «Listado de usuarios» | Pasa | E1, E2 | — |
| **B-4.c** El cliente no entra al panel de administración | Cliente en `/api/admin/*`: 403 | N | `AdminReservasIntegracionTest.unClienteNoEntraAlPanelDelAdmin`<br>Newman «Listado de reservas como cliente», «Buscar clientes como cliente», «Bloqueos como cliente» | Pasa | E1, E2 | — |
| **F-24.a** El pedido responde igual exista o no la cuenta | Pedir recuperación con email existente y con uno inexistente: 200 en los dos | N | `AutenticacionIntegracionTest.recuperarLaClaveConElLinkDelMail`<br>Newman «Olvidé mi clave», «Olvidé mi clave sin email» | Pasa | E1, E2 | — |
| **F-24.b** El token es de un solo uso | Restablecer dos veces con el mismo token: 200 y 400 | B | `AutenticacionIntegracionTest.recuperarLaClaveConElLinkDelMail`<br>Newman «Restablecer la clave», «Restablecer con link inválido» | Pasa | E1, E2 | — |
| **F-24.c** El token vence a los 30 minutos | El link pedido vence a los 30 minutos; restablecer con un token vencido: 400 y la contraseña no cambia | B | `AutenticacionIntegracionTest.unLinkDeRecuperacionDuraTreintaMinutosYVencidoNoSirve` | Pasa | E4 | — |
| **F-25.a** Se edita el teléfono | Editar mi teléfono: 200; teléfono inválido: 400 | P | `AutenticacionIntegracionTest.registroConfirmacionEInicioDeSesion`<br>Newman «Editar mi teléfono», «Editar con teléfono inválido» | Pasa | E1, E2 | — |
| **F-25.b** El cambio de contraseña pide la actual | Contraseña actual incorrecta: 400 | N | `AutenticacionIntegracionTest.cambiarLaClaveCierraLasDemasSesiones`<br>Newman «Cambiar la clave con la actual incorrecta» | Pasa | E1, E2 | — |
| **F-25.c** Cambiar la contraseña cierra las demás sesiones | Token emitido antes del cambio: 401; login con la nueva: 200 | P | `AutenticacionIntegracionTest.cambiarLaClaveCierraLasDemasSesiones`<br>Newman «Cambiar la clave» | Pasa | E1, E2 | — |
| **F-25.d** Nombre y email no se editan | Pantalla de perfil con nombre y email bloqueados | P | — | Manual | E3 | — |
| **B-3.a** Las operaciones públicas tienen tope por IP | Superar el máximo de una acción: rechazo, con contadores separados por cliente y acción, que se liberan al vencer la ventana | B | `LimitadorDeIntentosTest.permiteHastaElMaximoYDespuesRechaza`, `.guardaEstadoSeparadoPorClienteYPorAccion`, `.vuelveAPermitirCuandoVenceLaVentana` | Parcial: prueba el componente; el 429 del endpoint no tiene prueba | E1 | — |
| **F-22** Login: error, redirección según rol y sesión al recargar | Ingresar como cliente y como administrador y recargar la página | P | — | Manual | E3 | — |
| **F-23** Registro: validación en vivo, aviso de email ya registrado y modal de pago que no bloquea | Completar el formulario con un email ya registrado y sin tarjeta | N | Newman «Email disponible», «Email disponible con email inválido» (solo el endpoint) | Manual | E2, E3 | — |

## 2. Seguridad (criterios SEC)

Historias: [B-26](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/26) SEC-01 a [B-35](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/35) SEC-10, origen «Planta Libre - Criterios de seguridad v2».

| Criterio | Caso | Tipo | Prueba automatizada | Resultado | Evid. | Defecto |
|---|---|---|---|---|---|---|
| **SEC-01.a** Cabeceras CSP, HSTS, nosniff, X-Frame-Options DENY, Referrer-Policy y Permissions-Policy en toda respuesta | Por HTTP real: respuesta pública (200), error (401) y ruta inexistente traen las seis cabeceras | P | `CabecerasSeguridadIntegracionTest.todaRespuestaTraeLasCabecerasDeSeguridadYNoRevelaElServidor` (3 casos) | Pasa | E4 | — |
| **SEC-01.b** La respuesta no revela la versión del servidor | Las mismas tres respuestas, sin cabeceras `Server` ni `X-Powered-By` | N | `CabecerasSeguridadIntegracionTest.todaRespuestaTraeLasCabecerasDeSeguridadYNoRevelaElServidor` | Pasa | E4 | — |
| **SEC-02.a** El token de acceso dura como máximo 15 minutos | Token emitido con la configuración real: vence 15 minutos después de emitido | B | `LimiteTasaIntegracionTest.elTokenDeAccesoVenceALosQuinceMinutos` | Pasa | E4 | — |
| **SEC-02.b** El refresh emite un par nuevo y rota | Ver F-20.a | P | `SesionesTest.rotarGastaElTokenYEmiteOtroDeLaMismaFamilia` | Pasa | E1 | — |
| **SEC-02.c** Reusar un refresh rotado devuelve 401 y revoca la familia | Ver F-20.b | N | `SesionesTest.reutilizarUnTokenGastadoRevocaTodaLaFamilia` | Pasa | E1 | — |
| **SEC-03.a** El administrador necesita el segundo factor para obtener el token | Login de administrador: pide el código; con un TOTP válido: token | P | `AutenticacionIntegracionTest.elAdminEntraConSegundoFactor`<br>`SeguridadTest.totpCoincideConLosVectoresDelRfc6238`<br>Newman «Login de administrador», «Segundo factor» | Pasa | E1, E2 | — |
| **SEC-03.b** Un código incorrecto o repetido se rechaza | Código incorrecto: 401 `CODIGO_INCORRECTO`; reusar un código: rechazo | N | `AutenticacionIntegracionTest.elAdminEntraConSegundoFactor`<br>`SeguridadTest.totpAceptaUnPasoDeDesfasajePeroNoReutilizarElCodigo`<br>Newman «Segundo factor con código incorrecto» | Pasa | E1, E2 | — |
| **SEC-04.a** Una contraseña filtrada se rechaza | Contraseña presente en filtraciones: 400; a Have I Been Pwned solo viaja el prefijo del hash | N | `SeguridadTest.unaContrasenaFiltradaSeRechaza`, `.laConsultaDeFiltracionesSoloMandaElPrefijoDelHash` | Pasa | E1 | — |
| **SEC-04.b** El bloqueo por intentos sobrevive a un reinicio | Un limitador nuevo sobre el mismo almacén, en memoria y en la base real, ve el cliente bloqueado | B | `LimitadorDeIntentosTest.elBloqueoSobreviveAUnReinicioDelLimitador`<br>`LimiteTasaIntegracionTest.dosInstanciasCompartenElContadorYUnReinicioNoLoBorra` | Pasa | E1, E4 | — |
| **SEC-05.a** Los eventos de seguridad quedan en la bitácora | Login con segundo factor: aparece `LOGIN_SEGUNDO_FACTOR` en `/api/admin/auditoria` | P | `AutenticacionIntegracionTest.elAdminEntraConSegundoFactor`<br>Newman «Auditoría» | Pasa | E1, E2 | — |
| **SEC-05.b** Cada registro tiene usuario, fecha, IP, user-agent y resultado, sin datos sensibles | Login fallido y login correcto desde una IP y un user-agent conocidos: dos registros con todos los campos; ni las contraseñas ni el token ni la cookie de refresh aparecen en la bitácora | N | `AutenticacionIntegracionTest.laBitacoraGuardaQuienDesdeDondeYElResultadoSinDatosSensibles` | Pasa | E4 | — |
| **SEC-05.c** Solo el administrador consulta la bitácora | Cliente en `/api/admin/auditoria`: 403 | N | `AutenticacionIntegracionTest.unClienteNoVeElListadoDeUsuariosNiLaAuditoria`<br>Newman «Auditoría como cliente» | Pasa | E1, E2 | — |
| **SEC-06.a** Al superar el umbral responde 429 de forma consistente entre instancias | 201 pedidos de un usuario: 429 con `Retry-After: 60` en Problem Details, otro usuario sigue atendido y la auditoría registra un solo `LIMITE_SUPERADO`; la reserva número 11 en un minuto: 429; dos instancias suman sobre el mismo contador | B | `LimiteTasaIntegracionTest.superadoElLimiteGeneralResponde429ConRetryAfterYQuedaUnaSolaVezEnLaAuditoria`, `.crearReservasTieneSuPropioLimiteMasBajo`, `.dosInstanciasCompartenElContadorYUnReinicioNoLoBorra` | Pasa | E4 | — |
| **SEC-06.b** El límite no se reinicia al reiniciar una instancia | Un limitador nuevo sobre la base (la instancia reiniciada) ve al cliente por encima del límite | B | `LimiteTasaIntegracionTest.dosInstanciasCompartenElContadorYUnReinicioNoLoBorra` | Pasa | E4 | — |
| **SEC-07.a** El broker exige TLS y una cuenta propia de la aplicación | La suite de integración conecta por amqps con un usuario dedicado. El broker rechaza `guest/guest` y el puerto 5672 sin TLS | P | `MensajeriaIntegracionTest` (toda la clase)<br>[SeguridadBroker.md](SeguridadBroker.md) (4 conexiones: control positivo y los dos rechazos) | Pasa | E1, E3 | — |
| **SEC-08.a** Un mensaje alterado se rechaza por firma inválida | Mensaje con firma alterada: va a la DLQ sin cobrar; un evento alterado va a la DLQ de cada suscriptor | N | `MensajeriaIntegracionTest.unMensajeConFirmaInvalidaVaALaDlqSinCobrar`, `.unEventoConFirmaInvalidaVaALaDlqDeCadaSuscriptor`<br>`SeguridadTest.unMensajeAlteradoNoPasaLaFirma` | Pasa | E1 | — |
| **SEC-08.b** Un duplicado no se reprocesa | El mismo mensaje dos veces: un solo cobro; el mismo evento republicado: se procesa una vez por suscriptor | B | `MensajeriaIntegracionTest.unMensajeDuplicadoSeProcesaUnaSolaVez`, `.unEventoRepublicadoConElMismoIdSeProcesaUnaSolaVez`<br>`CobroServiceImplTest.unMensajeRepetidoNoCobraDosVeces`, `.unMensajeYaProcesadoNoSeVuelveACobrar` | Pasa | E1 | — |
| **SEC-08.c** Un mensaje en la DLQ genera una alerta | Mensajes nuevos en la DLQ: una alerta en la auditoría y por mail; los mismos mensajes no vuelven a alertar; sin mail configurado queda solo la auditoría; broker sin responder: ni alerta ni error | N | `MonitorDlqTest` (4 casos) | Pasa | E4 | — |
| **SEC-09.a** Los campos sensibles se guardan cifrados con AES-GCM | Cifrar y descifrar un campo; un valor alterado no se descifra | P | `SeguridadTest.elCampoQuedaCifradoYSeRecuperaIgual`, `.unValorAlteradoNoSeDescifra` | Pasa | E1 | — |
| **SEC-09.b** Las claves rotan | Con la clave rotada se lee lo viejo y se marca para recifrar | B | `SeguridadTest.conLaClaveRotadaSeLeeLoViejoYSeMarcaParaRecifrar` | Pasa | E1 | — |
| **SEC-10.a** La aplicación no arranca con un secreto ausente o débil | Arranque sin `JWT_SECRET` o con uno corto: falla | N | `SeguridadTest.sinSecretoOConUnoDebilLaAplicacionNoArranca` | Pasa | E1 | [B-17](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/17) |
| **SEC-10.b** No hay secretos en el repositorio | Escaneo de secretos de todo el historial | N | — | Sin prueba (gitleaks, pendiente en el plan de pruebas sección 9) | — | [B-17](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/17) |

## 3. Catálogo y disponibilidad

Historias: [F-28](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/28) Listado de empresas, edificios y pisos · [F-29](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/29) Motor de superposición · [F-30](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/30) Estados de los espacios de un piso · [F-31](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/31) Próxima franja libre · [B-12](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/12) Disposiciones de planta por piso

| Criterio | Caso | Tipo | Prueba automatizada | Resultado | Evid. | Defecto |
|---|---|---|---|---|---|---|
| **F-28.a** Se navega empresa, edificio y piso | Las empresas listan sus edificios y los edificios sus pisos | P | `CatalogoIntegracionTest.lasEmpresasListanSusEdificios`, `.losEdificiosListanSusPisos`<br>Newman «Empresas», «Edificios», «Empresa», «Edificio» | Pasa | E1, E2 | — |
| **F-28.b** Un recurso inexistente responde 404 | Empresa, edificio, piso o espacio inexistente | N | `CatalogoIntegracionTest.losRecursosQueNoExistenResponden404`<br>Newman «Empresa inexistente», «Edificio inexistente», «Espacio inexistente» | Pasa | E1, E2 | — |
| **F-29.a** Una franja que se superpone con una reserva activa o un bloqueo está ocupada | Franja que envuelve a otra: choca; el bloqueo gana al pintar el estado | P | `MotorDisponibilidadTest.unaFranjaQueEnvuelveOtraChoca`, `.unBloqueoGanaSobreUnaReservaAlPintarElEstado`<br>`EstadoReservaTest.soloPendienteYConfirmadaOcupanLaFranja` | Pasa | E1 | — |
| **F-29.b** Las franjas contiguas no se superponen | 10:00–11:00 y 11:00–12:00 conviven | B | `MotorDisponibilidadTest.franjasContiguasNoSeSuperponen`<br>`ReservaRepositoryIntegracionTest.lasReservasDelClienteSeSuperponenConRangosSemiabiertos` | Pasa | E1 | — |
| **F-29.c** Al editar, la reserva no choca consigo misma | Mover una reserva a una franja que se pisa con la suya | B | `MotorDisponibilidadTest.editarUnaReservaNoChocaConsigoMisma` | Pasa | E1 | — |
| **F-29.d** La base impide dos reservas activas superpuestas del mismo espacio | Insertar dos activas superpuestas: la base rechaza; una cancelada no ocupa | B | `ReservaRepositoryIntegracionTest.laBaseNoAdmiteDosReservasActivasSuperpuestasDelMismoEspacio`, `.unaReservaCanceladaNoOcupaLaFranjaEnLaBase` | Pasa | E1 | — |
| **F-30.a** El plano da el estado de cada espacio y el resumen de libres | Plano de un piso en una franja: todos los espacios, con libres, ocupados y bloqueados | P | `CatalogoIntegracionTest.elPlanoDeCadaPisoMuestraTodosSusEspacios`, `.elPlanoMarcaOcupadoElEspacioReservadoEnLaFranjaPedida`<br>`BloqueosIntegracionTest.elPlanoMuestraBloqueadoElEspacio`<br>Newman «Plano del piso» | Pasa | E1, E2 | — |
| **F-30.b** El plano rechaza una franja inválida | Horario invertido: 400 | N | `CatalogoIntegracionTest.unPlanoConElHorarioInvertidoEsUnPedidoInvalido`<br>Newman «Plano con horario invertido» | Pasa | E1, E2 | — |
| **F-31.a** Se calcula la próxima franja libre | Huecos libres máximos que empiezan en el próximo bloque de media hora | P | `MotorDisponibilidadTest.losHuecosSonLosRangosLibresMaximos`, `.losHuecosDelDiaEmpiezanEnElProximoBloqueDeMediaHora`, `.redondeaHaciaArribaAlProximoBloque`<br>`CatalogoIntegracionTest.lasFranjasLibresEsquivanLasReservas`<br>Newman «Franjas libres» | Pasa | E1, E2 | — |
| **F-31.b** Sin lugar hoy, la próxima franja es otro día | Día completo: salta al siguiente; fin de semana: el lunes | B | `MotorDisponibilidadTest.elProximoHuecoSaltaAlDiaSiguienteSiHoyEstaTodoTomado`, `.elFinDeSemanaNoHayHuecosYElProximoEsElLunes` | Pasa | E1 | — |
| **F-31.c** Los atajos del estado vacío sugieren franjas cercanas | Huecos ordenados por cercanía al horario pedido; primera franja con algún espacio libre | P | `MotorDisponibilidadTest.losHuecosCercanosSeOrdenanPorCercaniaAlHorarioPedidoYDespuesPorHora`, `.laSugerenciaBuscaLaPrimeraFranjaConAlgunEspacioLibre` | Pasa | E1 | — |
| **B-12** Cada piso tiene su disposición y la geometría es válida | Generar las disposiciones y validar que las áreas no se pisen | P | — | Sin prueba en el CI (lo valida `scripts/generar_disposiciones.py` al generar la migración) | — | — |

## 4. Reservas y cobro

Historias: [F-32](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/32) Plano con los cinco estados · [F-33](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/33) Filtros · [F-34](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/34) Panel del espacio · [F-35](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/35) Confirmación con revalidación · [F-43](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/43) Método de pago al confirmar · [F-36](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/36) Mis reservas · [B-10](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/10) Cobro asincrónico · [B-18](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/18) Barrido de cobros · [B-13](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/13) Política de cancelación · [B-20](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/20) Tópico de eventos de reserva

| Criterio | Caso | Tipo | Prueba automatizada | Resultado | Evid. | Defecto |
|---|---|---|---|---|---|---|
| **F-32** El plano muestra los cinco estados y el cliente abre solo los libres | Plano con espacios libres, ocupados, bloqueados y servicios; clic en uno ocupado | P | — | Manual (los estados que da la API están en F-30.a) | E3 | [F-46](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/46) |
| **F-33.a** El plano se filtra por tipo, capacidad y ventana | Plano con filtros: solo los espacios que cumplen | P | `CatalogoIntegracionTest.elPlanoAceptaFiltrosPorTipoCapacidadYVentana` | Pasa | E1 | — |
| **F-33.b** La interfaz muestra el contador de resultados y permite limpiar | Aplicar y limpiar filtros | P | — | Manual | E3 | — |
| **F-34.a** El panel trae el detalle del espacio | Detalle con ubicación y precio | P | `CatalogoIntegracionTest.elDetalleDelEspacioTraeSuUbicacionYPrecio`<br>Newman «Espacio» | Pasa | E1, E2 | — |
| **F-34.b** El calendario semanal muestra la agenda del espacio | Agenda de la semana: el detalle de cada reserva solo para su dueño y el administrador; rango invertido: 400 | N | `CatalogoIntegracionTest.laAgendaMuestraElDetalleSoloAlDuenoYAlAdmin`, `.laAgendaRechazaUnRangoInvalido`<br>Newman «Agenda del espacio», «Agenda con rango invertido» | Pasa | E1, E2 | — |
| **F-35.a** La reserva nace pendiente | Reservar una franja libre: 201, estado `PENDIENTE` | P | `ReservasClienteIntegracionTest.reservaUnaFranjaLibreYQuedaPendiente`<br>Newman «Reservar» | Pasa | E1, E2 | — |
| **F-35.b** Se revalida la franja antes de crear | Franja tomada mientras el modal estaba abierto: 409 con alternativas | N | `ReservasClienteIntegracionTest.unaFranjaTomadaRespondeConflictoConAlternativas`<br>Newman «Reservar una franja tomada» | Pasa | E1, E2 | — |
| **F-35.c** La franja respeta las reglas | Bloques de media hora, entre 30 minutos y 8 horas, dentro del horario del edificio, sin fin de semana ni franjas ya empezadas | B | `ReglasReservaTest` (6 casos)<br>`ReservasClienteIntegracionTest.lasReglasDeLaFranjaSeValidan`, `.sinEspacioElPedidoEsInvalido`<br>Newman «Reservar sin espacio» | Pasa | E1, E2 | — |
| **F-35.d** Un doble clic no crea dos reservas | Mismo POST con la misma `Idempotency-Key`: misma respuesta y una sola reserva | B | `IdempotenciaIntegracionTest.elMismoPostConLaMismaClaveDevuelveLaPrimeraRespuestaYNoCreaOtraReserva`<br>Newman «Reservar otra vez con la misma clave (doble clic)» | Pasa | E1, E2 | — |
| **F-35.e** Un cliente no reserva dos espacios en el mismo horario | Segunda reserva superpuesta del mismo cliente: 409 `RESERVA_SUPERPUESTA` | N | `ReservasClienteIntegracionTest.elClienteNoPuedeEstarEnDosEspaciosALaVez` | Pasa | E1 | [B-16](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/16) |
| **F-43.a** Se cobra la tarjeta elegida | Reservar con una tarjeta propia: queda guardada en la reserva | P | `ReservasClienteIntegracionTest.conUnaTarjetaPropiaLaReservaLaGuarda` | Pasa | E1 | — |
| **F-43.b** No se puede usar una tarjeta ajena | Reservar con la tarjeta de otro cliente: error | N | `ReservasClienteIntegracionTest.conUnaTarjetaAjenaRespondeError` | Pasa | E1 | — |
| **F-43.c** Sin tarjeta elegida se cobra la principal; sin ninguna, se rechaza | Cobro sin tarjeta elegida y cobro de un cliente sin tarjetas | B | `CobroServiceImplTest.sinTarjetaElegidaCobraLaPrincipalDelCliente`, `.sinNingunaTarjetaRechazaSinLlamarALaPasarela` | Pasa | E1 | — |
| **F-43.d** El modal lista las tarjetas con la principal primero y avisa si no hay | Confirmar con dos tarjetas y con ninguna | P | — | Manual | E3 | — |
| **F-36.a** Mis reservas separa próximas e historial | Las pasadas y canceladas van al historial | P | `ReservasClienteIntegracionTest.lasReservasPasadasYCanceladasVanAlHistorial`<br>Newman «Mis reservas» | Pasa | E1, E2 | — |
| **F-36.b** Se cancela una pendiente o confirmada que no empezó | Cancelar una confirmada futura: 200 | P | `TransicionesReservaIntegracionTest.elClienteCancelaUnaReservaConfirmadaQueTodaviaNoEmpezo`<br>`ReglasReservaTest.soloSeCancelaLoActivoQueTodaviaNoEmpezo`<br>Newman «Cancelar la reserva» | Pasa | E1, E2 | — |
| **F-36.c** No se cancela una cancelada ni una terminada | Cancelar una ya cancelada o una terminada: 409 | N | `TransicionesReservaIntegracionTest.elClienteNoPuedeCancelarUnaReservaYaCancelada`, `.elClienteNoPuedeCancelarUnaReservaTerminada`<br>Newman «Cancelar una reserva ya cancelada» | Pasa | E1, E2 | — |
| **F-36.d** Un cliente no ve las reservas de otro | Detalle de una reserva ajena: 404 | N | `ReservasClienteIntegracionTest.laReservaDeOtroClienteNoSeVe`<br>Newman «Detalle de una reserva inexistente» | Pasa | E1, E2 | — |
| **B-10.a** Con el pago aprobado la reserva queda confirmada | Mensaje firmado en la cola: cobro guardado y reserva `CONFIRMADA` | P | `MensajeriaIntegracionTest.unPedidoFirmadoCobraYConfirmaLaReserva`<br>`CobroServiceImplTest.conElPagoAprobadoConfirmaLaReservaYGuardaElCobro` | Pasa | E1 | — |
| **B-10.b** Con el pago rechazado la reserva se cancela | Tarjeta terminada en 0002: reserva `CANCELADA` y franja libre | N | `MensajeriaIntegracionTest.conLaTarjetaRechazadaLaReservaSeCancela`<br>`CobroServiceImplTest.conElPagoRechazadoCancelaLaReservaParaLiberarLaFranja` | Pasa | E1 | — |
| **B-10.c** Una falla técnica se reintenta sin escribir nada | Tarjeta terminada en 0119: la falla se propaga para el reintento y no hay cobro parcial | N | `CobroServiceImplTest.unaFallaTecnicaDeLaPasarelaSePropagaSinEscribirNada`<br>`PasarelaPagosSimuladaTest.laTarjetaTerminadaEn0119SimulaUnaFallaTecnica` | Pasa | E1 | — |
| **B-10.d** La pasarela que no responde se corta a los 5 s, con circuit breaker | Tarjeta terminada en 0077: corte a los 5 s; con la mitad de las llamadas fallidas el circuito se abre y después se recupera | B | `PasarelaPagosResilienteTest` (6 casos)<br>`PasarelaPagosSimuladaTest.laTarjetaTerminadaEn0077TardaMasQueElLimiteYSeCortaAlInterrumpirla` | Pasa | E1 | — |
| **B-10.e** Una reserva que ya no está pendiente no se cobra | Mensaje de una reserva cancelada | B | `CobroServiceImplTest.unaReservaQueYaNoEstaPendienteNoSeCobra` | Pasa | E1 | — |
| **B-18.a** El barrido vuelve a pedir los cobros sin respuesta | Pendiente con más de 2 minutos sin cobro: se reencola y se cuenta el intento | B | `BarridoCobrosTest.reencolaUnCobroSinRespuestaYCuentaElIntento`, `.registrarLaSolicitudMarcaLaHora`<br>`ReservaRepositoryIntegracionTest.cobrosSinResolverTraeSoloLasPendientesViejasYSinCobro` | Pasa | E1 | [B-19](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/19) |
| **B-18.b** Agotados los intentos, la reserva se cancela | Tres intentos sin respuesta: reserva cancelada y franja libre | N | `BarridoCobrosTest.agotadosLosIntentosCancelaLaReservaYLiberaLaFranja` | Pasa | E1 | — |
| **B-18.c** Lo que se resolvió mientras tanto no se toca | Reserva cobrada entre la consulta y el reencolado | B | `BarridoCobrosTest.siMientrasTantoSeResolvioNoHaceNada` | Pasa | E1 | — |
| **B-13.a** Hasta 24 h antes se devuelve todo | Cancelar con más de un día de anticipación | P | `PoliticaCancelacionTest.conVeinticuatroHorasOMasDevuelveTodo`<br>`CancelacionesTest.elClienteConMasDeUnDiaRecuperaTodo` | Pasa | E1 | — |
| **B-13.b** Entre 24 h y 2 h se devuelve el 50 % | Cancelar el mismo día, con más de 2 h | B | `PoliticaCancelacionTest.entreVeinticuatroYDosHorasDevuelveLaMitad`, `.elReintegroSeRedondeaAlCentavo`<br>`CancelacionesTest.elClienteElMismoDiaRecuperaLaMitad` | Pasa | E1 | — |
| **B-13.c** Con menos de 2 h no se devuelve nada | Cancelar sobre la hora | B | `PoliticaCancelacionTest.conMenosDeDosHorasNoDevuelveNada`<br>`CancelacionesTest.sobreLaHoraNoHayReintegroPeroQuedaSaldado` | Pasa | E1 | — |
| **B-13.d** Si cancela un administrador se devuelve todo | Cancelación del administrador a menos de una hora | P | `PoliticaCancelacionTest.siCancelaUnAdministradorSiempreDevuelveTodo`<br>`CancelacionesTest.unAdministradorDevuelveTodoAunqueFalteMenosDeUnaHora` | Pasa | E1 | — |
| **B-13.e** Si el reintegro falla, la reserva no se cancela | Pasarela que rechaza el reintegro; pasarela sin respuesta: 503 con `Retry-After` y la reserva sigue activa | N | `CancelacionesTest.siLaPasarelaRechazaElReintegroLaReservaNoSeCancela`<br>`PasarelaLentaIntegracionTest.cancelarConLaPasarelaSinResponderDa503ConRetryAfterYLaReservaSigueActiva` | Pasa | E1 | — |
| **B-13.f** Sin cobro no se llama a la pasarela | Cancelar una reserva que nunca se cobró | B | `CancelacionesTest.sinCobroSeCancelaSinLlamarALaPasarela` | Pasa | E1 | — |
| **B-13.g** El cliente recibe un mail con lo que se devuelve | Mail de cancelación con el monto | P | — | Sin prueba | — | — |
| **B-20.a** La reserva confirmada se publica en el tópico y llega a cada suscriptor | Cobro aprobado: habilitación en el control de acceso y registro en la auditoría; confirmación manual del administrador: evento con origen `ADMINISTRADOR` | P | `MensajeriaIntegracionTest.unCobroAprobadoLlegaPorElTopicoAlControlDeAccesoYALaAuditoria`<br>`AdminReservasIntegracionTest.confirmarAManoPublicaReservaConfirmadaConOrigenAdministrador`<br>`ControlAccesoSimuladoTest` (2 casos) | Pasa | E1 | — |
| **B-20.b** Solo se publica al confirmar | Mover una reserva sin confirmarla no publica nada | N | `AdminReservasIntegracionTest.moverUnaReservaSinConfirmarlaNoPublicaReservaConfirmada` | Pasa | E1 | — |
| **B-20.c** El evento sale solo si la transacción confirma, y espera si el broker está caído | Rollback: no queda en el outbox; broker caído: los eventos esperan y salen en orden | B | `MensajeriaIntegracionTest.elEventoSoloQuedaEnElOutboxSiLaTransaccionConfirma`, `.conElBrokerCaidoLosEventosEsperanYSalenEnOrdenCuandoVuelve`, `.losEventosPublicadosViejosSePurgan` | Pasa | E1 | — |
| **B-20.d** Se publica también la reserva cancelada | Cancelar una reserva y verla en el tópico con la clave `reserva.cancelada` | P | `MensajeriaIntegracionTest.unaCancelacionTambienSalePorElOutboxYLlegaALaAuditoria` | Pasa | E1 | #121 (resuelto) |

## 5. Administración

Historias: [F-37](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/37) Agenda del día · [F-38](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/38) Listado de reservas · [F-39](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/39) Formulario de reserva del administrador · [F-40](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/40) Motivo obligatorio · [F-41](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/41) Bloqueos · [B-14](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/14) Bloqueos que cancelan reservas · [F-44](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/44) Filtro por empresa en cascada · [B-9](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/9) Paginado del listado de usuarios

| Criterio | Caso | Tipo | Prueba automatizada | Resultado | Evid. | Defecto |
|---|---|---|---|---|---|---|
| **F-37.a** La agenda del piso trae las reservas del día | Agenda de un piso en una fecha; sin piso: 400 | P | `AdminReservasIntegracionTest.laAgendaDelPisoTraeLasReservasDelDia`<br>Newman «Agenda del piso», «Agenda sin piso» | Pasa | E1, E2 | — |
| **F-37.b** El administrador abre cualquier espacio, también ocupados y bloqueados | Clic en un espacio ocupado en modo administrador | P | — | Manual | E3 | — |
| **F-38.a** El listado filtra por cliente, estado y ubicación | Filtros combinados; las finalizadas se distinguen de las activas | P | `AdminReservasIntegracionTest.elListadoFiltraPorClienteEstadoYUbicacion`, `.elListadoDistingueFinalizadasDeActivas`<br>Newman «Listado de reservas» | Pasa | E1, E2 | — |
| **F-38.b** El listado pagina | Página fuera de rango o tamaño enorme: no es un error del servidor | B | `ContratoApiIntegracionTest.unaPaginaEnormeNoEsUnErrorDelServidor` | Pasa | E1 | — |
| **F-38.c** El listado se ordena por hora de inicio | Varias reservas en desorden | P | `AdminReservasIntegracionTest.elListadoSeOrdenaPorHoraDeInicio` | Pasa | E1 | — |
| **F-39.a** El administrador reserva a nombre de un cliente | Alta para un cliente: 201 | P | `AdminReservasIntegracionTest.elAdminReservaANombreDeUnCliente`<br>Newman «Reservar para un cliente» | Pasa | E1, E2 | — |
| **F-39.b** No reserva a nombre de otro administrador | Alta para un administrador: 400 | N | `AdminReservasIntegracionTest.elAdminNoReservaANombreDeOtroAdmin`<br>Newman «Reservar para un administrador» | Pasa | E1, E2 | — |
| **F-39.c** Edita horario, espacio y cliente | Mover de horario y espacio; pasar a otro cliente | P | `AdminReservasIntegracionTest.elAdminMueveUnaReservaDeHorarioYDeEspacioYQuedaEnLaBitacora`, `.elAdminPasaUnaReservaAOtroCliente`<br>Newman «Mover y confirmar la reserva» | Pasa | E1, E2 | — |
| **F-39.d** Valida la superposición antes de guardar | Mover a una franja tomada: 409 | N | `AdminReservasIntegracionTest.moverAUnaFranjaTomadaRespondeConflicto` | Pasa | E1 | — |
| **F-39.e** Cambia el estado respetando las transiciones | Transiciones válidas aplicadas; una inválida: 409 | N | `TransicionesReservaIntegracionTest.elAdminHaceLasTransicionesValidas`, `.elAdminNoPuedeHacerUnaTransicionInvalida`<br>Newman «Volver la reserva a pendiente» | Pasa | E1, E2 | — |
| **F-39.f** Busca clientes por nombre o email | Búsqueda por email; búsqueda por nombre, parcial y sin distinguir mayúsculas; solo devuelve clientes | P | `AdminReservasIntegracionTest.laBusquedaDeClientesEncuentraPorEmail`, `.laBusquedaDeClientesEncuentraPorNombre`<br>Newman «Buscar clientes» | Pasa | E1, E2 | — |
| **F-40.a** El motivo es obligatorio al editar la reserva de otra persona | Editar sin motivo o sin cambios: 400. Borde del mínimo: 9 caracteres y un motivo en blanco se rechazan, 10 se aceptan | N | `AdminReservasIntegracionTest.editarSinCambiosOSinMotivoEsUnPedidoInvalido`, `.elMotivoNecesitaAlMenosDiezCaracteres` | Pasa | E1 | — |
| **F-40.b** El motivo queda en la bitácora de la reserva | Mover la reserva: entrada de bitácora con el motivo | P | `AdminReservasIntegracionTest.elAdminMueveUnaReservaDeHorarioYDeEspacioYQuedaEnLaBitacora` | Pasa | E1 | — |
| **F-40.c** El cliente ve el motivo en su detalle | Detalle de la reserva editada, visto por el cliente | P | `AdminReservasIntegracionTest.elClienteVeElMotivoDeLaEdicionEnSuDetalle` | Pasa | E1 | — |
| **F-41.a** El bloqueo ocupa el calendario igual que una reserva | Bloquear un espacio: aparece entre los vigentes y en el plano | P | `BloqueosIntegracionTest.bloqueaUnEspacioYApareceEntreLosVigentes`, `.elPlanoMuestraBloqueadoElEspacio`<br>Newman «Bloquear un espacio», «Bloqueos vigentes» | Pasa | E1, E2 | — |
| **F-41.b** «Piso entero» crea un bloqueo por espacio reservable | Bloquear un piso; borrar el grupo; borrar uno suelto deja el resto | P | `BloqueosIntegracionTest.bloquearUnPisoBloqueaCadaEspacioYSeBorraEnGrupo`, `.borrarUnBloqueoSueltoDejaElRestoDelGrupo`<br>Newman «Eliminar bloqueo» | Pasa | E1, E2 | — |
| **F-41.c** Las reglas del bloqueo se validan | Fuera de los bloques de 30 minutos: 400; sobre otro bloqueo: 409; bloqueo inexistente: 404 | N | `BloqueosIntegracionTest.lasReglasDelBloqueoSeValidan`, `.noSePisaUnBloqueoConOtro`<br>Newman «Bloquear fuera de los bloques de 30 minutos», «Eliminar bloqueo inexistente» | Pasa | E1, E2 | — |
| **B-14.a** Bloquear sobre reservas pide confirmación y después las cancela con reintegro total | Primer intento: 409 con la lista; con `cancelarReservas`: reservas canceladas | B | `BloqueosIntegracionTest.unBloqueoSobreReservasPideConfirmacionYDespuesLasCancela` | Pasa | E1 | — |
| **B-14.b** Cada cliente afectado recibe un mail | Mail de cancelación por bloqueo | P | — | Sin prueba | — | — |
| **F-44** Empresa, edificio y piso se eligen en cascada | Elegir una empresa y ver solo sus edificios y pisos | P | — | Manual | E3 | — |
| **B-9** El listado de usuarios pagina | `GET /api/usuarios` paginado | P | — | No implementado | — | — |

## 6. Medios de pago

Historias: [F-26](https://github.com/pedroberro/Front-Desarrollo-de-Aplicaciones-II/issues/26) Métodos de pago con tope de tres · [B-5](https://github.com/pedroberro/Back-Desarrollo-de-Aplicaciones-II/issues/5) Tarjeta principal y edición

| Criterio | Caso | Tipo | Prueba automatizada | Resultado | Evid. | Defecto |
|---|---|---|---|---|---|---|
| **F-26.a** Solo se guardan la marca y los últimos cuatro dígitos | Alta de una tarjeta: la respuesta y la base tienen solo los últimos cuatro | P | `MediosPagoIntegracionTest.laPrimeraTarjetaEsLaPrincipalYSoloGuardaLosUltimosCuatro`<br>Newman «Agregar tarjeta» | Pasa | E1, E2 | — |
| **F-26.b** Tope de tres tarjetas | Cuarta tarjeta: rechazo | B | `MediosPagoIntegracionTest.noSeAceptanTarjetasVencidasNiMasDeTres` | Pasa | E1 | — |
| **F-26.c** Al llegar a tres, la fila de agregar queda deshabilitada | Perfil con tres tarjetas | B | — | Manual | E3 | — |
| **B-5.a** Siempre hay una principal, que se reasigna al borrarla | Cambiar la principal y borrarla: la marca pasa a la que queda | P | `MediosPagoIntegracionTest.cambiarLaPrincipalYBorrarlaPasaLaMarcaALaQueQueda`<br>Newman «Marcar principal», «Eliminar tarjeta» | Pasa | E1, E2 | — |
| **B-5.b** Se editan el titular y el vencimiento | Editar tarjeta: 200; con un ETag viejo: 412 | P | `MediosPagoIntegracionTest.seActualizanElTitularYElVencimiento`<br>`EtagIntegracionTest.laTarjetaConUnEtagViejoResponde412`<br>Newman «Editar tarjeta», «Editar tarjeta con un ETag viejo» | Pasa | E1, E2 | — |
| **B-5.c** No se aceptan tarjetas vencidas ni números mal formados | Tarjeta vencida o número inválido: 400 | N | `MediosPagoIntegracionTest.noSeAceptanTarjetasVencidasNiMasDeTres`, `.unNumeroMalFormadoEsUnPedidoInvalido`<br>Newman «Agregar tarjeta vencida» | Pasa | E1, E2 | — |
| **B-5.d** La tarjeta de otro cliente no se puede tocar | Ver, editar o borrar una tarjeta ajena: 404 | N | `MediosPagoIntegracionTest.laTarjetaDeOtroNoSePuedeTocar`<br>`EtagIntegracionTest.laTarjetaDeOtroNoDaSuEtag`<br>Newman «Tarjeta inexistente», «Editar tarjeta inexistente», «Eliminar tarjeta inexistente» | Pasa | E1, E2 | — |

---

## 7. Resumen

**Por resultado** (127 criterios en total):

| Resultado | Criterios | % |
|---|---|---|
| Pasa | 104 | 81,9 % |
| Parcial | 6 | 4,7 % |
| Sin prueba | 6 | 4,7 % |
| Manual | 9 | 7,1 % |
| No implementado | 2 | 1,6 % |

**Por tipo de caso:** 55 positivos, 42 negativos y 30 de borde. Los negativos y de borde suman 72, el 56,7 %, por encima del 40 % que pide la cátedra (plan de pruebas, sección 9).

> Estos números salen del script de la sección 10, que recorre las tablas de arriba. Si se edita una fila, hay que volver a correrlo.

### Huecos a cubrir antes de la entrega final

Todos los criterios SEC tienen prueba automatizada, salvo dos: SEC-07.a, cuya evidencia manual está en [SeguridadBroker.md](SeguridadBroker.md), y SEC-10.b, que espera a gitleaks. Lo que sigue sin prueba, ordenado por peso en los criterios de la cátedra:

1. **Criterios de la interfaz (Manual, 9):** se cubren con los escenarios de punta a punta de Playwright, pendientes en el plan de pruebas (sección 9).
2. **SEC-10.b (secretos en el historial):** gitleaks en el pipeline (plan de pruebas, sección 9).
3. **Mails:** cancelación con reintegro (B-13.g) y cancelación por bloqueo (B-14.b). Se verifican con un `EmailService` simulado.
4. **Parciales de la API:** la contraseña corta o sin número (F-18.c), el email inexistente en el login (F-19.b) y el 429 de los endpoints públicos (B-3.a).
5. **Otros:** la geometría de las disposiciones en el CI (B-12).

## 8. Diferencias entre el tablero y el sistema

El texto de algunas historias quedó atrás de lo implementado. La matriz prueba lo implementado; estas diferencias se corrigen en el tablero:

| Historia | Dice | El sistema hace |
|---|---|---|
| F-11, F-20 | Refresh de 30 días | Refresh de 7 días (`app.jwt.refresh-dias`) |
| F-14 | Errores con `code`, `message` y `fields` | Problem Details (RFC 9457) con `code` y `fields` |
| F-18 | El registro devuelve el token e inicia sesión | La cuenta nace pendiente y se inicia sesión al confirmar el mail (B-1) |
| F-19 | Tope de intentos por IP y email | Tope de intentos por cuenta (B-15) |
| B-20 | Publicar reserva confirmada y cancelada | Se publican las dos por el outbox, con las claves `reserva.confirmada` y `reserva.cancelada` |
| B-26 a B-35 (SEC) | «Situación actual (no cumple)» y estado *To Do* | Los diez criterios están implementados |
| B-20, B-24 | Estado *To Do* | Implementados: el tópico existe y el CI corre los tests en cada push |
| B-21 | Servicio SOAP de facturación, *To Do* | Fuera del alcance ([Integracion.md](Integracion.md), sección 2) |
| B-22 | Pasarela como servicio REST aparte, *To Do* | Sigue siendo un componente interno detrás del puerto `PasarelaPagos` |

## 9. Pruebas que no rastrean a una historia

Estas pruebas cubren criterios de la cátedra (resiliencia y contrato de la API), no criterios de una historia:

| Prueba | Criterio que cubre |
|---|---|
| `ContratoApiIntegracionTest` (10 casos) | Contrato OpenAPI publicado, errores en Problem Details, entradas malformadas sin errores 5xx |
| `IdempotenciaIntegracionTest` (5 casos, además del de F-35.d), `EtagsTest`, `PrecondicionTest` | Idempotency-Key en las altas |
| `EtagIntegracionTest` (4 casos, además de los de B-5) | ETag e If-Match: dos administradores editando la misma reserva, el segundo recibe 412 |
| `TarifarioTest` (3 casos) | Monto del cobro según la tarifa por hora del tipo de espacio |
| `AdminReservasIntegracionTest.eliminarUnaReservaFuturaLaCancelaYLaBorra` | Baja de una reserva por el administrador |
| `GmailApiEmailServiceTest` (2 casos) | Integración con la API de Gmail |
| `PasarelaPagosSimuladaTest.apruebaUnaTarjetaComunConReferencia`, `.rechazaLaTarjetaDePruebaTerminadaEn0002`, `.elReintegroSeApruebaConReferenciaPropia` | Comportamiento del simulador de pagos |
| Schemathesis (job E2) | Todas las respuestas contra el contrato OpenAPI |

## 10. Cómo se recalcula el resumen

```bash
python3 - <<'EOF'
import re, collections
filas = [l for l in open("docs/MatrizTrazabilidad.md", encoding="utf-8") if l.startswith("| **")]
res = collections.Counter(next(r for r in ("Pasa", "Parcial", "Sin prueba", "Manual", "No implementado")
                               if l.split("|")[5].strip().startswith(r)) for l in filas)
tipos = collections.Counter(l.split("|")[3].strip() for l in filas)
print(len(filas), dict(res), dict(tipos))
EOF
```
