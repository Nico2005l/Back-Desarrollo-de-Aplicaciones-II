package com.daii.backdaii.mensajeria;

import com.daii.backdaii.domain.evento.ReservaConfirmada;
import com.daii.backdaii.service.ControlAcceso;
import com.daii.backdaii.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * The two independent subscribers of reserva.confirmada (the audit, which listens to the whole
 * booking topic, lives in AuditoriaDeReservas). Each has its own queue, so one being down or
 * failing never holds the others back, and a new one is added without touching the publisher.
 */
final class SuscriptoresReservaConfirmada {

    private SuscriptoresReservaConfirmada() {
    }

    /** Mails the customer that the booking is confirmed and how much was charged. */
    @Component
    @ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
    static class Avisos extends SuscriptorEventos<ReservaConfirmada> {

        private static final Logger log = LoggerFactory.getLogger(Avisos.class);
        private final EmailService emailService;

        Avisos(Dependencias dependencias, EmailService emailService) { // NOSONAR java:S9352 - only one EmailService exists at a time (app.mail.provider selects it)
            super("avisos", ReservaConfirmada.class, dependencias);
            this.emailService = emailService;
        }

        @RabbitListener(queues = Colas.AVISOS)
        public void alRecibir(Message mensaje) {
            recibir(mensaje, Colas.AVISOS);
        }

        @Override
        protected void procesar(ReservaConfirmada r) {
            if (r.origen() != ReservaConfirmada.Origen.COBRO) {
                // Confirmed by an administrator: the customer already got the "Cambió tu reserva" mail.
                log.info("Reserva {} confirmada por un administrador: sin mail de cobro", r.codigoReserva());
                return;
            }
            emailService.sendAvisoReserva(r.email(), r.nombre(), "Tu reserva está confirmada",
                    "Cobramos " + pesos(r.monto()) + " a tu tarjeta terminada en " + r.ultimos4()
                            + " por la reserva " + r.codigoReserva() + ": " + r.descripcionReserva() + ".");
        }

        @Override
        protected Long reservaDe(ReservaConfirmada evento) {
            return evento.reservaId();
        }

        private static String pesos(BigDecimal monto) {
            return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR")).format(monto);
        }
    }

    /** Enables the customer's card at the space's door for the booked slot. */
    @Component
    @ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
    static class ControlDeAcceso extends SuscriptorEventos<ReservaConfirmada> {

        private final ControlAcceso controlAcceso;

        ControlDeAcceso(Dependencias dependencias, ControlAcceso controlAcceso) {
            super("control-acceso", ReservaConfirmada.class, dependencias);
            this.controlAcceso = controlAcceso;
        }

        @RabbitListener(queues = Colas.CONTROL_ACCESO)
        public void alRecibir(Message mensaje) {
            recibir(mensaje, Colas.CONTROL_ACCESO);
        }

        @Override
        protected void procesar(ReservaConfirmada r) {
            controlAcceso.habilitar(new ControlAcceso.Habilitacion(r.codigoReserva(), r.codigoEspacio(), r.email(),
                    r.inicio(), r.fin()));
        }

        @Override
        protected Long reservaDe(ReservaConfirmada evento) {
            return evento.reservaId();
        }
    }
}
