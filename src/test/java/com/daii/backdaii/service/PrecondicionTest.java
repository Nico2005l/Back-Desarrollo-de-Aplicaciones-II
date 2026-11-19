package com.daii.backdaii.service;

import com.daii.backdaii.exception.PrecondicionFallidaException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrecondicionTest {

    @Test
    void sinIfMatchAceptaCualquierVersion() {
        assertThatCode(() -> Precondicion.NINGUNA.verificar(7L)).doesNotThrowAnyException();
        assertThatCode(() -> Precondicion.NINGUNA.verificar(null)).doesNotThrowAnyException();
    }

    @Test
    void laVersionEsperadaPasa() {
        assertThatCode(() -> Precondicion.version(3).verificar(3L)).doesNotThrowAnyException();
        assertThatCode(() -> new Precondicion(Set.of(2L, 3L)).verificar(2L)).doesNotThrowAnyException();
    }

    @Test
    void otraVersionRespondePrecondicionFallida() {
        assertThatThrownBy(() -> Precondicion.version(3).verificar(4L))
                .isInstanceOf(PrecondicionFallidaException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(
                        ((PrecondicionFallidaException) e).getStatus().value()).isEqualTo(412));
        assertThatThrownBy(() -> Precondicion.version(0).verificar(null))
                .isInstanceOf(PrecondicionFallidaException.class);
    }
}
