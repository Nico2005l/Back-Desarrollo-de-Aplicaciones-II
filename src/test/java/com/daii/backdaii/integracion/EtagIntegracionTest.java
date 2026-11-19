package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.MedioPago;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.Rol;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ETag and If-Match: an update based on a stale read answers 412 and changes nothing. */
class EtagIntegracionTest extends IntegracionBase {

    private static final String MOTIVO = "Pedido del cliente por telefono";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private ReservaRepository reservas;
    @Autowired
    private EspacioRepository espacios;
    @Autowired
    private MedioPagoRepository medios;
    @Autowired
    private Clock reloj;

    private Escenario escenario;
    private Usuario cliente;

    @BeforeEach
    void preparar() {
        escenario = new Escenario(usuarios, reservas, espacios, medios, reloj);
        cliente = escenario.cliente();
    }

    @Test
    void elPerfilConUnEtagViejoResponde412YNoCambia() throws Exception {
        String etag = mvc.perform(get("/api/usuarios/me").with(cliente()))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        // Someone else changes the profile first (another tab, another device).
        String nuevo = mvc.perform(patch("/api/usuarios/me").with(cliente()).header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON).content(telefono("+54 11 5555 0001")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(nuevo).isNotEqualTo(etag);

        mvc.perform(patch("/api/usuarios/me").with(cliente()).header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON).content(telefono("+54 11 5555 0002")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(412))
                .andExpect(jsonPath("$.code").value("RECURSO_MODIFICADO"));

        mvc.perform(get("/api/usuarios/me").with(cliente()))
                .andExpect(header().string(HttpHeaders.ETAG, nuevo))
                .andExpect(jsonPath("$.telefono").value("+54 11 5555 0001"));
    }

    @Test
    void sinIfMatchElCambioSeAplica() throws Exception {
        mvc.perform(patch("/api/usuarios/me").with(cliente())
                        .contentType(MediaType.APPLICATION_JSON).content(telefono("+54 11 5555 0003")))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG));
    }

    @Test
    void laTarjetaConUnEtagViejoResponde412() throws Exception {
        MedioPago tarjeta = escenario.tarjeta(cliente, "4242");
        String ruta = "/api/usuarios/me/medios-pago/" + tarjeta.getId();
        String etag = mvc.perform(get(ruta).with(cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimos4").value("4242"))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mvc.perform(put(ruta).with(cliente()).header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON).content(vencimiento("10/36")))
                .andExpect(status().isOk());
        mvc.perform(put(ruta).with(cliente()).header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON).content(vencimiento("11/37")))
                .andExpect(status().isPreconditionFailed());

        assertThat(medios.findById(tarjeta.getId()).orElseThrow().getVencimiento()).isEqualTo("10/36");
    }

    @Test
    void laTarjetaDeOtroNoDaSuEtag() throws Exception {
        MedioPago ajena = escenario.tarjeta(escenario.cliente(), "4242");

        mvc.perform(get("/api/usuarios/me/medios-pago/" + ajena.getId()).with(cliente()))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
    }

    @Test
    void dosAdministradoresEditandoLaMismaReservaElSegundoRecibe412() throws Exception {
        Reserva reserva = escenario.reservaEn(cliente, escenario.espacio(Escenario.ESPACIO_API),
                escenario.franjaReservable(), EstadoReserva.PENDIENTE);
        String etag = mvc.perform(get("/api/reservas/" + reserva.getId()).with(comoAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mvc.perform(put("/api/admin/reservas/" + reserva.getId()).with(comoAdmin()).header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edicion(reserva, reserva.getInicio(), EstadoReserva.CONFIRMADA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        mvc.perform(put("/api/admin/reservas/" + reserva.getId()).with(comoAdmin()).header(HttpHeaders.IF_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edicion(reserva, escenario.franjaReservable(), EstadoReserva.CONFIRMADA)))
                .andExpect(status().isPreconditionFailed());

        assertThat(reservas.findById(reserva.getId()).orElseThrow().getInicio()).isEqualTo(reserva.getInicio());
    }

    @Test
    void cualquierCambioDeEstadoCambiaElEtag() throws Exception {
        Reserva reserva = escenario.futura(cliente, EstadoReserva.PENDIENTE);
        String antes = mvc.perform(get("/api/reservas/" + reserva.getId()).with(cliente()))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/reservas/{id}/cancelar", reserva.getId()).with(cliente()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/reservas/" + reserva.getId()).with(cliente()))
                .andExpect(header().string(HttpHeaders.ETAG, org.hamcrest.Matchers.not(antes)));
    }

    private String edicion(Reserva reserva, LocalDateTime inicio, EstadoReserva estado) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("usuarioId", cliente.getId());
        cuerpo.put("espacioId", reserva.getEspacio().getId());
        cuerpo.put("inicio", inicio.toString());
        cuerpo.put("fin", inicio.plusHours(1).toString());
        cuerpo.put("estado", estado);
        cuerpo.put("motivo", MOTIVO);
        return json.writeValueAsString(cuerpo);
    }

    private String telefono(String numero) throws Exception {
        return json.writeValueAsString(Map.of("telefono", numero));
    }

    private String vencimiento(String mmaa) throws Exception {
        return json.writeValueAsString(Map.of("titular", "CLIENTE DE PRUEBA", "vencimiento", mmaa));
    }

    private RequestPostProcessor cliente() {
        return como(cliente.getEmail(), Rol.CLIENTE);
    }
}
