package com.daii.backdaii.mensajeria;

import com.daii.backdaii.service.AuditoriaService;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
class SuscriptoresConfig {

    @Bean
    SuscriptorEventos.Dependencias dependenciasSuscriptores(FirmaMensajes firma, MessageConverter conversor, JdbcTemplate jdbc,
                                                            PlatformTransactionManager transacciones, AuditoriaService auditoria) {
        return new SuscriptorEventos.Dependencias(firma, conversor, jdbc, new TransactionTemplate(transacciones), auditoria);
    }
}
