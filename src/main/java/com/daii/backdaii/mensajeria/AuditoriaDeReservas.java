package com.daii.backdaii.mensajeria;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.domain.evento.ReservaCancelada;
import com.daii.backdaii.domain.evento.ReservaConfirmada;
import com.daii.backdaii.service.AuditoriaService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The audit subscriber of the whole booking topic ({@code reserva.*}): one queue, one subscriber,
 * every booking event recorded in the same log. It is the only subscriber that receives both
 * confirmations and cancellations, so it dispatches on the event type instead of being bound to a
 * single one; an event it does not know is rejected to its dead-letter queue rather than silently
 * dropped.
 */
@Component
@ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
class AuditoriaDeReservas extends SuscriptorEventos<Object> {

    private final AuditoriaService auditoria;

    AuditoriaDeReservas(Dependencias dependencias, AuditoriaService auditoria) {
        super("auditoria", Object.class, dependencias);
        this.auditoria = auditoria;
    }

    @RabbitListener(queues = Colas.AUDITORIA)
    public void alRecibir(Message mensaje) {
        recibir(mensaje, Colas.AUDITORIA);
    }

    @Override
    protected void procesar(Object evento) {
        if (evento instanceof ReservaConfirmada r) {
            auditoria.registrar(EventoAuditoria.RESERVA_CONFIRMADA, ResultadoAuditoria.EXITO, r.email(),
                    r.codigoReserva() + " · " + r.descripcionReserva() + " · "
                            + (r.origen() == ReservaConfirmada.Origen.COBRO ? "cobro aprobado" : "confirmada por un administrador"));
            return;
        }
        if (evento instanceof ReservaCancelada c) {
            auditoria.registrar(EventoAuditoria.RESERVA_CANCELADA, ResultadoAuditoria.EXITO, c.email(),
                    c.codigoReserva() + " · " + c.descripcionReserva() + " · " + comoTermino(c) + " · " + reintegro(c));
            return;
        }
        throw new AmqpRejectAndDontRequeueException(
                "Evento no soportado en " + Colas.AUDITORIA + ": " + evento.getClass().getName());
    }

    @Override
    protected Long reservaDe(Object evento) {
        if (evento instanceof ReservaConfirmada r) {
            return r.reservaId();
        }
        return evento instanceof ReservaCancelada c ? c.reservaId() : null;
    }

    private static String comoTermino(ReservaCancelada c) {
        String quien = switch (c.motivo()) {
            case CLIENTE -> "cancelada por el cliente";
            case ADMINISTRADOR -> "cancelada por un administrador";
            case ELIMINADA -> "eliminada por un administrador";
            case BLOQUEO -> "cancelada por un bloqueo del espacio";
        };
        return c.detalle() == null || c.detalle().isBlank() ? quien : quien + ": " + c.detalle();
    }

    private static String reintegro(ReservaCancelada c) {
        if (c.cobrado() == null) {
            return "sin cobro";
        }
        return "reintegrado " + c.reintegrado().stripTrailingZeros().toPlainString() + " de "
                + c.cobrado().stripTrailingZeros().toPlainString() + " (" + c.porcentaje() + "%)";
    }
}
