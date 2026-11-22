# Dependencias y vulnerabilidades — defecto #120

Actualización de Spring Boot a la última 3.5.x, verificación de build en verde y escaneo de
vulnerabilidades con OSV-Scanner.

| Dato | Valor |
|---|---|
| Fecha del escaneo | 2026-11-22 |
| Herramienta | OSV-Scanner `2.7.1` (osv-scalibr `0.5.3`), base de datos OSV/GHSA |
| Módulo escaneado | `pom.xml` de `back-daii` |
| Build de verificación | `mvn -B verify` |

---

## 1. Resumen

| Métrica | Antes (HEAD) | Después |
|---|---:|---:|
| Spring Boot | 3.3.3 | **3.5.16** |
| Paquetes con vulnerabilidades | 25 | **1** |
| Vulnerabilidades totales | **117** | **2** |
| Críticas | 11 | **2** |
| Altas | 41 | **0** |
| Moderadas | 48 | **0** |
| Bajas | 17 | **0** |

Las dos vulnerabilidades que quedan son del mismo paquete (`spring-webmvc` 6.2.19) y **no tienen
parche publicado en la línea 6.2.x**. El análisis de explotabilidad está en la
[sección 6](#6-vulnerabilidades-que-quedan).

---

## 2. Cómo reproducir el escaneo

```powershell
# Descargar el binario (una sola vez)
$osv = Join-Path $env:TEMP "osv\osv-scanner.exe"

# Escanear el pom.xml del proyecto
& $osv scan source -r . --format json | Out-File -Encoding utf8 osv-report.json
& $osv scan source -r .                     # salida legible + código de salida
```

El comando devuelve código de salida `1` cuando encuentra vulnerabilidades, así que un job de CI
debe leer el JSON o usar `--fail-on-vuln=false` si se quiere solo el informe.

Para comparar contra el estado anterior se puede escanear el `pom.xml` de un commit dado sin tocar
el working tree:

```powershell
$dir = Join-Path $env:TEMP "osv\baseline"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
git show HEAD~1:pom.xml | Set-Content -Encoding utf8 (Join-Path $dir "pom.xml")
& $osv scan source -r $dir --format json
```

---

## 3. Versiones antes / después

### 3.1 Plataforma

| Componente | Antes | Después |
|---|---|---|
| Spring Boot (parent) | 3.3.3 | **3.5.16** |
| Spring Framework | 6.1.12 | **6.2.19** |
| Spring Security | 6.3.3 | **6.5.11** |
| Spring AMQP | 3.1.7 | **3.2.12** |
| Spring Data Commons | 3.3.3 | 3.5.x (gestionado por el parent) |
| Hibernate ORM | 6.5.2.Final | **6.6.53.Final** |
| Tomcat embebido | 10.1.28 | **10.1.62** |
| Jackson (BOM) | 2.17.2 | **2.21.8** |
| Logback | 1.5.7 | **1.5.34** |
| Log4j API | 2.23.1 | **2.25.5** |
| Flyway | 10.10.0 | **11.7.2** |
| HikariCP | 5.1.0 | **6.3.3** |
| Cliente RabbitMQ (`amqp-client`) | 5.21.0 | **5.38.0** |
| Netty | 4.1.x (vía BOM 3.3.3) | **4.1.139.Final** |
| PostgreSQL JDBC | 42.7.3 | **42.7.14** |
| `commons-lang3` | 3.14.0 | **3.18.0** |
| `commons-compress` (solo test) | 1.24.0 | **1.28.0** |
| Lombok | 1.18.38 (fijado en el pom) | 1.18.46 (heredado del parent) |

### 3.2 Plugins de build

| Plugin | Antes | Después |
|---|---|---|
| `springdoc-openapi-starter-webmvc-api` | 2.6.0 | **2.8.17** |
| `resilience4j` | 2.2.0 | **2.4.0** |
| `jacoco-maven-plugin` | 0.8.12 | **0.8.13** |
| `pitest-maven` | 1.17.0 | **1.19.1** |
| `pitest-junit5-plugin` | 1.2.1 | **1.2.3** |
| `spotbugs-maven-plugin` | 4.8.6.6 | **4.9.3.0** |
| `testcontainers.version` | 1.21.4 (fijado) | 1.21.4 (fijado, coincide con el parent) |

Las versiones de `jackson-bom`, `tomcat`, `postgresql`, `commons-lang3`, `log4j2`,
`rabbit-amqp-client` y `netty` se fijan como *properties* en el `pom.xml` porque Spring Boot 3.5.16
todavía gestiona versiones anteriores a las que corrigen los avisos. Son las versiones **mínimas que
corrigen**, salvo `netty`, `amqp-client` y `jackson-bom`, donde se tomó la última revisión de la
misma línea. `commons-compress` no está gestionado por el parent, por eso se declara en
`<dependencyManagement>` (solo entra en alcance de test a través de Testcontainers).

---

## 4. Cambio de código obligatorio que introdujo el upgrade

El upgrade no es solo de versiones: **Spring AMQP 3.2 dejó de deserializar paquetes arbitrarios**.
Con la configuración anterior, los 5 tests de `MensajeriaIntegracionTest` que consumen eventos del
broker fallaban con:

```
java.lang.IllegalArgumentException: The class 'com.daii.backdaii.domain.evento.ReservaConfirmada'
is not in the trusted packages: [java.util, java.lang]. If you believe this class is safe to
deserialize, please provide its name.
```

La corrección está en
[`MensajeriaConfig.java`](../src/main/java/com/daii/backdaii/mensajeria/MensajeriaConfig.java):
el `Jackson2JsonMessageConverter` ahora usa un `DefaultJackson2JavaTypeMapper` con lista blanca
explícita de paquetes.

```java
DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
typeMapper.setTrustedPackages(
        "java.util", "java.lang", "com.daii.backdaii.mensajeria", "com.daii.backdaii.domain.evento");
conversor.setJavaTypeMapper(typeMapper);
```

La lista es explícita y **no incluye `*`**: habilitar todos los paquetes reabriría la
vulnerabilidad de deserialización arbitraria que el cambio de Spring AMQP vino a cerrar.

---

## 5. Vulnerabilidades eliminadas

Conteo por paquete en el estado anterior (117 en total):

| Paquete (versión anterior) | Avisos | Estado tras el upgrade |
|---|---:|---|
| `tomcat-embed-core` 10.1.28 | 37 | 0 — Tomcat 10.1.62 |
| `spring-webmvc` 6.1.12 | 15 | 2 — ver sección 6 |
| `jackson-databind` 2.17.2 | 12 | 0 — Jackson 2.21.8 |
| `amqp-client` 5.21.0 | 10 | 0 — `amqp-client` 5.38.0 |
| `logback-core` 1.5.7 | 6 | 0 — Logback 1.5.34 |
| `spring-security-web` 6.3.3 | 4 | 0 — Spring Security 6.5.11 |
| `jackson-core` 2.17.2 | 4 | 0 |
| `spring-data-commons` 3.3.3 | 3 | 0 |
| `spring-expression` 6.1.12 | 3 | 0 |
| `spring-security-core` 6.3.3 | 2 | 0 |
| `spring-web` 6.1.12 | 2 | 0 |
| `spring-context` 6.1.12 | 2 | 0 |
| `spring-core` 6.1.12 | 2 | 0 |
| `commons-compress` 1.24.0 | 2 | 0 — fijado en 1.28.0 |
| `spring-boot` 3.3.3 | 2 | 0 |
| `spring-amqp` 3.1.7 | 2 | 0 |
| `log4j-api` 2.23.1 | 1 | 0 — Log4j 2.25.5 |
| `commons-lang3` 3.14.0 | 1 | 0 — `commons-lang3` 3.18.0 |
| `json-smart` 2.5.1 | 1 | 0 |
| `xmlunit-core` 2.9.1 | 1 | 0 |
| `spring-retry` 2.0.8 | 1 | 0 |
| `spring-boot-autoconfigure` 3.3.3 | 1 | 0 |
| `spring-security-crypto` 6.3.3 | 1 | 0 |
| `assertj-core` 3.25.3 | 1 | 0 |
| `postgresql` 42.7.3 | 1 | 0 — driver 42.7.14 |
| **Total** | **117** | **2** |

También se corrigieron 3 avisos de Netty (`netty-handler` y `netty-codec` 4.1.135.Final) que
aparecieron al pasar a 3.5.16 por el BOM nuevo; se resolvieron subiendo a 4.1.139.Final.

---

## 6. Vulnerabilidades que quedan

Quedan **2 avisos, ambos críticos, ambos en `org.springframework:spring-webmvc` 6.2.19**.

| Aviso | Severidad | Componente afectado | Primera versión con fix |
|---|---|---|---|
| GHSA-j9f9-w8pj-32f8 | Crítica (9.8) | Renderizado de *Server-Sent Events* con fragmentos | 7.0.9 |
| GHSA-pc63-qcmh-9cmg | Crítica (9.8) | Limitación de rutas en `XsltView` | 7.0.9 |

### 6.1 Por qué no se actualizan

Los dos avisos **solo tienen versión corregida en la línea 7.0.x** (7.0.9). No existe ningún
`fixed` event para 6.2.x, que es la línea que usa Spring Boot 3.5.x. Actualizar obligaría a mover el
proyecto a Spring Framework 7, y eso implica Spring Boot 4 (y, en cascada, Spring Security 7,
Hibernate 7, Jakarta EE 11). Es un cambio de *major* con reescritura de configuración de seguridad y
de capa de persistencia, fuera del alcance de este defecto, que pide explícitamente quedarse en la
última 3.5.x.

Verificación de que no hay fix en 6.2.x:

```powershell
$osv = Join-Path $env:TEMP "osv\osv-scanner.exe"
& $osv scan source -r . --format json | ConvertFrom-Json |
    Select-Object -ExpandProperty results |
    ForEach-Object { $_.packages.vulnerabilities } |
    Where-Object { $_.id -in @('GHSA-j9f9-w8pj-32f8','GHSA-pc63-qcmh-9cmg') } |
    ForEach-Object { $_.affected.ranges.events | Where-Object { $_.fixed } }
# devuelve únicamente 7.0.9
```

### 6.2 Análisis de explotabilidad en esta aplicación

Ninguno de los dos avisos es alcanzable con el código actual:

| Aviso | Requisito para explotarlo | ¿Aplica a `back-daii`? | Evidencia |
|---|---|---|---|
| GHSA-j9f9-w8pj-32f8 (SSE + fragmentos) | Exponer endpoints que devuelvan `text/event-stream` mediante `SseEmitter` / `StreamingResponseBody` y renderizar fragmentos | **No** | Búsqueda en `src/`: 0 coincidencias de `SseEmitter`, `TEXT_EVENT_STREAM`, `text/event-stream`. La API es 100 % JSON síncrona |
| GHSA-pc63-qcmh-9cmg (`XsltView`) | Usar `XsltView` o un `ViewResolver` con plantillas XSLT | **No** | Búsqueda en `src/`: 0 coincidencias de `XsltView`, `TransformerFactory`, `xslt`. `src/main/resources` solo contiene `db/` (migraciones Flyway) y `seguridad/`; no existe `templates/` |

Además, los 10 controladores del proyecto son `@RestController` (JSON) o `@ControllerAdvice`
(`GlobalExceptionHandler`); no hay ninguno que devuelva una vista.

**Riesgo residual aceptado:** el vector queda fuera de alcance por diseño, no por suerte. Si en el
futuro se agregara *streaming* (SSE) o renderizado XSLT, estos dos avisos pasan a ser explotables y
el proyecto queda bloqueado hasta migrar a Spring Boot 4.

### 6.3 Plan de seguimiento

1. Revisar `spring-webmvc` en cada release de Spring Boot 3.5.x: si Spring publica la corrección en
   la línea 6.2.x, basta con subir el parent.
2. Si el proyecto necesita SSE o XSLT, abrir un *spike* de migración a Spring Boot 4 antes de
   implementar la funcionalidad.
3. Re-ejecutar OSV-Scanner en cada PR que toque `pom.xml`.

---

## 7. Verificación del build

`mvn -B verify` en verde con las versiones nuevas:

| Gate | Resultado |
|---|---|
| Compilación | OK (191 archivos) |
| Tests | **258 ejecutados, 0 fallos, 0 errores, 0 omitidos** |
| Cobertura JaCoCo | `All coverage checks have been met` (80 % líneas / 70 % ramas en la capa de negocio) |
| Mutación PIT | 167 mutaciones generadas, **127 matadas (76 %)**, *test strength* 93 % (umbral 60 %) |
| SpotBugs | **0 bugs** |
| Empaquetado | `target/back-daii-0.0.1-SNAPSHOT.jar` |

Los tests de integración corren contra Testcontainers (PostgreSQL 16 y RabbitMQ 3.13 con TLS); sin
Docker se omiten, así que el escaneo y el `verify` deben ejecutarse en una máquina o runner con
Docker disponible.

Concretamente, el bloque que rompió el upgrade y ahora pasa completo:

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
       -- in com.daii.backdaii.integracion.MensajeriaIntegracionTest
```

---

## 8. Riesgo residual

| Riesgo | Severidad | Estado |
|---|---|---|
| 2 avisos críticos de `spring-webmvc` 6.2.19 sin parche en 6.2.x | Crítica (teórica) / nula (real) | **Aceptado y documentado** — vectores no presentes en el código (sección 6.2) |
| Dependencia de una versión de `amqp-client` (5.38.0) más nueva que la que prueba Spring AMQP 3.2.12 | Baja | Mitigado: los 10 tests de mensajería contra RabbitMQ real pasan |
| `netty` fijado por fuera del BOM de Boot (4.1.139.Final) | Baja | Mitigado: `mvn verify` completo en verde |
| Deuda de migración a Spring Boot 4 | Media (largo plazo) | Registrado en el plan de seguimiento (sección 6.3) |
