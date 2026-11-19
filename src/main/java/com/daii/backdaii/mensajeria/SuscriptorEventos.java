package com.daii.backdaii.mensajeria;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.service.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What every subscriber of the event topic does with a message, in order (Template Method):
 * <ol>
 *   <li>checks the signature (SEC-08): an unsigned or altered message goes straight to its DLQ;</li>
 *   <li>turns the JSON into the event;</li>
 *   <li>marks the message as processed by this subscriber and handles it, in one transaction. The
 *       mark is per subscriber, since every queue gets a copy of the same message: a redelivered
 *       copy is discarded, and if handling fails the mark rolls back and the message is retried
 *       (and after the retries, dead-lettered).</li>
 * </ol>
 * Subclasses only say which queue they read and what they do with the event.
 */
abstract class SuscriptorEventos<E> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final String nombre;
    private final Class<E> tipo;
    private final FirmaMensajes firma;
    private final MessageConverter conversor;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final AuditoriaService auditoria;

    protected SuscriptorEventos(String nombre, Class<E> tipo, Dependencias dependencias) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.firma = dependencias.firma();
        this.conversor = dependencias.conversor();
        this.jdbc = dependencias.jdbc();
        this.transaccion = dependencias.transaccion();
        this.auditoria = dependencias.auditoria();
    }

    /** What the subscriber does with an event it has not seen before. A thrown exception retries it. */
    protected abstract void procesar(E evento);

    /** The booking the event is about, recorded with the processed mark. */
    protected abstract Long reservaDe(E evento);

    protected void recibir(Message mensaje, String cola) {
        String id = mensaje.getMessageProperties().getMessageId();
        if (!firma.valida(id, mensaje.getBody(), mensaje.getMessageProperties().getHeaders().get(FirmaMensajes.CABECERA))) {
            log.error("Mensaje {} con firma inválida en {}: se descarta sin procesar", id, cola);
            auditoria.registrar(EventoAuditoria.MENSAJE_FIRMA_INVALIDA, ResultadoAuditoria.DENEGADO, null,
                    "Cola " + cola + ", mensaje " + id);
            throw new AmqpRejectAndDontRequeueException("Firma inválida");
        }
        Object convertido = conversor.fromMessage(mensaje);
        if (!tipo.isInstance(convertido)) {
            throw new AmqpRejectAndDontRequeueException("Se esperaba " + tipo.getSimpleName() + " en " + cola);
        }
        E evento = tipo.cast(convertido);
        transaccion.executeWithoutResult(estado -> {
            if (!marcarProcesado(id, reservaDe(evento))) {
                log.info("{}: mensaje {} ya procesado, se descarta el duplicado", nombre, id);
                return;
            }
            procesar(evento);
        });
    }

    /** True the first time this subscriber sees the message. Atomic: two copies at once, only one wins. */
    private boolean marcarProcesado(String mensajeId, Long reservaId) {
        return jdbc.update("INSERT INTO mensajes_procesados (mensaje_id, reserva_id, procesado_el) VALUES (?, ?, now())"
                + " ON CONFLICT (mensaje_id) DO NOTHING", nombre + ":" + mensajeId, reservaId) == 1;
    }

    /** What every subscriber needs, injected once. */
    record Dependencias(FirmaMensajes firma, MessageConverter conversor, JdbcTemplate jdbc,
                        TransactionTemplate transaccion, AuditoriaService auditoria) {
    }
}
