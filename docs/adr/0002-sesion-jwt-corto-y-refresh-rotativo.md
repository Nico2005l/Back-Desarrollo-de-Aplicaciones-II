# 0002. Sesión con JWT corto y refresh rotativo

- **Estado:** Aceptada
- **Fecha:** 2026-08-28

## Contexto

La API no guarda estado de sesión, y tiene que poder reiniciarse o correr en varias instancias sin perder sesiones. A la vez, cerrar sesión y cambiar la contraseña tienen que invalidar la sesión de verdad (SEC-02), y un token robado debería servir poco tiempo.

## Opciones consideradas

1. **JWT de larga duración** (días), guardado en `localStorage`.
2. **Sesión en el servidor**, con cookie de sesión.
3. **JWT de acceso de 15 minutos más refresh token rotativo de 7 días.** El refresh viaja en una cookie `HttpOnly` y se guarda en la base como hash, agrupado por familia.

## Decisión

Opción 3:

- Cada renovación entrega un refresh nuevo y da por gastado el anterior.
- Si alguien reutiliza un refresh gastado, se revoca toda la familia, porque eso indica un robo.
- Un token emitido antes de un cambio de contraseña deja de valer.

## Consecuencias

- Se descartó el JWT largo porque no se puede revocar: un token robado sirve por días. El token de acceso sigue guardado en `localStorage`, así que un XSS puede leerlo, pero vence a los 15 minutos. El refresh, que es el que dura, va en una cookie `HttpOnly`, y desde JavaScript no se puede leer.
- Se descartó la sesión en el servidor porque obliga a compartir las sesiones entre instancias, y la revocación que se buscaba ya la da el refresh.
- Validar el token de acceso no consulta la base: solo la renovación lo hace, una vez cada 15 minutos.
- El front tiene que renovar el token sin que la persona lo note cuando recibe un `401`.
