package com.daii.backdaii.web;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The keys of IdempotenciaFilter, in PostgreSQL so they hold across instances and restarts. Each
 * statement commits on its own (no surrounding transaction): the reservation of a key must be
 * visible to a concurrent request with the same key right away. The primary key (usuario, clave)
 * makes the reservation atomic: of two simultaneous requests, exactly one inserts.
 */
@Component
@RequiredArgsConstructor
class AlmacenIdempotencia {

    static final String EN_CURSO = "EN_CURSO";
    static final String COMPLETA = "COMPLETA";

    private final JdbcTemplate jdbc;

    /** A request already seen with this key: its fingerprint and, when it ended, its answer. */
    record Previa(String huella, String estado, Integer status, String tipoContenido, String cuerpo) {

        boolean enCurso() {
            return EN_CURSO.equals(estado);
        }
    }

    /**
     * Reserves the key for this request. Empty when it was free (the request goes on); otherwise
     * the request that holds it. Keys older than 24 hours, and reservations left EN_CURSO for more
     * than a minute (the instance died mid-request), no longer count.
     */
    Optional<Previa> reservar(String usuario, String clave, String metodo, String ruta, String huella) {
        jdbc.update("DELETE FROM claves_idempotencia WHERE usuario = ? AND clave = ? AND (creada_el < now() - interval '24 hours'"
                + " OR (estado = ? AND creada_el < now() - interval '1 minute'))", usuario, clave, EN_CURSO);
        int insertadas = jdbc.update("INSERT INTO claves_idempotencia (usuario, clave, metodo, ruta, huella, estado)"
                + " VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (usuario, clave) DO NOTHING",
                usuario, clave, metodo, ruta, huella, EN_CURSO);
        if (insertadas == 1) {
            return Optional.empty();
        }
        return jdbc.query("SELECT huella, estado, status_http, tipo_contenido, cuerpo FROM claves_idempotencia"
                        + " WHERE usuario = ? AND clave = ?",
                (fila, n) -> new Previa(fila.getString(1), fila.getString(2), (Integer) fila.getObject(3),
                        fila.getString(4), fila.getString(5)),
                usuario, clave).stream().findFirst()
                // Deleted in between by an expiry: treat it as busy, the client retries.
                .or(() -> Optional.of(new Previa(huella, EN_CURSO, null, null, null)));
    }

    void completar(String usuario, String clave, int status, String tipoContenido, String cuerpo) {
        jdbc.update("UPDATE claves_idempotencia SET estado = ?, status_http = ?, tipo_contenido = ?, cuerpo = ?"
                + " WHERE usuario = ? AND clave = ?", COMPLETA, status, tipoContenido, cuerpo, usuario, clave);
    }

    /** The request failed on the server side: the key is freed so the client can try again with it. */
    void liberar(String usuario, String clave) {
        jdbc.update("DELETE FROM claves_idempotencia WHERE usuario = ? AND clave = ? AND estado = ?",
                usuario, clave, EN_CURSO);
    }

    /** Daily housekeeping: answers older than 24 hours are no longer replayed. */
    int purgarVencidas() {
        return jdbc.update("DELETE FROM claves_idempotencia WHERE creada_el < now() - interval '24 hours'");
    }
}
