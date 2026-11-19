package com.daii.backdaii.mensajeria;

import com.daii.backdaii.domain.evento.ReservaConfirmada;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Writer of the transactional outbox. A domain event that goes to the topic is written in the
 * same transaction as the change it announces (BEFORE_COMMIT): if that change rolls back, the
 * event disappears with it; if it commits, the event is there even if the broker is down or the
 * app dies right after. RelayEventos publishes it.
 */
@Component
@ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
@RequiredArgsConstructor
class OutboxEventos {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock reloj;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void alConfirmarReserva(ReservaConfirmada evento) {
        guardar(Colas.RESERVA_CONFIRMADA, evento, evento.reservaId());
    }

    void guardar(String routingKey, Object evento, Long reservaId) {
        String cuerpo;
        try {
            cuerpo = json.writeValueAsString(evento);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + evento.getClass().getSimpleName(), e);
        }
        jdbc.update("INSERT INTO eventos_salientes (mensaje_id, exchange, routing_key, tipo, cuerpo, reserva_id, creado_el)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), Colas.EXCHANGE_EVENTOS, routingKey, evento.getClass().getName(), cuerpo,
                reservaId, LocalDateTime.now(reloj));
    }
}
