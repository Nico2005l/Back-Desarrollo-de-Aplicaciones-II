package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.Rol;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Idempotency-Key on the creation endpoints: a repeated POST creates nothing new. */
class IdempotenciaIntegracionTest extends IntegracionBase {

    private static final String TARJETAS = "/api/usuarios/me/medios-pago";

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
    void elMismoPostConLaMismaClaveDevuelveLaPrimeraRespuestaYNoCreaOtraReserva() throws Exception {
        LocalDateTime inicio = escenario.franjaReservable();
        String clave = UUID.randomUUID().toString();
        String cuerpo = json.writeValueAsString(Map.of("espacioId", escenario.espacio(Escenario.ESPACIO_API).getId(),
                "inicio", inicio.toString(), "fin", inicio.plusHours(1).toString()));
        long antes = reservas.count();

        MockHttpServletResponse primera = reservar(cliente(), clave, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn().getResponse();
        reservar(cliente(), clave, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(content().json(primera.getContentAsString(java.nio.charset.StandardCharsets.UTF_8), true));

        assertThat(reservas.count()).isEqualTo(antes + 1);
    }

    @Test
    void laMismaClaveConOtroCuerpoResponde422() throws Exception {
        String clave = UUID.randomUUID().toString();
        agregarTarjeta(cliente(), clave, "4111 1111 1111 1111").andExpect(status().isCreated());

        agregarTarjeta(cliente(), clave, "5500 0000 0000 0004")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUTILIZADA"));

        assertThat(medios.countByUsuarioIdAndEliminadoFalse(cliente.getId())).isEqualTo(1);
    }

    @Test
    void laClaveEsDeCadaUsuario() throws Exception {
        String clave = UUID.randomUUID().toString();
        Usuario otro = escenario.cliente();

        agregarTarjeta(cliente(), clave, "4111 1111 1111 1111").andExpect(status().isCreated());
        agregarTarjeta(como(otro.getEmail(), Rol.CLIENTE), clave, "4111 1111 1111 1111")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));

        assertThat(medios.countByUsuarioIdAndEliminadoFalse(otro.getId())).isEqualTo(1);
    }

    @Test
    void unErrorDeValidacionTambienSeRepiteIgual() throws Exception {
        String clave = UUID.randomUUID().toString();

        agregarTarjeta(cliente(), clave, "1234").andExpect(status().isBadRequest());
        agregarTarjeta(cliente(), clave, "1234")
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void unaClaveMalFormadaResponde400() throws Exception {
        agregarTarjeta(cliente(), "corta", "4111 1111 1111 1111")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_INVALIDA"));

        assertThat(medios.countByUsuarioIdAndEliminadoFalse(cliente.getId())).isZero();
    }

    @Test
    void sinClaveCadaPostCreaUnRecurso() throws Exception {
        String cuerpo = tarjeta("4111 1111 1111 1111");
        mvc.perform(post(TARJETAS).with(cliente()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());
        mvc.perform(post(TARJETAS).with(cliente()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());

        assertThat(medios.countByUsuarioIdAndEliminadoFalse(cliente.getId())).isEqualTo(2);
    }

    private ResultActions reservar(RequestPostProcessor quien, String clave, String cuerpo) throws Exception {
        return mvc.perform(post("/api/reservas").with(quien).header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions agregarTarjeta(RequestPostProcessor quien, String clave, String numero) throws Exception {
        return mvc.perform(post(TARJETAS).with(quien).header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON).content(tarjeta(numero)));
    }

    private String tarjeta(String numero) throws Exception {
        return json.writeValueAsString(Map.of("titular", "CLIENTE DE PRUEBA", "numeroTarjeta", numero,
                "vencimiento", "12/35", "codigoSeguridad", "123"));
    }

    private RequestPostProcessor cliente() {
        return como(cliente.getEmail(), Rol.CLIENTE);
    }
}
