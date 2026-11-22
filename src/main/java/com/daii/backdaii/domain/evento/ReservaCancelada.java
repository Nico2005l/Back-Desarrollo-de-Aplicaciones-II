package com.daii.backdaii.domain.evento;

import java.math.BigDecimal;

/**
 * A booking stopped taking its slot: cancelled by the customer or an administrator, deleted, or
 * swept by a block. Published on the topic planta-libre.eventos with the key reserva.cancelada,
 * through the outbox like reserva.confirmada, so the audit hears it even if the broker is down at
 * the moment of the cancellation. The customer's mail goes out in the same transaction, after
 * commit, refund included.
 *
 * @param cobrado     what had been charged; null when nothing was (pending, or charging is off)
 * @param reintegrado what goes back to the card; zero when nothing does
 */
public record ReservaCancelada(
        Long reservaId,
        String codigoReserva,
        String descripcionReserva,
        String email,
        String nombre,
        Motivo motivo,
        String detalle,
        BigDecimal cobrado,
        BigDecimal reintegrado,
        int porcentaje) {

    public enum Motivo { CLIENTE, ADMINISTRADOR, ELIMINADA, BLOQUEO }
}
