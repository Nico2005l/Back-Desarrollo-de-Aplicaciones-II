package com.daii.backdaii.integracion;

import com.daii.backdaii.domain.cobro.Tarifario;
import com.daii.backdaii.domain.disponibilidad.Franja;
import com.daii.backdaii.domain.entity.Usuario;
import com.daii.backdaii.domain.enums.EstadoReserva;
import com.daii.backdaii.domain.enums.EstadoUsuario;
import com.daii.backdaii.domain.enums.Rol;
import com.daii.backdaii.domain.repository.CobroRepository;
import com.daii.backdaii.domain.repository.EspacioRepository;
import com.daii.backdaii.domain.repository.MedioPagoRepository;
import com.daii.backdaii.domain.repository.ReservaRepository;
import com.daii.backdaii.domain.repository.UsuarioRepository;
import com.daii.backdaii.service.CobroService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeguridadApiIntegracionTest extends IntegracionBase {

    private static final String EMISOR = "back-daii";
    private static final String CLAVE_JWT = "pruebas-integracion-jwt-Xk29Qm7Lr5Tv8Wz3Nb6Hc";
    private static final String CLAVE_LOGIN = "Prueba-Seguridad-7392";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ReservaRepository reservas;
    @Autowired private CobroRepository cobros;
    @Autowired private EspacioRepository espacios;
    @Autowired private MedioPagoRepository mediosPagos;
    @Autowired private PasswordEncoder encoder;
    @Autowired private Clock reloj;
    @Autowired private CobroService cobroService;
    @Autowired private Tarifario tarifario;

    private Escenario escenario;
    private Usuario cliente;

    @BeforeEach
    void preparar() {
        escenario = new Escenario(usuarios, reservas, espacios, mediosPagos, reloj);
        cliente = usuarios.save(Usuario.builder()
                .nombre("Cliente seguridad")
                .email("seguridad-" + UUID.randomUUID() + "@pruebas.plantalibre.local")
                .password(encoder.encode(CLAVE_LOGIN))
                .rol(Rol.CLIENTE)
                .estado(EstadoUsuario.ACTIVO)
                .build());
    }

    @Test
    void tokensVencidosConOtraClaveAlgNoneOtroEmisorOSinEmisorResponden401() throws Exception {
        String tokenValido = tokenDeLogin(cliente);
        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValido))
                .andExpect(status().isOk());
        Claims claims = Jwts.parser().verifyWith(claveAplicacion()).build().parseSignedClaims(tokenValido).getPayload();
        UUID familia = UUID.fromString(claims.get("sid", String.class));
        Date ahora = new Date();
        Date iat = new Date(ahora.getTime() - 60_000);

        String vencido = token(cliente, familia, claveAplicacion(), EMISOR, iat,
                new Date(ahora.getTime() - 1_000));
        String otraClave = token(cliente, familia,
                Keys.hmacShaKeyFor("otra-clave-de-integracion-jwt-9Q2s5Vx8".getBytes(StandardCharsets.UTF_8)),
                EMISOR, ahora, new Date(ahora.getTime() + 60_000));
        String otroEmisor = token(cliente, familia, claveAplicacion(), "https://otro-emisor.invalid",
                ahora, new Date(ahora.getTime() + 60_000));
        String sinEmisor = token(cliente, familia, claveAplicacion(), null,
                ahora, new Date(ahora.getTime() + 60_000));
        String sinFirma = tokenSinFirma(cliente, familia, ahora, new Date(ahora.getTime() + 60_000));

        assertNoAutenticado(vencido);
        assertNoAutenticado(otraClave);
        assertNoAutenticado(sinFirma);
        assertNoAutenticado(otroEmisor);
        assertNoAutenticado(sinEmisor);
    }

    @Test
    void camposDeRolEstadoYMontoNoSeAsignanDesdeRequestsDeCliente() throws Exception {
        Map<String, Object> perfil = new LinkedHashMap<>();
        perfil.put("telefono", "+54 11 4444 1111");
        perfil.put("rol", "ADMIN");
        perfil.put("estado", "BLOQUEADO");
        perfil.put("monto", 0);
        mvc.perform(patch("/api/usuarios/me").with(IntegracionBase.como(cliente.getEmail(), Rol.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(perfil)))
                .andExpect(status().isOk());

        Usuario guardado = usuarios.findById(cliente.getId()).orElseThrow();
        assertThat(guardado.getRol()).isEqualTo(Rol.CLIENTE);
        assertThat(guardado.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);

        LocalDateTime inicio = escenario.franjaReservable();
        var espacio = escenario.espacio(Escenario.ESPACIO_API);
        BigDecimal montoCalculado = tarifario.monto(espacio.getTipo(), new Franja(inicio, inicio.plusHours(1)));
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("espacioId", espacio.getId());
        pedido.put("inicio", inicio.toString());
        pedido.put("fin", inicio.plusHours(1).toString());
        pedido.put("estado", "CONFIRMADA");
        pedido.put("monto", 0);
        pedido.put("rol", "ADMIN");
        JsonNode creada = json.readTree(mvc.perform(post("/api/reservas")
                        .with(IntegracionBase.como(cliente.getEmail(), Rol.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString());

        Long reservaId = creada.get("id").asLong();
        assertThat(reservas.findById(reservaId).orElseThrow().getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        escenario.tarjeta(cliente, "4242");
        cobroService.procesar(reservaId, null);
        BigDecimal montoPersistido = cobros.findByReservaId(reservaId).orElseThrow().getMonto();
        assertThat(montoPersistido).isEqualByComparingTo(montoCalculado).isNotZero();
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().getRol()).isEqualTo(Rol.CLIENTE);
    }

    @Test
    void payloadSqlNoModificaLaConsultaNiDevuelveClientesNoCoincidentes() throws Exception {
        LocalDateTime inicio = escenario.franjaReservable();
        escenario.reservaEn(cliente, escenario.espacio(Escenario.ESPACIO_API), inicio, EstadoReserva.PENDIENTE);
        String adminPrueba = "admin-sql-" + UUID.randomUUID() + "@pruebas.plantalibre.local";
        assertThat(usuarios.existsByEmail(cliente.getEmail())).isTrue();

        mvc.perform(get("/api/admin/reservas").with(IntegracionBase.como(adminPrueba, Rol.ADMIN))
                        .param("q", "' OR 1=1 --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        MvcResult resultado = mvc.perform(get("/api/admin/clientes").with(IntegracionBase.como(adminPrueba, Rol.ADMIN))
                        .param("q", "' OR 1=1 --"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode clientes = json.readTree(resultado.getResponse().getContentAsString());
        assertThat(clientes.isArray()).isTrue();
        assertThat(clientes.size()).isZero();
        assertThat(usuarios.existsByEmail(cliente.getEmail())).isTrue();
    }

    @Test
    void contenidoHtmlMaliciosoSeDevuelveComoDatoJsonConNosniff() throws Exception {
        String nombre = "<script>alert('xss')</script>";
        String email = "xss-" + UUID.randomUUID() + "@pruebas.plantalibre.local";
        Map<String, Object> registro = Map.of(
                "nombre", nombre,
                "email", email,
                "telefono", "+54 11 5555 0000",
                "password", CLAVE_LOGIN,
                "rol", "CLIENTE");

        MvcResult respuesta = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(registro)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.nombre").value(nombre))
                .andReturn();

        JsonNode cuerpo = json.readTree(respuesta.getResponse().getContentAsString());
        assertThat(cuerpo.get("nombre").asText()).isEqualTo(nombre);
        assertThat(usuarios.findByEmail(email).orElseThrow().getNombre()).isEqualTo(nombre);
    }

    private String tokenDeLogin(Usuario usuario) throws Exception {
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", usuario.getEmail(), "password", CLAVE_LOGIN))))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(login.getResponse().getContentAsString()).get("token").asText();
    }

    private void assertNoAutenticado(String token) throws Exception {
        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String token(Usuario usuario, UUID familia, SecretKey clave, String emisor, Date iat, Date exp) {
        var constructor = Jwts.builder();
        if (emisor != null) {
            constructor.issuer(emisor);
        }
        return constructor.subject(usuario.getEmail())
                .claim("typ", "acceso").claim("sid", familia.toString())
                .claim("id", usuario.getId()).claim("rol", usuario.getRol().name())
                .issuedAt(iat).expiration(exp).signWith(clave).compact();
    }

    private String tokenSinFirma(Usuario usuario, UUID familia, Date iat, Date exp) throws Exception {
        Map<String, Object> header = Map.of("alg", "none", "typ", "JWT");
        Map<String, Object> payload = Map.of(
                "iss", EMISOR,
                "sub", usuario.getEmail(),
                "typ", "acceso",
                "sid", familia.toString(),
                "id", usuario.getId(),
                "rol", usuario.getRol().name(),
                "iat", iat.getTime() / 1000,
                "exp", exp.getTime() / 1000);
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        return base64.encodeToString(json.writeValueAsBytes(header)) + "."
                + base64.encodeToString(json.writeValueAsBytes(payload)) + ".";
    }

    private static SecretKey claveAplicacion() {
        return Keys.hmacShaKeyFor(CLAVE_JWT.getBytes(StandardCharsets.UTF_8));
    }
}
