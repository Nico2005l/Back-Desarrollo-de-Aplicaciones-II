package com.daii.backdaii.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a creation endpoint that honours the Idempotency-Key header (IdempotenciaFilter): the
 * same key repeated by the same user gets the first answer back instead of a second resource.
 * The header is optional; without it the endpoint behaves as usual.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotente {

    String CABECERA = "Idempotency-Key";
    String DESCRIPCION = "Clave única por intento (por ejemplo un UUID), de 8 a 255 caracteres visibles. "
            + "Repetir el pedido con la misma clave devuelve la primera respuesta sin crear otro recurso; "
            + "la misma clave con otro cuerpo responde 422. Vale 24 horas.";
}
