package com.daii.backdaii.service;

import com.daii.backdaii.dto.usuario.UpdateMeRequest;
import com.daii.backdaii.dto.usuario.UsuarioDTO;

import java.util.List;

public interface UsuarioService {

    UsuarioDTO getMe(String email);

    default UsuarioDTO updateMe(String email, UpdateMeRequest request) {
        return updateMe(email, request, Precondicion.NINGUNA);
    }

    /** Applies the change only if the profile is still in a version the client expected (If-Match). */
    UsuarioDTO updateMe(String email, UpdateMeRequest request, Precondicion precondicion);

    List<UsuarioDTO> listar();
}