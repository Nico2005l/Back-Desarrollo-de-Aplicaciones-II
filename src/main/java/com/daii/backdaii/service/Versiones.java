package com.daii.backdaii.service;

/**
 * Current version of the updatable resources, for their ETag. Read after the operation that
 * served or changed the resource, so it already checked existence and ownership.
 */
public interface Versiones {

    long reserva(Long id);

    long medioPago(Long id);

    long usuario(String email);
}
