package com.daii.backdaii.mensajeria;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.service.AuditoriaService;
import com.daii.backdaii.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** SEC-08: a message landing in a dead-letter queue raises exactly one alert. */
@ExtendWith(MockitoExtension.class)
class MonitorDlqTest {

    private static final String DLQ = Colas.COBROS_DLQ;
    private static final String EQUIPO = "equipo@plantalibre.local";

    @Mock
    private AmqpAdmin admin;
    @Mock
    private AuditoriaService auditoria;
    @Mock
    private EmailService email;

    @Test
    void unMensajeNuevoEnLaDlqDisparaUnaAlertaEnLaAuditoriaYPorMail() {
        MonitorDlq monitor = new MonitorDlq(admin, auditoria, email, EQUIPO);
        enLaDlq(0);
        monitor.revisar();
        verifyNoInteractions(auditoria, email);

        enLaDlq(2);
        monitor.revisar();

        verify(auditoria).registrar(eq(EventoAuditoria.MENSAJE_EN_DLQ), eq(ResultadoAuditoria.FALLO), isNull(),
                contains("2 mensaje(s) nuevo(s) en " + DLQ));
        verify(email).sendAlerta(eq(EQUIPO), anyString(), contains(DLQ));
    }

    @Test
    void losMismosMensajesNoVuelvenAAlertarYLosNuevosSi() {
        MonitorDlq monitor = new MonitorDlq(admin, auditoria, email, EQUIPO);
        enLaDlq(1);
        monitor.revisar();
        monitor.revisar();
        verify(auditoria).registrar(eq(EventoAuditoria.MENSAJE_EN_DLQ), eq(ResultadoAuditoria.FALLO), isNull(),
                contains("1 mensaje(s) nuevo(s)"));

        enLaDlq(3);
        monitor.revisar();
        verify(auditoria).registrar(eq(EventoAuditoria.MENSAJE_EN_DLQ), eq(ResultadoAuditoria.FALLO), isNull(),
                contains("2 mensaje(s) nuevo(s) en " + DLQ + " (3 en total)"));
    }

    @Test
    void sinMailDeAlertasConfiguradoQuedaSoloEnLaAuditoria() {
        MonitorDlq monitor = new MonitorDlq(admin, auditoria, email, "");
        enLaDlq(1);
        monitor.revisar();

        verify(auditoria).registrar(eq(EventoAuditoria.MENSAJE_EN_DLQ), eq(ResultadoAuditoria.FALLO), isNull(), anyString());
        verify(email, never()).sendAlerta(anyString(), anyString(), anyString());
    }

    @Test
    void siElBrokerNoRespondeNoAlertaNiFalla() {
        MonitorDlq monitor = new MonitorDlq(admin, auditoria, email, EQUIPO);
        when(admin.getQueueInfo(anyString())).thenThrow(new IllegalStateException("broker caído"));

        monitor.revisar();

        verifyNoInteractions(auditoria, email);
    }

    /** The cobros DLQ holds {@code cantidad} messages; the other dead-letter queues are empty. */
    private void enLaDlq(int cantidad) {
        lenient().when(admin.getQueueInfo(anyString())).thenAnswer(invocacion ->
                new QueueInformation(invocacion.getArgument(0), 0, 0));
        when(admin.getQueueInfo(DLQ)).thenReturn(new QueueInformation(DLQ, cantidad, 0));
    }
}
