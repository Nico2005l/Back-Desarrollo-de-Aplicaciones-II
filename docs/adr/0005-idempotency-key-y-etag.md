# 0005. Idempotency-Key y ETag en la API

- **Estado:** Aceptada
- **Fecha:** 2026-11-19

## Contexto

Un doble clic o un corte de red durante `POST /api/reservas` puede crear dos reservas y cobrar dos veces. Hasta esta decisión, el único paso idempotente era el consumidor de cobros: la API no protegía las altas. Además, si dos administradores editaban la misma reserva a la vez, uno pisaba los cambios del otro sin enterarse.

## Opciones consideradas

1. **Deduplicar en el front**, deshabilitando el botón mientras el pedido está en curso.
2. **Idempotency-Key guardada en memoria**, en cada instancia.
3. **Idempotency-Key guardada en PostgreSQL** (tabla `claves_idempotencia`) para las altas, más **ETag con `If-Match`** para las modificaciones.

## Decisión

Opción 3.

En las altas marcadas con `@Idempotente`, la API acepta la cabecera `Idempotency-Key`:

- Si el pedido se repite con la misma clave, recibe la misma respuesta que la primera vez, con `Idempotent-Replayed: true`.
- Si la clave se reutiliza con otro cuerpo, la respuesta es `422`.

En las modificaciones, los recursos devuelven `ETag`, que es su versión. Un trigger de la base la incrementa en cada cambio. Si un `PUT` o un `PATCH` llega con un `If-Match` vencido, la respuesta es `412` y no se aplica nada.

## Consecuencias

- Se descartó deduplicar en el front porque no cubre los reintentos por red ni a otros clientes de la API, que es pública.
- Se descartó guardar la clave en memoria porque, con varias instancias, el reintento puede llegar a otra instancia, y además la clave se pierde al reiniciar.
- Guardar la clave en la base agrega una escritura por cada alta. Las claves se borran a las 24 horas.
- `If-Match` es opcional: sin esa cabecera, el cambio se aplica igual, así que los clientes que no la mandan siguen funcionando.
- El detalle está en [Integracion.md, secciones 5.2 y 5.3](../Integracion.md#52-idempotency-key).
