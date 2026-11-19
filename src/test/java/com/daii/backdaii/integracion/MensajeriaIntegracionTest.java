package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoCobro;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.repository.CobroRepository;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.domain.evento.ReservaConfirmada;
import com.daii.backdaii.mensajeria.CanalEventos;
import com.daii.backdaii.mensajeria.CobroSolicitado;
import com.daii.backdaii.mensajeria.Colas;
import com.daii.backdaii.mensajeria.EventoSaliente;
import com.daii.backdaii.mensajeria.FirmaMensajes;
import com.daii.backdaii.mensajeria.RelayEventos;
import com.daii.backdaii.service.impl.ControlAccesoSimulado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The messaging end to end on a real RabbitMQ: same broker configuration as production
 * (docker/rabbitmq/rabbitmq.conf: TLS only, plain AMQP off) and the app connecting with amqps and
 * its own account. The certificate is created for the test run.
 *
 * Covers the charge queue (point to point) and the event topic with its outbox and subscribers.
 * The scheduled outbox relay is held back (cada-ms of an hour): the tests run it by hand, so they
 * decide when events go out and with which channel.
 */
@TestPropertySource(properties = {
        "app.mensajeria.habilitada=true",
        "app.mensajeria.firma-secreto=pruebas-integracion-firma-Ua6Lm3Vq9Bt2Hx8Pc4",
        "app.mensajeria.outbox.cada-ms=3600000",
        "app.pasarela.demora-ms=0",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.ssl.bundle=rabbitmq"
})
class MensajeriaIntegracionTest extends IntegracionBase {

    private static final String USUARIO = "planta-libre-api";
    private static final String CLAVE = "clave-del-broker-de-pruebas";
    private static final Path CERTIFICADOS = certificados();

