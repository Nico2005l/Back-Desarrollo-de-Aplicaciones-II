package com.daii.backdaii.service.impl;

import com.daii.backdaii.service.PasarelaPagos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stands in for the payment provider, with the providers' sandbox test cards: one ending in 0002
 * (4000 0000 0000 0002) is declined, one ending in 0119 (4000 0000 0000 0119) makes the provider
 * fail technically, one ending in 0077 (4000 0000 0000 0077) makes the provider take 30 s to
 * answer (longer than the time limit of PasarelaPagosResiliente), and every other card is
 * approved. The delay mimics the provider's response time, so the PENDIENTE state is visible in a
 * demo. Refunds take app.pasarela.demora-reintegro-ms, zero by default, to show the timeout of a
 * cancellation on demand.
 */
@Component
public class PasarelaPagosSimulada implements PasarelaPagos {

    static final String TERMINACION_RECHAZADA = "0002";
    static final String TERMINACION_FALLA_TECNICA = "0119";
    static final String TERMINACION_SIN_RESPUESTA = "0077";
    static final long DEMORA_SIN_RESPUESTA_MS = 30_000;

    private final long demoraMs;
    private final long demoraReintegroMs;

    public PasarelaPagosSimulada(@Value("${app.pasarela.demora-ms:1500}") long demoraMs,
                                 @Value("${app.pasarela.demora-reintegro-ms:0}") long demoraReintegroMs) {
        this.demoraMs = demoraMs;
        this.demoraReintegroMs = demoraReintegroMs;
    }

    @Override
    public ResultadoPago cobrar(SolicitudPago solicitud) {
        esperar(TERMINACION_SIN_RESPUESTA.equals(solicitud.ultimos4()) ? DEMORA_SIN_RESPUESTA_MS : demoraMs);
        if (TERMINACION_FALLA_TECNICA.equals(solicitud.ultimos4())) {
            throw new IllegalStateException("La pasarela de pagos no respondió");
        }
        if (TERMINACION_RECHAZADA.equals(solicitud.ultimos4())) {
            return ResultadoPago.rechazado("El emisor de la tarjeta rechazó el pago");
        }
        return ResultadoPago.aprobado("SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    /** Refunds go through without delay by default: the customer is waiting for the cancellation answer. */
    @Override
    public ResultadoPago reintegrar(SolicitudReintegro solicitud) {
        esperar(demoraReintegroMs);
        return ResultadoPago.aprobado("SIM-R-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private static void esperar(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpió la espera de la pasarela de pagos", e);
        }
    }
}
