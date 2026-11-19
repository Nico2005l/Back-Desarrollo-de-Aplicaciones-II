package com.daii.backdaii.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The single error format of the API: Problem Details (RFC 9457), served as application/problem+json.
 * Every error, from a controller or from a filter, is built here. Besides type, title, status, detail
 * and instance it carries the members the front reads: message (same as detail), code when the client
 * reacts to a specific case, and the extras of some errors (errors, alternativas, reservas).
 */
public final class Problemas {

    public static final MediaType TIPO = MediaType.APPLICATION_PROBLEM_JSON;
    static final String PREFIJO_TIPO = "urn:planta-libre:problema:";

    private static final Map<Integer, String> TITULOS = Map.ofEntries(
            Map.entry(400, "Solicitud inválida"),
            Map.entry(401, "No autenticado"),
            Map.entry(403, "Sin permiso"),
            Map.entry(404, "No encontrado"),
            Map.entry(405, "Método no permitido"),
            Map.entry(409, "Conflicto con el estado actual"),
            Map.entry(412, "El recurso cambió"),
            Map.entry(415, "Tipo de contenido no soportado"),
            Map.entry(422, "Solicitud no procesable"),
            Map.entry(428, "Falta una precondición"),
            Map.entry(429, "Demasiadas solicitudes"),
            Map.entry(500, "Error inesperado"),
            Map.entry(503, "Servicio no disponible"));

    private Problemas() {
    }

    public static ProblemDetail de(HttpStatus status, String mensaje) {
        return de(status, mensaje, null);
    }

    /** With a code, the type identifies the case: urn:planta-libre:problema:CODE. Without one, about:blank. */
    public static ProblemDetail de(HttpStatus status, String mensaje, String codigo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, mensaje);
        problema.setTitle(TITULOS.getOrDefault(status.value(), status.getReasonPhrase()));
        problema.setProperty("message", mensaje);
        if (codigo != null) {
            problema.setType(URI.create(PREFIJO_TIPO + codigo));
            problema.setProperty("code", codigo);
        }
        return problema;
    }

    /** For the filters that answer before any controller (authentication, rate limit, idempotency). */
    public static void escribir(HttpServletResponse respuesta, ObjectMapper json, String instancia,
                                ProblemDetail problema) throws IOException {
        problema.setInstance(URI.create(instancia));
        respuesta.setStatus(problema.getStatus());
        respuesta.setContentType(TIPO.toString());
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(respuesta.getWriter(), problema);
    }
}
