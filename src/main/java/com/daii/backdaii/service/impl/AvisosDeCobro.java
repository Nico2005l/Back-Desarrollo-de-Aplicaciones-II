package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.evento.CobroResuelto;
import com.daii.backdaii.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Tells the customer how the charge ended. After commit, so a rolled-back charge never mails. */
@Component
@RequiredArgsConstructor
class AvisosDeCobro {

    private final EmailService emailService;

    /**
     * Only the declined charge: an approved one becomes ReservaConfirmada, whose mail is sent by
     * the subscriber of the topic (mensajeria.SuscriptoresReservaConfirmada.Avisos).
     */
    @TransactionalEventListener
    public void alResolverCobro(CobroResuelto cobro) {
        if (cobro.aprobado()) {
            return;
        }
        emailService.sendAvisoReserva(cobro.email(), cobro.nombre(), "No pudimos cobrar tu reserva",
                "La reserva " + cobro.codigoReserva() + " (" + cobro.descripcionReserva() + ") quedó cancelada y el espacio se liberó. "
                        + "Motivo: " + cobro.motivoRechazo() + ". Revisá tus métodos de pago en tu perfil y volvé a reservar.");
    }
}
