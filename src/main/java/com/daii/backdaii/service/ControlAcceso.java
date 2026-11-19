package com.daii.backdaii.service;

import java.time.LocalDateTime;

/**
 * Port to the building's access control: enables the customer's card at the space's door for the
 * booked slot. The app only knows this interface; today the adapter is a simulator
 * (ControlAccesoSimulado), and a real access-control system is another adapter of the same port.
 */
public interface ControlAcceso {

    void habilitar(Habilitacion habilitacion);

    record Habilitacion(String codigoReserva, String codigoEspacio, String email, LocalDateTime desde, LocalDateTime hasta) {
    }
}
