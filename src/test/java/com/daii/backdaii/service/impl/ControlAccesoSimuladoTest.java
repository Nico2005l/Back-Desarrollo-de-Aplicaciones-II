package com.daii.backdaii.service.impl;

import com.daii.backdaii.service.ControlAcceso.Habilitacion;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ControlAccesoSimuladoTest {

    private static final LocalDateTime DESDE = LocalDateTime.of(2026, 12, 1, 10, 0);

    @Test
    void guardaLasHabilitacionesMasNuevasPrimero() {
        ControlAccesoSimulado control = new ControlAccesoSimulado();

        control.habilitar(habilitacion("R-1"));
        control.habilitar(habilitacion("R-2"));

        assertThat(control.recientes()).extracting(Habilitacion::codigoReserva).containsExactly("R-2", "R-1");
    }

    @Test
    void conservaSoloLasUltimasCien() {
        ControlAccesoSimulado control = new ControlAccesoSimulado();

        for (int i = 1; i <= ControlAccesoSimulado.RECIENTES + 5; i++) {
            control.habilitar(habilitacion("R-" + i));
        }

        assertThat(control.recientes()).hasSize(ControlAccesoSimulado.RECIENTES);
        assertThat(control.recientes().get(0).codigoReserva()).isEqualTo("R-105");
        assertThat(control.recientes()).extracting(Habilitacion::codigoReserva).doesNotContain("R-5");
    }

    private static Habilitacion habilitacion(String codigo) {
        return new Habilitacion(codigo, "C-101", "ana@example.com", DESDE, DESDE.plusHours(1));
    }
}
