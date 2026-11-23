package com.daii.backdaii.service.impl;

import com.daii.backdaii.domain.evento.ReservaCancelada;
import com.daii.backdaii.domain.evento.ReservaCancelada.Motivo;
import com.daii.backdaii.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AvisosDeCancelacionTest {

    @Mock
    private EmailService emailService;

    @Test
    void informaElReintegroParcialEnElAvisoDeCancelacion() {
        AvisosDeCancelacion avisos = new AvisosDeCancelacion(emailService);
        ReservaCancelada cancelacion = new ReservaCancelada(
                7L,
                "R-7",
                "C-01 (Cubículo)",
                "ana@example.com",
                "Ana",
                Motivo.CLIENTE,
                null,
                new BigDecimal("7000.00"),
                new BigDecimal("3500.00"),
                50);

        avisos.alCancelar(cancelacion);

        ArgumentCaptor<String> destinatario = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nombre = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendAvisoReserva(destinatario.capture(), nombre.capture(), asunto.capture(), cuerpo.capture());

        assertThat(destinatario.getValue()).isEqualTo("ana@example.com");
        assertThat(nombre.getValue()).isEqualTo("Ana");
        assertThat(asunto.getValue()).isEqualTo("Cancelaste tu reserva R-7");
        String cuerpoSinEspaciosDeMoneda = cuerpo.getValue().replace("\u00a0", "").replace(" ", "");
        assertThat(cuerpoSinEspaciosDeMoneda)
                .contains("50%", "$3.500,00", "$7.000,00");
    }
}
