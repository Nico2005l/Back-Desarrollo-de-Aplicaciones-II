package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.seguridad.filtraciones.consultar-externo=true")
@Import(PwnedPasswordsCaidoIntegracionTest.TimeoutExternoConfig.class)
class PwnedPasswordsCaidoIntegracionTest extends IntegracionBase {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;

    @Test
    void registroValidoSeCompletaAunqueLaConsultaExternaFalle() throws Exception {
        String email = "pwned-caido-" + UUID.randomUUID() + "@pruebas.plantalibre.local";
        Map<String, String> registro = Map.of(
                "nombre", "Prueba Pwned",
                "email", email,
                "telefono", "+54 11 5555 0011",
                "password", "Unica-Prueba-8Kzq29",
                "rol", "CLIENTE");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(registro)))
                .andExpect(status().isCreated());

        assertThat(usuarios.findByEmail(email)).isPresent();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeoutExternoConfig {

        @Bean
        RestClientCustomizer simularTimeoutHttp() {
            return builder -> builder.requestInterceptor((request, body, execution) -> {
                throw new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"));
            });
        }
    }
}
