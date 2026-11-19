package com.daii.backdaii.mensajeria;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.AbstractJavaTypeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Publishes an outbox event on RabbitMQ and waits for the broker's confirm (publisher confirms):
 * the event counts as sent only when the broker took it, persistent, into its queues. Signed like
 * the charge messages (SEC-08), with the id fixed in the outbox so a re-publication is recognised.
 */
@Component
@ConditionalOnProperty(name = "app.mensajeria.habilitada", havingValue = "true")
@RequiredArgsConstructor
class CanalRabbit implements CanalEventos {

    private final RabbitTemplate rabbit;
    private final FirmaMensajes firma;

    @Value("${app.mensajeria.outbox.confirmacion-ms:5000}")
    private long esperaConfirmacionMs;

    @Override
    public void publicar(EventoSaliente evento) {
        byte[] cuerpo = evento.cuerpo().getBytes(StandardCharsets.UTF_8);
        Message mensaje = MessageBuilder.withBody(cuerpo)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(evento.mensajeId())
                // What the subscribers' JSON converter turns the body into.
                .setHeader(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME, evento.tipo())
                .setHeader(FirmaMensajes.CABECERA, firma.firmar(evento.mensajeId(), cuerpo))
                .build();
        rabbit.invoke(canal -> {
            canal.send(evento.exchange(), evento.routingKey(), mensaje);
            canal.waitForConfirmsOrDie(esperaConfirmacionMs);
            return null;
        });
    }
}
