package com.daii.backdaii.service.impl;

import com.daii.backdaii.service.ControlAcceso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Stands in for the building's access-control system: logs the access it would enable and keeps
 * the last ones in memory, so a demo or a test can see them.
 */
@Component
public class ControlAccesoSimulado implements ControlAcceso {

    private static final Logger log = LoggerFactory.getLogger(ControlAccesoSimulado.class);
    static final int RECIENTES = 100;

    private final Deque<Habilitacion> recientes = new ArrayDeque<>();

    @Override
    public void habilitar(Habilitacion h) {
        log.info("Control de acceso: tarjeta de {} habilitada en {} de {} a {} (reserva {})",
                h.email(), h.codigoEspacio(), h.desde(), h.hasta(), h.codigoReserva());
        synchronized (recientes) {
            recientes.addFirst(h);
            if (recientes.size() > RECIENTES) {
                recientes.removeLast();
            }
        }
    }

    /** The most recent enabled accesses, newest first. */
    public List<Habilitacion> recientes() {
        synchronized (recientes) {
            return List.copyOf(recientes);
        }
    }
}
