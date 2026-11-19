# 0007. Límites de intentos guardados en PostgreSQL

- **Estado:** Aceptada
- **Fecha:** 2026-10-16

## Contexto

Dos controles cuentan intentos: el bloqueo por logins fallidos (SEC-04) y el límite de tasa (SEC-06). Los dos tienen que valer para todas las instancias de la API y sobrevivir a un reinicio. Si no, un atacante puede repartir los intentos entre instancias o esperar a que la API se reinicie.

## Opciones consideradas

1. **Contadores en memoria** en cada instancia, por ejemplo con Bucket4j local.
2. **Redis**, con contadores que vencen solos.
3. **Una tabla de PostgreSQL** (`limites_intentos`), que cada intento actualiza con un upsert atómico.

## Decisión

Opción 3, detrás del puerto `AlmacenIntentos`, que implementa `AlmacenIntentosEnBase`. Cada intento es un solo `INSERT ... ON CONFLICT DO UPDATE`, que suma uno al contador o reinicia la ventana si ya venció. Las ventanas se miden con el reloj de la base, así que todas las instancias coinciden aunque sus relojes difieran.

## Consecuencias

- Se descartaron los contadores en memoria porque no se comparten entre instancias y se pierden al reiniciar.
- Se descartó Redis porque suma un servicio más que operar y asegurar, y PostgreSQL ya resuelve la atomicidad a este volumen.
- Cada pedido hace una escritura más en la base. Si el tráfico crece, alcanza con implementar el puerto con Redis, sin tocar los filtros.
- Lo prueba `LimiteTasaIntegracionTest.dosInstanciasCompartenElContadorYUnReinicioNoLoBorra`.
