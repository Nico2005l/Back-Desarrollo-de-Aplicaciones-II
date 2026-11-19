package com.daii.backdaii.exception;

import org.springframework.http.HttpStatus;

/** The If-Match of an update names a version the resource no longer has: someone changed it first. */
public class PrecondicionFallidaException extends ApiException {

    public static final String CODE = "RECURSO_MODIFICADO";

    public PrecondicionFallidaException() {
        super(HttpStatus.PRECONDITION_FAILED,
                "Alguien modificó estos datos después de que los cargaste. Volvé a cargarlos y repetí el cambio.", CODE);
    }
}
