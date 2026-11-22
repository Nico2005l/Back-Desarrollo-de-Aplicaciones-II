package com.daii.backdaii.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.List;

/**
 * Broker topology, declared by the app on startup (RabbitAdmin creates what is missing).
 * Only active with app.mensajeria.habilitada=true: without a broker, bookings stay PENDIENTE
 * and an administrator confirms them, as before.
 */
@Configuration
@ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
// For the charge sweep (ProgramadorBarridoCobros) and the outbox relay (ProgramadorRelayEventos).
@EnableScheduling
public class MensajeriaConfig {

    @Bean
    DirectExchange exchangeReservas() {
        return new DirectExchange(Colas.EXCHANGE_RESERVAS);
    }

    @Bean
    DirectExchange exchangeDlx() {
        return new DirectExchange(Colas.EXCHANGE_DLX);
    }

    /** Durable, and rejected messages go to the dead-letter exchange instead of being lost. */
    @Bean
    Queue colaCobros() {
        return QueueBuilder.durable(Colas.COBROS)
                .deadLetterExchange(Colas.EXCHANGE_DLX)
                .deadLetterRoutingKey(Colas.COBROS_DLQ)
                .build();
    }

    @Bean
    Queue colaCobrosDlq() {
        return QueueBuilder.durable(Colas.COBROS_DLQ).build();
    }

    @Bean
    Binding cobrosAlExchange() {
        return BindingBuilder.bind(colaCobros()).to(exchangeReservas()).with(Colas.COBRO_SOLICITADO);
    }

    @Bean
    Binding dlqAlExchange() {
        return BindingBuilder.bind(colaCobrosDlq()).to(exchangeDlx()).with(Colas.COBROS_DLQ);
    }

    // Topic of the domain events and its subscribers: mail, building access control and audit.

    @Bean
    TopicExchange exchangeEventos() {
        return new TopicExchange(Colas.EXCHANGE_EVENTOS);
    }

    @Bean
    Declarables suscriptoresDeEventos() {
        List<Declarable> objetos = new ArrayList<>();
        suscriptor(objetos, Colas.AVISOS, Colas.RESERVA_CONFIRMADA);
        suscriptor(objetos, Colas.CONTROL_ACCESO, Colas.RESERVA_CONFIRMADA);
        suscriptor(objetos, Colas.AUDITORIA, Colas.TODO_RESERVA);
        return new Declarables(objetos);
    }

    /** A durable queue bound to the topic with its pattern, and its own dead-letter queue. */
    private void suscriptor(List<Declarable> objetos, String nombre, String patron) {
        Queue cola = QueueBuilder.durable(nombre)
                .deadLetterExchange(Colas.EXCHANGE_DLX)
                .deadLetterRoutingKey(Colas.dlq(nombre))
                .build();
        Queue dlq = QueueBuilder.durable(Colas.dlq(nombre)).build();
        objetos.add(cola);
        objetos.add(dlq);
        objetos.add(BindingBuilder.bind(cola).to(exchangeEventos()).with(patron));
        objetos.add(BindingBuilder.bind(dlq).to(exchangeDlx()).with(Colas.dlq(nombre)));
    }

    /**
     * Fail-fast checks of the broker connection (SEC-07): TLS always, and never the default
     * guest account. Also the signing secret of the messages (SEC-08).
     */
    @Bean
    FirmaMensajes firmaMensajes(@Value("${spring.rabbitmq.addresses:}") String direccion,
                                @Value("${app.mensajeria.firma-secreto:}") String secreto) {
        if (direccion == null || !direccion.startsWith("amqps://")) {
            throw new IllegalStateException("RABBITMQ_URL tiene que usar amqps:// (TLS): la mensajería no arranca sin cifrado.");
        }
        if (direccion.startsWith("amqps://guest:") || !direccion.substring("amqps://".length()).contains("@")) {
            throw new IllegalStateException("RABBITMQ_URL tiene que usar una cuenta propia de la aplicación, no guest ni conexión anónima.");
        }
        return new FirmaMensajes(secreto);
    }

    /**
     * JSON on the wire (readable in the RabbitMQ console); Boot wires it into the template and the listeners.
     *
     * <p>Spring AMQP 3.2 (Spring Boot 3.5) stopped trusting every package when deserializing: by
     * default only {@code java.util} and {@code java.lang} are accepted, which makes our own events
     * fail with "is not in the trusted packages". Only the packages that actually travel on the
     * wire are declared, never {@code *}.
     */
    @Bean
    MessageConverter conversorJson(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter conversor = new Jackson2JsonMessageConverter(objectMapper);
        DefaultJackson2JavaTypeMapper tipos = new DefaultJackson2JavaTypeMapper();
        tipos.setTrustedPackages(
                "java.util",
                "java.lang",
                "com.daii.backdaii.mensajeria",
                "com.daii.backdaii.domain.evento");
        conversor.setJavaTypeMapper(tipos);
        return conversor;
    }
}
