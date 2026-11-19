package com.daii.backdaii.service;

import com.daii.backdaii.exception.PrecondicionFallidaException;

import java.util.Set;

/**
 * The versions an update accepts, taken from its If-Match header. An update without If-Match
 * accepts any version (NINGUNA). The service checks it with the row locked, so two updates that
 * both read version 3 cannot both pass: the second one finds version 4 and gets 412.
 */
public record Precondicion(Set<Long> versiones) {

    public static final Precondicion NINGUNA = new Precondicion(null);

    public static Precondicion version(long version) {
        return new Precondicion(Set.of(version));
    }

    /** Throws 412 when the resource's current version is not one the client expected. */
    public void verificar(Long actual) {
        if (versiones != null && (actual == null || !versiones.contains(actual))) {
            throw new PrecondicionFallidaException();
        }
    }
}
