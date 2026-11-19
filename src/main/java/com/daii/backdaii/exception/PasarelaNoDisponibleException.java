package com.daii.backdaii.exception;

import org.springframework.http.HttpStatus;

/**
 * The payment provider did not answer in time, failed technically, or its circuit is open. Each
 * caller decides the fallback: the charge consumer lets the message be retried (and the sweep
 * after it); a cancellation rolls back and the customer gets a 503 asking to try again later.
 */
public class PasarelaNoDisponibleException extends ApiException {

    public static final String CODE = "PASARELA_NO_DISPONIBLE";
    /** Seconds the client should wait before trying again: the time the circuit stays open. */
    public static final int REINTENTAR_EN_SEGUNDOS = 30;

    private final String motivo;

    public PasarelaNoDisponibleException(String motivo, Throwable causa) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                "La pasarela de pagos no respondió. Tu reserva no cambió; probá de nuevo en unos minutos.", CODE);
        initCause(causa);
        this.motivo = motivo;
    }

    /** For the log only: why the call failed (timeout, open circuit, provider error). */
    public String getMotivo() {
        return motivo;
    }
}
