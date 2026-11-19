# 0003. Cobro asincrónico por cola punto a punto

- **Estado:** Aceptada
- **Fecha:** 2026-10-15

## Contexto

El cobro de una reserva depende de una pasarela de pagos externa, que puede tardar o estar caída. Si el cobro corre dentro de `POST /api/reservas`, la persona espera a la pasarela y la transacción deja bloqueado el espacio todo ese tiempo. Cada cobro tiene que procesarse una sola vez.

## Opciones consideradas

1. **Cobro síncrono** dentro del alta de la reserva.
2. **Cobro asincrónico por un tópico** pub/sub.
3. **Cobro asincrónico por una cola punto a punto** (`planta-libre.cobros` en RabbitMQ), con consumidores que compiten por los mensajes.

## Decisión

Opción 3. El alta guarda la reserva como `PENDIENTE` y responde `201` en milisegundos. Después del commit se publica `CobroSolicitado`, firmado con HMAC.

`ConsumidorCobros` cobra dentro de una sola transacción, que:

- descarta el mensaje si su id ya se procesó;
- bloquea la fila de la reserva.

Si el cobro tiene una falla técnica, se reintenta 3 veces. Después de eso, el mensaje va a la DLQ, que vigila `MonitorDlq`.

## Consecuencias

- Se descartó el cobro síncrono porque ata el alta a la disponibilidad de la pasarela y deja el espacio bloqueado mientras se cobra.
- Se descartó el tópico porque entrega una copia a cada suscriptor: con dos instancias, la reserva se cobraría dos veces.
- Una pasarela caída no hace perder reservas: el mensaje espera y se reintenta. Los cobros que quedan sin resolver los vuelve a pedir `BarridoCobros`.
- La reserva pasa por el estado intermedio `PENDIENTE`. El front lo muestra y lo actualiza cuando llega el resultado del cobro.
- El detalle está en [Integracion.md, sección 3](../Integracion.md#3-cobro-de-una-reserva-cola-punto-a-punto).
