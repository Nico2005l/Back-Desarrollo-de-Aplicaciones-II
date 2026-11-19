package com.daii.backdaii.dto.asistente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contratos HTTP del asistente. Las acciones posibles se limitan a navegación interna. */
public final class AsistenteDtos {

    private AsistenteDtos() {
    }

    public record ConsultaRequest(
            @NotBlank(message = "La consulta no puede estar vacía")
            @Size(max = 1000, message = "La consulta no puede superar los 1000 caracteres")
            String message
    ) {
    }

    public record Respuesta(
            String message,
            Accion action,
            String buttonLabel
    ) {
    }

    /** Debe mantenerse alineado con los identificadores permitidos por el frontend. */
    public enum Accion {
        GO_TO_RESERVATIONS,
        GO_TO_MY_RESERVATIONS,
        GO_TO_AVAILABILITY
    }
}
