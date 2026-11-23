package com.daii.backdaii.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class PwnedPasswordsClienteTest {

    @Test
    void anteUnTimeoutOmiteLaConsultaYRegistraUnWarn() {
        RestClient.Builder builder = RestClient.builder().requestInterceptor((request, body, execution) -> {
            throw new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"));
        });
        PwnedPasswordsCliente cliente = new PwnedPasswordsCliente(builder);

        Logger logger = (Logger) LoggerFactory.getLogger(PwnedPasswordsCliente.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(cliente.filtrada("Clave-de-prueba-82kLm9")).isFalse();
            assertThat(appender.list)
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage())
                                .contains("No se pudo consultar Pwned Passwords")
                                .contains("Read timed out");
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