    static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:3.13-management")
            .withEnv("RABBITMQ_DEFAULT_USER", USUARIO)
            .withEnv("RABBITMQ_DEFAULT_PASS", CLAVE)
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("docker/rabbitmq/rabbitmq.conf"), 0644),
                    "/etc/rabbitmq/conf.d/90-planta-libre.conf")
            .withCopyFileToContainer(MountableFile.forHostPath(CERTIFICADOS.resolve("ca.pem"), 0644), "/etc/rabbitmq/certs/ca.pem")
            .withCopyFileToContainer(MountableFile.forHostPath(CERTIFICADOS.resolve("servidor.pem"), 0644), "/etc/rabbitmq/certs/servidor.pem")
            .withCopyFileToContainer(MountableFile.forHostPath(CERTIFICADOS.resolve("servidor.key"), 0644), "/etc/rabbitmq/certs/servidor.key")
            .withExposedPorts(5671)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void broker(DynamicPropertyRegistry registro) {
        registro.add("spring.rabbitmq.addresses", () -> "amqps://" + USUARIO + ":" + CLAVE + "@"
                + RABBIT.getHost() + ":" + RABBIT.getMappedPort(5671));
        registro.add("spring.ssl.bundle.pem.rabbitmq.truststore.certificate",
                () -> "file:" + CERTIFICADOS.resolve("ca.pem").toAbsolutePath());
    }

    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private FirmaMensajes firma;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private ReservaRepository reservas;
    @Autowired
    private EspacioRepository espacios;
    @Autowired
    private MedioPagoRepository medios;
    @Autowired
    private CobroRepository cobros;
    @Autowired
    private Clock reloj;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired
    private CanalEventos canal;
    @Autowired
    private ControlAccesoSimulado controlAcceso;
    @Autowired
    private org.springframework.context.ApplicationEventPublisher eventosDeDominio;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transacciones;

    private org.springframework.transaction.support.TransactionTemplate transaccion;
    private Escenario escenario;
    private Usuario cliente;

    @BeforeEach
    void preparar() {
        escenario = new Escenario(usuarios, reservas, espacios, medios, reloj);
        cliente = escenario.cliente();
        transaccion = new org.springframework.transaction.support.TransactionTemplate(transacciones);
    }

    @Test
    void unPedidoFirmadoCobraYConfirmaLaReserva() {
        escenario.tarjeta(cliente, "4242");
        Reserva reserva = escenario.futura(cliente, EstadoReserva.PENDIENTE);

        publicar(reserva.getId(), UUID.randomUUID().toString(), true);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.CONFIRMADA));
        assertThat(cobros.findByReservaId(reserva.getId())).get()
                .satisfies(cobro -> assertThat(cobro.getEstado()).isEqualTo(EstadoCobro.APROBADO));
    }

    @Test
    void conLaTarjetaRechazadaLaReservaSeCancela() {
        escenario.tarjeta(cliente, "0002");
        Reserva reserva = escenario.futura(cliente, EstadoReserva.PENDIENTE);

        publicar(reserva.getId(), UUID.randomUUID().toString(), true);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.CANCELADA));
        assertThat(cobros.findByReservaId(reserva.getId())).get()
                .satisfies(cobro -> assertThat(cobro.getEstado()).isEqualTo(EstadoCobro.RECHAZADO));
    }

    @Test
    void unMensajeDuplicadoSeProcesaUnaSolaVez() {
        escenario.tarjeta(cliente, "4242");
        Reserva reserva = escenario.futura(cliente, EstadoReserva.PENDIENTE);
        String id = UUID.randomUUID().toString();

        publicar(reserva.getId(), id, true);
        publicar(reserva.getId(), id, true);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.CONFIRMADA));
        // Gives the redelivered copy time to arrive: it must not add a second charge.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(cobros.findByReservaIdIn(java.util.List.of(reserva.getId()))).hasSize(1));
    }

    @Test
    void unMensajeConFirmaInvalidaVaALaDlqSinCobrar() {
        escenario.tarjeta(cliente, "4242");
        Reserva reserva = escenario.futura(cliente, EstadoReserva.PENDIENTE);
        String id = UUID.randomUUID().toString();

        publicar(reserva.getId(), id, false);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Message enDlq = rabbit.receive(Colas.COBROS_DLQ, 500);
            assertThat(enDlq).isNotNull();
            assertThat(enDlq.getMessageProperties().getMessageId()).isEqualTo(id);
        });
        assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.PENDIENTE);
        assertThat(cobros.existsByReservaId(reserva.getId())).isFalse();
    }

    // ---- Topic reserva.confirmada: outbox, relay and subscribers ----

    @Test
    void unCobroAprobadoLlegaPorElTopicoAlControlDeAccesoYALaAuditoria() {
        escenario.tarjeta(cliente, "4242");
        Reserva reserva = escenario.futura(cliente, EstadoReserva.PENDIENTE);

        publicar(reserva.getId(), UUID.randomUUID().toString(), true);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.CONFIRMADA));
        // Written in the charge's own transaction, still unpublished: the relay is held back.
        assertThat(eventosDe(reserva.getId())).isEqualTo(1);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            relay(canal).publicarPendientes();
            assertThat(controlAcceso.recientes()).anySatisfy(h -> {
                assertThat(h.codigoReserva()).isEqualTo("R-" + reserva.getId());
                assertThat(h.email()).isEqualTo(cliente.getEmail());
                assertThat(h.desde()).isEqualTo(reserva.getInicio());
            });
            assertThat(auditadas("R-" + reserva.getId())).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_salientes WHERE reserva_id = ? AND publicado_el IS NOT NULL",
                Integer.class, reserva.getId())).isEqualTo(1);
    }

    @Test
    void unEventoRepublicadoConElMismoIdSeProcesaUnaSolaVez() {
        long reservaId = 900_000_000L + System.nanoTime() % 1_000_000;
        EventoSaliente evento = eventoDePrueba(reservaId);

        // As if the broker confirmed but the mark was lost: the relay sends the same event again.
        canal.publicar(evento);
        canal.publicar(evento);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(auditadas("R-" + reservaId)).isEqualTo(1));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4)).untilAsserted(() ->
                assertThat(auditadas("R-" + reservaId)).isEqualTo(1));
    }

    @Test
    void unEventoConFirmaInvalidaVaALaDlqDeCadaSuscriptor() {
        String id = UUID.randomUUID().toString();
        rabbit.convertAndSend(Colas.EXCHANGE_EVENTOS, Colas.RESERVA_CONFIRMADA, confirmada(1L), mensaje -> {
            mensaje.getMessageProperties().setMessageId(id);
            mensaje.getMessageProperties().setHeader(FirmaMensajes.CABECERA, "firma-falsa");
            return mensaje;
        });

        for (String cola : java.util.List.of(Colas.AVISOS, Colas.CONTROL_ACCESO, Colas.AUDITORIA)) {
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                Message enDlq = rabbit.receive(Colas.dlq(cola), 500);
                assertThat(enDlq).isNotNull();
                assertThat(enDlq.getMessageProperties().getMessageId()).isEqualTo(id);
            });
        }
    }

    @Test
    void elEventoSoloQuedaEnElOutboxSiLaTransaccionConfirma() {
        long confirmada = 910_000_000L + System.nanoTime() % 1_000_000;
        long revertida = confirmada + 1;

        transaccion.executeWithoutResult(estado -> eventosDeDominio.publishEvent(confirmada(confirmada)));
        transaccion.executeWithoutResult(estado -> {
            eventosDeDominio.publishEvent(confirmada(revertida));
            estado.setRollbackOnly();
        });

        assertThat(eventosDe(confirmada)).isEqualTo(1);
        assertThat(eventosDe(revertida)).isZero();
    }

    @Test
    void conElBrokerCaidoLosEventosEsperanYSalenEnOrdenCuandoVuelve() {
        long reservaId = 920_000_000L + System.nanoTime() % 1_000_000;
        long primero = guardarEvento(reservaId);
        long segundo = guardarEvento(reservaId + 1);

        // Broker down: nothing is marked as published and the error is kept.
        relay(evento -> {
            throw new org.springframework.amqp.AmqpConnectException(new java.net.ConnectException("Connection refused"));
        }).publicarPendientes();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_salientes WHERE id IN (?, ?) AND publicado_el IS NULL",
                Integer.class, primero, segundo)).isEqualTo(2);

        // Back up: both go out, in the order they were written.
        java.util.List<Long> enviados = new java.util.concurrent.CopyOnWriteArrayList<>();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            relay(evento -> enviados.add(evento.id())).publicarPendientes();
            assertThat(enviados).contains(primero, segundo);
        });
        assertThat(enviados.indexOf(primero)).isLessThan(enviados.indexOf(segundo));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_salientes WHERE id IN (?, ?) AND publicado_el IS NOT NULL",
                Integer.class, primero, segundo)).isEqualTo(2);
    }

    @Test
    void losEventosPublicadosViejosSePurgan() {
        long id = guardarEvento(930_000_000L + System.nanoTime() % 1_000_000);
        jdbc.update("UPDATE eventos_salientes SET publicado_el = ? WHERE id = ?",
                java.time.LocalDateTime.now(reloj).minusDays(8), id);

        relay(canal).purgarPublicados(7);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_salientes WHERE id = ?", Integer.class, id)).isZero();
    }

    private RelayEventos relay(CanalEventos canalDePrueba) {
        return new RelayEventos(jdbc, transaccion, canalDePrueba, reloj);
    }

    private int eventosDe(long reservaId) {
        return jdbc.queryForObject("SELECT count(*) FROM eventos_salientes WHERE reserva_id = ?", Integer.class, reservaId);
    }

    private int auditadas(String codigoReserva) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE evento = 'RESERVA_CONFIRMADA' AND detalle LIKE ?",
                Integer.class, codigoReserva + " %");
    }

    private ReservaConfirmada confirmada(long reservaId) {
        java.time.LocalDateTime inicio = java.time.LocalDateTime.now(reloj).plusDays(90).withNano(0);
        return new ReservaConfirmada(reservaId, "R-" + reservaId, "Cubículo de prueba", "C-PRUEBA", inicio,
                inicio.plusHours(1), "prueba@pruebas.plantalibre.local", "Prueba", ReservaConfirmada.Origen.ADMINISTRADOR,
                null, null);
    }

    private EventoSaliente eventoDePrueba(long reservaId) {
        try {
            return new EventoSaliente(0, UUID.randomUUID().toString(), Colas.EXCHANGE_EVENTOS, Colas.RESERVA_CONFIRMADA,
                    ReservaConfirmada.class.getName(), json.writeValueAsString(confirmada(reservaId)), 0);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** An outbox row written straight to the table, pending. */
    private long guardarEvento(long reservaId) {
        EventoSaliente e = eventoDePrueba(reservaId);
        return jdbc.queryForObject("INSERT INTO eventos_salientes (mensaje_id, exchange, routing_key, tipo, cuerpo, reserva_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                e.mensajeId(), e.exchange(), e.routingKey(), e.tipo(), e.cuerpo(), reservaId);
    }

    private void publicar(Long reservaId, String mensajeId, boolean firmado) {
        rabbit.convertAndSend(Colas.EXCHANGE_RESERVAS, Colas.COBRO_SOLICITADO,
                new CobroSolicitado(reservaId, Instant.now(reloj)), mensaje -> {
                    mensaje.getMessageProperties().setMessageId(mensajeId);
                    mensaje.getMessageProperties().setHeader(FirmaMensajes.CABECERA,
                            firmado ? firma.firmar(mensajeId, mensaje.getBody()) : "firma-falsa");
                    return mensaje;
                });
    }

    /** Self-signed certificate for localhost, written as PEM files for the broker and the app's truststore. */
    private static Path certificados() {
        try {
            Path dir = Files.createTempDirectory("rabbitmq-tls");
            Path almacen = dir.resolve("servidor.p12");
            char[] clave = "solo-para-esta-prueba".toCharArray();
            Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                    "-genkeypair", "-alias", "rabbitmq", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                    "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                    "-storetype", "PKCS12", "-keystore", almacen.toString(),
                    "-storepass", new String(clave), "-keypass", new String(clave))
                    .redirectErrorStream(true).start();
            String salida = new String(keytool.getInputStream().readAllBytes());
            if (keytool.waitFor() != 0) {
                throw new IllegalStateException("keytool falló: " + salida);
            }
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(almacen)) {
                ks.load(in, clave);
            }
            PrivateKey llave = (PrivateKey) ks.getKey("rabbitmq", clave);
            Certificate certificado = ks.getCertificate("rabbitmq");
            String pem = pem("CERTIFICATE", certificado.getEncoded());
            Files.writeString(dir.resolve("servidor.pem"), pem);
            Files.writeString(dir.resolve("ca.pem"), pem);
            Files.writeString(dir.resolve("servidor.key"), pem("PRIVATE KEY", llave.getEncoded()));
            return dir;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el certificado del broker de pruebas", e);
        }
    }

    private static String pem(String tipo, byte[] contenido) {
        return "-----BEGIN " + tipo + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(contenido)
                + "\n-----END " + tipo + "-----\n";
    }
}
