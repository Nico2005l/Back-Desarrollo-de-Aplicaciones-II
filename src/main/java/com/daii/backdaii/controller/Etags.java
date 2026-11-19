package com.daii.backdaii.controller;

import com.daii.backdaii.service.Precondicion;

import java.util.HashSet;
import java.util.Set;

/**
 * ETag and If-Match of the updatable resources. The ETag is the resource's version, a strong
 * validator: "7". If-Match accepts a list of tags or "*"; a tag this API never issued can never
 * match, so an update carrying it gets 412 like any stale one.
 */
final class Etags {

    private static final long NUNCA = -1;

    /** Contract of the Idempotency-Key header, as documented in OpenAPI (IdempotenciaFilter checks the same). */
    static final String PATRON_CLAVE = "^[\\x21-\\x7E]{8,255}$";
    static final String DESCRIPCION_IF_MATCH = "ETag recibido al consultar el recurso. Si cambió desde entonces, "
            + "responde 412 y no se aplica nada. Sin esta cabecera el cambio se aplica igual.";

    private Etags() {
    }

    static String de(long version) {
        return "\"" + version + "\"";
    }

    static Precondicion precondicion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.trim().equals("*")) {
            return Precondicion.NINGUNA;
        }
        Set<Long> versiones = new HashSet<>();
        for (String etiqueta : ifMatch.split(",")) {
            versiones.add(version(etiqueta.trim()));
        }
        return new Precondicion(versiones);
    }

    private static long version(String etiqueta) {
        // A weak tag (W/"7") never matches under If-Match's strong comparison (RFC 9110, 13.1.1).
        if (etiqueta.length() < 3 || !etiqueta.startsWith("\"") || !etiqueta.endsWith("\"")) {
            return NUNCA;
        }
        try {
            return Long.parseLong(etiqueta.substring(1, etiqueta.length() - 1));
        } catch (NumberFormatException e) {
            return NUNCA;
        }
    }
}
