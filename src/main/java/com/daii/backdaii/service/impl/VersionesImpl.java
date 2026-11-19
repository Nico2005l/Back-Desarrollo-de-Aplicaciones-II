package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.exception.NotFoundException;
import com.daii.backdaii.service.Versiones;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VersionesImpl implements Versiones {

    private final ReservaRepository reservas;
    private final MedioPagoRepository medios;
    private final UsuarioRepository usuarios;

    @Override
    public long reserva(Long id) {
        return reservas.findVersionById(id).orElseThrow(() -> new NotFoundException("Reserva no encontrada"));
    }

    @Override
    public long medioPago(Long id) {
        return medios.findVersionById(id).orElseThrow(() -> new NotFoundException("Medio de pago no encontrado"));
    }

    @Override
    public long usuario(String email) {
        return usuarios.findVersionByEmail(email).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }
}
