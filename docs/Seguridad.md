# Informe de seguridad — OWASP ZAP

## Resumen ejecutivo

Se realizaron dos análisis baseline (frontend y API) y un escaneo activo autenticado de la API mediante `zap-api-scan.py` y OpenAPI. El frontend conserva tres alertas Medium pendientes: falta de CSP, protección anti-clickjacking y SRI para una hoja de estilos externa. En el backend se incorporó `Cross-Origin-Resource-Policy: same-origin`; la prueba de integración correspondiente pasó en sus tres casos y el retest no volvió a listar la alerta CORP.

El retest de API importó 48 rutas y analizó 142 URLs, sin nuevos `FAIL` ni `WARN`. Sin embargo, el informe registra 163 respuestas 401 y un 99 % de respuestas 4xx. Aunque el análisis se configuró con JWT, muchas solicitudes no fueron autorizadas; por eso no se puede afirmar que todas las operaciones protegidas se probaron correctamente.

## Alcance y resultados

Los resultados corresponden a `zap-frontend.html`, `zap-api-baseline.html`, `zap-api-retest2.html` y `zap-api-retest2.json`.

| Análisis | Alcance | Alertas registradas por severidad ZAP |
|---|---|---|
| Baseline frontend | `http://host.docker.internal:8088` | 0 High, 3 Medium, 4 Low, 3 Informational |
| Baseline inicial de API | `http://host.docker.internal:8080` | 0 High, 0 Medium, 1 Low, 1 Informational |
| API activa, retest 2 | 48 rutas OpenAPI; 142 URLs analizadas | 0 High, 0 Medium, 0 Low, 3 Informational; 0 nuevos `FAIL` y 0 nuevos `WARN` |

## Hallazgos del frontend

El baseline reportó tres alertas Medium. La configuración revisada de Nginx no define CSP ni `X-Frame-Options`, y el HTML mantiene el enlace externo a Google Fonts sin `integrity`. Por ello, las tres quedan pendientes en el frontend.

### Content Security Policy (CSP) Header Not Set — Medium

ZAP observó la ausencia de CSP en tres respuestas: página principal, `robots.txt` y `sitemap.xml`. Una CSP limita los recursos que el navegador puede cargar y ayuda a reducir el impacto de ciertos ataques XSS e inyección. El informe evidencia la falta de esa defensa, no una explotación. **Estado: pendiente.** La política CSP de `SecurityConfig` aplica a la API, no a las respuestas estáticas de Nginx.

### Missing Anti-clickjacking Header — Medium

En la página principal ZAP no encontró `X-Frame-Options` ni CSP `frame-ancestors`. Sin una restricción de enmarcado, un sitio externo podría mostrar la aplicación dentro de un frame y engañar al usuario para que interactúe con ella. **Estado: pendiente** en la configuración del frontend.

### Sub Resource Integrity Attribute Missing — Medium

El informe identificó tres instancias de la hoja de estilos de Google Fonts (`fonts.googleapis.com`) sin atributo `integrity`. SRI permite al navegador comprobar el contenido de recursos externos. El hallazgo no demuestra que el proveedor esté comprometido ni que se haya servido contenido malicioso. El enlace en `index.html` sigue sin `integrity`. **Estado: pendiente.**

El mismo baseline registró cuatro tipos de alerta **Low**: COEP, COOP, CORP y Permissions Policy ausentes o inválidos. También registró tres alertas **Informational**: comentario sospechoso, detección de aplicación web moderna y contenido almacenable/cacheable. Se mantienen con la severidad asignada por ZAP; estos avisos no prueban por sí solos una vulnerabilidad explotable ni exposición de información sensible.

## API: resultados y corrección

### Baseline inicial y límite de cobertura

El baseline inicial encontró una alerta **Low** de CORP en `GET /api/health` y una **Informational** de contenido no almacenable (cinco instancias). No registró alertas High ni Medium.

Las rutas protegidas respondieron HTTP 401 a las solicitudes sin autenticación. El informe contabiliza 71 % de respuestas 4xx. En consecuencia, el baseline no evaluó funcionalmente las operaciones que requieren JWT y no permite sacar conclusiones sobre ellas.

### Cabecera CORP implementada y verificada

En `SecurityConfig.java` se agregó a la configuración centralizada de encabezados de seguridad:

```http
Cross-Origin-Resource-Policy: same-origin
```

`CabecerasSeguridadIntegracionTest.java` comprueba el valor exacto en tres respuestas: `/api/health` (200), una ruta protegida sin autenticar (401) y una ruta inexistente (401). El resultado Surefire disponible registra **3 tests ejecutados, 0 fallos, 0 errores y 0 omitidos**. En el retest de API ya no aparece la alerta CORP registrada en el baseline inicial.

Esta corrección es del backend. No corrige las alertas Medium del frontend, cuyas respuestas estáticas las sirve Nginx.

### Escaneo activo autenticado con OpenAPI

El retest 2 importó 48 rutas del contrato OpenAPI y analizó 142 URLs. Reportó **0 nuevos `FAIL` y 0 nuevos `WARN`**. El informe lista tres hallazgos `Informational`:

- **A Client Error response code was returned by the server:** 163 instancias asociadas a respuestas 401. ZAP lo clasifica como Informational, no como vulnerabilidad. Las respuestas indican que esas solicitudes no fueron autorizadas.
- **Authentication Request Identified:** una solicitud de login reconocida por ZAP; es un mensaje informativo.
- **Non-Storable Content:** cinco instancias; observación de caché que no demuestra por sí misma que se almacenara información sensible.

El informe presenta además el insight **Percentage of responses with status code 4xx: 99 %**, con el indicador “Exceeded High”. Es una estadística que superó un umbral de insight; no es una alerta de severidad High ni un `WARN` nuevo. El JWT se configuró para el análisis, pero los 401 muestran que muchas solicitudes no obtuvieron autorización efectiva. Las 48 rutas importadas y 142 URLs analizadas describen el alcance generado, no prueban que cada operación protegida haya sido ejercitada con éxito.

## Conclusión

La consigna queda cubierta en cuanto a la ejecución y documentación de los tres análisis, y la alerta Low de CORP del backend fue atendida y verificada mediante tres tests aprobados y el retest. El baseline de API tuvo cobertura limitada por falta de autenticación. El análisis activo se configuró con JWT y OpenAPI, pero el 99 % de respuestas 4xx —incluidas 163 respuestas 401— impide afirmar que las rutas protegidas se probaron correctamente en su totalidad.

En el frontend quedan pendientes las tres alertas Medium: CSP, protección anti-clickjacking y SRI. Por ello, el trabajo no permite declarar resueltos todos los hallazgos ni afirmar una cobertura autenticada completa de la API.
