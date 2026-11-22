# 0004. Outbox transaccional y confirmaciones del publicador

- **Estado:** Aceptada
- **Fecha:** 2026-11-19

## Contexto

Cuando una reserva se confirma, se publica `reserva.confirmada` en el tópico `planta-libre.eventos`. Lo consumen el aviso por mail, el control de acceso y la auditoría.

Hasta esta decisión, el mensaje se publicaba después del commit. Si el proceso se caía entre el commit y el envío, o si el broker estaba caído, el evento se perdía hasta que pasara el barrido. Para el tercer checkpoint, el broker tiene que poder apagarse sin que se pierdan mensajes.

## Opciones consideradas

1. **Publicar después del commit**, como antes, y confiar en el barrido.
2. **Solo confirmaciones del publicador** (publisher confirms). Se sabe si el broker recibió el mensaje, pero si el proceso se cae antes de enviarlo, el mensaje se pierde igual.
3. **Outbox transaccional con confirmaciones del publicador.** El evento se escribe en la tabla `eventos_salientes` en la misma transacción que la confirmación. Un relay lo publica, y lo marca como enviado solo cuando el broker lo confirma.
4. **Captura de cambios de la base**, con Debezium sobre el WAL de PostgreSQL.

## Decisión

Opción 3:

- `OutboxEventos` escribe el evento dentro de la transacción del cobro.
- `RelayEventos` corre cada 2 segundos, publica los eventos pendientes en orden y espera la confirmación del broker durante 5 segundos como máximo.
- Mientras el broker no confirma, el evento sigue pendiente y registra `intentos` y `ultimo_error`.

## Consecuencias

- El evento existe si y solo si la confirmación hizo commit, y no se pierde con el broker caído. Lo prueba `MensajeriaIntegracionTest.conElBrokerCaidoLosEventosEsperanYSalenEnOrdenCuandoVuelve`.
- La entrega es *al menos una vez*. Si se pierde la confirmación del broker, el evento sale de nuevo con el mismo id, y los suscriptores descartan la copia gracias a `mensajes_procesados`.
- Entre la confirmación y la publicación pasan hasta 2 segundos. Se suma una tabla, que se purga a los 7 días.
- El mismo mecanismo publica `reserva.cancelada` desde `Cancelaciones` (defecto #121): la tabla y el relay no dependen del tipo de evento.
- Se descartó la opción 4 porque suma un componente más que operar (Kafka Connect o Debezium Server) sin ninguna ganancia a esta escala.
