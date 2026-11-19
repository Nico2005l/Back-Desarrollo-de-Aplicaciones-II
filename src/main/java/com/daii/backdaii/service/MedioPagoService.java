package com.daii.backdaii.service;

import com.daii.backdaii.dto.medioPago.MedioPagoRequest;
import com.daii.backdaii.dto.medioPago.MedioPagoResumen;
import com.daii.backdaii.dto.medioPago.MedioPagoUpdateRequest;

import java.util.List;

public interface MedioPagoService {

    MedioPagoResumen agregarTarjeta(String email, MedioPagoRequest request);

    List<MedioPagoResumen> listar(String email);

    /** One of the user's cards; 404 if it is someone else's. */
    MedioPagoResumen obtener(String email, Long id);

    default MedioPagoResumen actualizar(String email, Long id, MedioPagoUpdateRequest request) {
        return actualizar(email, id, request, Precondicion.NINGUNA);
    }

    /** Applies the change only if the card is still in a version the client expected (If-Match). */
    MedioPagoResumen actualizar(String email, Long id, MedioPagoUpdateRequest request, Precondicion precondicion);

    /** Makes the card the user's primary one and returns the updated list. */
    List<MedioPagoResumen> marcarPrincipal(String email, Long id);

    void eliminar(String email, Long id);
}