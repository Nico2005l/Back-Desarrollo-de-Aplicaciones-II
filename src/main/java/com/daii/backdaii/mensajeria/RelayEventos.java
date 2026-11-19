package com.daii.backdaii.mensajeria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Relay of the transactional outbox: takes the pending events in order and publishes each one,
 * marking it as sent only after the broker confirmed it. With the broker down the pass stops at
 * the first failure and the events stay pending for the next pass: none is lost, and they keep
 * their order. FOR UPDATE SKIP LOCKED lets several instances relay without sending one twice.
 *
 * If the broker confirmed but the mark did not commit, the event goes out again with the same
 * message id and the subscribers discard the copy: delivery is at least once, processing once.
 */
public class RelayEventos {

    private static final Logger log = LoggerFactory.getLogger(RelayEventos.class);
    static final int POR_PASADA = 50;
    private static final int LARGO_ERROR = 300;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final CanalEventos canal;
    private final Clock reloj;

    public RelayEventos(JdbcTemplate jdbc, TransactionTemplate transaccion, CanalEventos canal, Clock reloj) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
        this.canal = canal;
        this.reloj = reloj;
    }

    /** One pass. Returns how many events were published. */
    public int publicarPendientes() {
        Integer publicados = transaccion.execute(estado -> {
            List<EventoSaliente> pendientes = jdbc.query(
                    "SELECT id, mensaje_id, exchange, routing_key, tipo, cuerpo, intentos FROM eventos_salientes"
                            + " WHERE publicado_el IS NULL ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED",
                    (fila, n) -> new EventoSaliente(fila.getLong(1), fila.getString(2), fila.getString(3),
                            fila.getString(4), fila.getString(5), fila.getString(6), fila.getInt(7)),
                    POR_PASADA);
            int enviados = 0;
            for (EventoSaliente evento : pendientes) {
                if (!publicar(evento)) {
                    break;
                }
                enviados++;
            }
            return enviados;
        });
        return publicados == null ? 0 : publicados;
    }

    private boolean publicar(EventoSaliente evento) {
        try {
            canal.publicar(evento);
        } catch (RuntimeException e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            jdbc.update("UPDATE eventos_salientes SET intentos = intentos + 1, ultimo_error = ? WHERE id = ?",
                    error.length() > LARGO_ERROR ? error.substring(0, LARGO_ERROR) : error, evento.id());
            if (evento.intentos() == 0) {
                // Once per event: with the broker down this pass repeats every few seconds.
                log.warn("Outbox: no se pudo publicar el evento {} ({}); queda pendiente: {}",
                        evento.id(), evento.routingKey(), error);
            }
            return false;
        }
        jdbc.update("UPDATE eventos_salientes SET publicado_el = ?, intentos = intentos + 1, ultimo_error = NULL WHERE id = ?",
                LocalDateTime.now(reloj), evento.id());
        log.info("Outbox: evento {} publicado en {} con la clave {}{}", evento.id(), evento.exchange(),
                evento.routingKey(), evento.intentos() > 0 ? " (tras " + evento.intentos() + " intentos)" : "");
        return true;
    }

    /** Published events older than the given days are deleted: the outbox is not an archive. */
    public int purgarPublicados(int dias) {
        return jdbc.update("DELETE FROM eventos_salientes WHERE publicado_el < ?",
                LocalDateTime.now(reloj).minusDays(dias));
    }
}
