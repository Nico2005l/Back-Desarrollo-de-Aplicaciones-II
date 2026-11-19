# 0006. Errores en Problem Details

- **Estado:** Aceptada
- **Fecha:** 2026-11-06 (unificado el 2026-11-19)

## Contexto

El front necesita mostrar cada error en el campo que corresponde y reaccionar a casos puntuales, como una franja tomada o una cuenta sin confirmar. Los errores salen de varios lugares: los controladores, los filtros de seguridad, el límite de tasa y la Idempotency-Key. No todos usaban el mismo cuerpo.

## Opciones consideradas

1. **Formato propio:** `ErrorResponse`, con `message`, `code` y `errors`.
2. **Problem Details (RFC 9457)**, servido como `application/problem+json` y construido con el `ProblemDetail` de Spring.
3. **Solo el código HTTP**, sin cuerpo.

## Decisión

Opción 2, con un único constructor: `Problemas.de(status, mensaje, código)`. Lo usan `GlobalExceptionHandler` y los filtros que responden antes de llegar a un controlador.

El cuerpo lleva los campos del estándar: `type`, `title`, `status`, `detail` e `instance`. Además lleva, como extensiones, lo que lee el front:

- `message`;
- `code`;
- los datos propios de cada error: `errors`, `alternativas` o `reservas`.

Cuando hay un código, `type` toma la forma `urn:planta-libre:problema:<CÓDIGO>`.

## Consecuencias

- Se descartó el formato propio porque solo lo entiende este front. La primera versión convertía `ErrorResponse` a Problem Details al responder, lo que dejaba dos formatos en el código. Se reemplazó por el constructor único.
- Se descartó responder solo el código HTTP porque así el front no puede señalar el campo inválido ni ofrecer alternativas.
- Cualquier cliente puede leer los errores sin conocer la API. El front no tuvo que cambiar, porque `message` y `code` siguen en el cuerpo.
