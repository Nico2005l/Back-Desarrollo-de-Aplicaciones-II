package com.daii.backdaii.controller;

import com.daii.backdaii.exception.PrecondicionFallidaException;
import com.daii.backdaii.service.Precondicion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EtagsTest {

    @Test
    void elEtagEsLaVersionEntreComillas() {
        assertThat(Etags.de(7)).isEqualTo("\"7\"");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "*", " * "})
    void sinIfMatchOConAsteriscoNoHayPrecondicion(String ifMatch) {
        assertThat(Etags.precondicion(ifMatch)).isSameAs(Precondicion.NINGUNA);
    }

    @Test
    void aceptaUnaListaDeEtiquetas() {
        Precondicion precondicion = Etags.precondicion("\"3\", \"5\"");

        assertThatCode(() -> precondicion.verificar(5L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> precondicion.verificar(4L)).isInstanceOf(PrecondicionFallidaException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"W/\"3\"", "3", "\"\"", "\"abc\"", "\"3"})
    void unaEtiquetaDebilOMalFormadaNuncaCoincide(String ifMatch) {
        assertThatThrownBy(() -> Etags.precondicion(ifMatch).verificar(3L))
                .isInstanceOf(PrecondicionFallidaException.class);
    }
}
