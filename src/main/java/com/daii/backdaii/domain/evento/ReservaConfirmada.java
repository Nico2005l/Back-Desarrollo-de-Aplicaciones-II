package com.daii.backdaii.domain.evento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A booking became CONFIRMADA: its charge was approved, or an administrator confirmed it by hand.
 * Published on the topic planta-libre.eventos with the key reserva.confirmada; whoever cares
 * (mail, building access control, audit) subscribes without the publisher knowing them.
 *
 * @param monto    charged amount; null when an administrator confirmed it
 * @param ultimos4 last four digits of the charged card; null when an administrator confirmed it
 */
public record ReservaConfirmada(
        Long reservaId,
        String codigoReserva,
        String descripcionReserva,
        String codigoEspacio,
        LocalDateTime inicio,
        LocalDateTime fin,
        String email,
        String nombre,
        Origen origen,
        BigDecimal monto,
        String ultimos4) {

    public enum Origen {
        /** The asynchronous charge was approved. */
        COBRO,
        /** An administrator moved it from PENDIENTE to CONFIRMADA. */
        ADMINISTRADOR
    }
}
