package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.entity.Cobro;
import com.daii.backdaii.domain.entity.MedioPago;
import com.daii.backdaii.domain.entity.Reserva;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoCobro;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.Rol;
import com.daii.backdaii.domain.repository.CobroRepository;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The payment provider takes longer than the time limit. The limit is scaled down (300 ms instead
 * of 5 s, refunds taking 2 s) so the test runs fast; the behaviour is the same.
 */
@TestPropertySource(properties = {
        "app.pasarela.timeout-ms=300",
        "app.pasarela.demora-reintegro-ms=2000"
})
class PasarelaLentaIntegracionTest extends IntegracionBase {

    @Autowired
    private MockMvc mvc;
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

    @Test
    void cancelarConLaPasarelaSinResponderDa503ConRetryAfterYLaReservaSigueActiva() throws Exception {
        Escenario escenario = new Escenario(usuarios, reservas, espacios, medios, reloj);
        Usuario cliente = escenario.cliente();
        MedioPago tarjeta = escenario.tarjeta(cliente, "4242");
        Reserva reserva = escenario.futura(cliente, EstadoReserva.CONFIRMADA);
        cobros.save(Cobro.builder().reserva(reserva).medioPago(tarjeta).monto(new BigDecimal("3500.00"))
                .estado(EstadoCobro.APROBADO).referencia("SIM-PRUEBA").procesadoEl(LocalDateTime.now(reloj)).build());

        long inicio = System.nanoTime();
        mvc.perform(patch("/api/reservas/{id}/cancelar", reserva.getId()).with(como(cliente.getEmail(), Rol.CLIENTE)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PASARELA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no respondió")));
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        // Cut at the limit, well before the provider would have answered.
        assertThat(ms).isLessThan(1_800);
        // Fallback: nothing changed, the customer can try again later.
        assertThat(escenario.estadoGuardado(reserva.getId())).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(cobros.findByReservaId(reserva.getId()).orElseThrow().getMontoReintegrado()).isNull();
    }
}
