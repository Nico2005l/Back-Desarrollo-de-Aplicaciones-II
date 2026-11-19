package com.daii.backdaii.mensajeria;

/** A row of the outbox (eventos_salientes) waiting to be published. */
public record EventoSaliente(long id, String mensajeId, String exchange, String routingKey, String tipo, String cuerpo,
                      int intentos) {
}
