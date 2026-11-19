package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.Rol;
import com.daii.backdaii.dto.auditoria.PaginaAuditoria;
import com.daii.backdaii.security.AlmacenIntentosEnBase;
import com.daii.backdaii.security.JwtTokenProvider;
import com.daii.backdaii.security.LimitadorDeIntentos;
import com.daii.backdaii.security.LimiteIntentos;
import com.daii.backdaii.service.AuditoriaService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-06: the API-wide rate limit and the tighter one for creating bookings, with counters in the
 * database shared by every instance. Each test signs in as a user of its own, because the counters
 * are per user and the rest of the suite must not run into them. SEC-02 (access token lifetime)
 * lives here too: it checks the configured value, not a unit default.
 */
class LimiteTasaIntegracionTest extends IntegracionBase {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AuditoriaService auditoria;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtTokenProvider jwt;

    @Test
    void superadoElLimiteGeneralResponde429ConRetryAfterYQuedaUnaSolaVezEnLaAuditoria() throws Exception {
        String email = usuarioNuevo();
        for (int i = 0; i < LimiteIntentos.API_GENERAL.getMaximo(); i++) {
            mvc.perform(get("/api/empresas").with(como(email, Rol.CLIENTE))).andExpect(status().isOk());
        }

        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/empresas").with(como(email, Rol.CLIENTE)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "60"))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(429));
        }
        // The limit is per client: someone else is still served.
        mvc.perform(get("/api/empresas").with(como(usuarioNuevo(), Rol.CLIENTE))).andExpect(status().isOk());

        PaginaAuditoria registros = auditoria.buscar(EventoAuditoria.LIMITE_SUPERADO, email, 0, 10);
        assertThat(registros.content()).singleElement()
                .satisfies(r -> assertThat(r.detalle()).contains("API_GENERAL", "GET /api/empresas"));
    }

    @Test
    void crearReservasTieneSuPropioLimiteMasBajo() throws Exception {
        String email = usuarioNuevo();
        for (int i = 0; i < LimiteIntentos.CREAR_RESERVA.getMaximo(); i++) {
            // An invalid body is enough: the limit counts the attempt before the controller.
            mvc.perform(post("/api/reservas").with(como(email, Rol.CLIENTE))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(resultado -> assertThat(resultado.getResponse().getStatus()).isNotEqualTo(429));
        }

        mvc.perform(post("/api/reservas").with(como(email, Rol.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
        // Reading is still allowed: only booking hit its limit.
        mvc.perform(get("/api/empresas").with(como(email, Rol.CLIENTE))).andExpect(status().isOk());
    }

    @Test
    void dosInstanciasCompartenElContadorYUnReinicioNoLoBorra() {
        String cliente = "usuario:" + usuarioNuevo();
        LimiteIntentos limite = LimiteIntentos.CREAR_RESERVA;
        // Two instances of the application, each with its own limiter over the same database.
        LimitadorDeIntentos instanciaA = new LimitadorDeIntentos(new AlmacenIntentosEnBase(jdbc));
        LimitadorDeIntentos instanciaB = new LimitadorDeIntentos(new AlmacenIntentosEnBase(jdbc));

        for (int i = 0; i < limite.getMaximo(); i++) {
            assertThat((i % 2 == 0 ? instanciaA : instanciaB).permitir(limite, cliente)).isTrue();
        }
        assertThat(instanciaA.permitir(limite, cliente)).isFalse();
        assertThat(instanciaB.permitir(limite, cliente)).isFalse();

        // A restarted instance starts with a new limiter and still sees the client over the limit.
        LimitadorDeIntentos reiniciada = new LimitadorDeIntentos(new AlmacenIntentosEnBase(jdbc));
        assertThat(reiniciada.agotado(limite, cliente)).isTrue();
    }

    @Test
    void elTokenDeAccesoVenceALosQuinceMinutos() {
        String token = jwt.generateToken(usuarioNuevo(), 1L, Rol.CLIENTE.name(), UUID.randomUUID());

        Claims claims = jwt.leerAcceso(token).orElseThrow();
        assertThat(Duration.between(JwtTokenProvider.emitido(claims), claims.getExpiration().toInstant()))
                .isEqualTo(Duration.ofMinutes(15));
    }

    private static String usuarioNuevo() {
        return "limite-" + UUID.randomUUID() + "@pruebas.plantalibre.local";
    }
}
