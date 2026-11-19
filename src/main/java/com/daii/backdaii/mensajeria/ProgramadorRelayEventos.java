package com.daii.backdaii.mensajeria;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** Runs the outbox relay every few seconds, and cleans the published events once a day. */
@Component
@ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
class ProgramadorRelayEventos {

    private final RelayEventos relay;
    private final int diasPublicados;

    ProgramadorRelayEventos(JdbcTemplate jdbc, PlatformTransactionManager transacciones, CanalEventos canal, Clock reloj,
                            @Value("${app.mensajeria.outbox.conservar-dias:7}") int diasPublicados) {
        this.relay = new RelayEventos(jdbc, new TransactionTemplate(transacciones), canal, reloj);
        this.diasPublicados = diasPublicados;
    }

    @Scheduled(initialDelayString = "${app.mensajeria.outbox.cada-ms:2000}",
            fixedDelayString = "${app.mensajeria.outbox.cada-ms:2000}")
    public void publicar() {
        relay.publicarPendientes();
    }

    @Scheduled(cron = "${app.mensajeria.outbox.purga-cron:0 15 4 * * *}")
    public void purgar() {
        relay.purgarPublicados(diasPublicados);
    }
}
