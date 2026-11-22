package com.daii.backdaii.integracion;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-01 over real HTTP: MockMvc skips Tomcat, and Tomcat is the one that could add a Server
 * header. Starts the application on a random port, so it needs its own context; the properties
 * repeat the ones of {@link IntegracionBase} because this annotation replaces that one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=pruebas-integracion-jwt-Xk29Qm7Lr5Tv8Wz3Nb6Hc",
        "app.cifrado.clave-actual=pruebas-integracion-cifrado-Ye4Jn8Rp2Gd7Sk9Fw5",
        "spring.datasource.password=la-define-el-contenedor",
        "app.demo.habilitado=false",
        "app.seguridad.filtraciones.consultar-externo=false",
        "spring.jpa.show-sql=false"
})
class CabecerasSeguridadIntegracionTest extends IntegracionBase {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    private int puerto;

    /** Every kind of response carries them: a public one, an error, and a route that does not exist. */
    @ParameterizedTest(name = "{0} responde {1} con las cabeceras de seguridad")
    @CsvSource({
            "/api/health, 200",
            "/api/usuarios/me, 401",
            "/api/no-existe, 401"
    })
    void todaRespuestaTraeLasCabecerasDeSeguridadYNoRevelaElServidor(String ruta, int estado) throws Exception {
        HttpResponse<String> respuesta = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode()).isEqualTo(estado);
        var cabeceras = respuesta.headers();
        assertThat(cabeceras.firstValue("Content-Security-Policy")).hasValueSatisfying(csp ->
                assertThat(csp).contains("default-src 'none'", "frame-ancestors 'none'"));
        assertThat(cabeceras.firstValue("Strict-Transport-Security")).hasValueSatisfying(hsts ->
                assertThat(hsts).contains("max-age=31536000", "includeSubDomains"));
        assertThat(cabeceras.firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(cabeceras.firstValue("X-Frame-Options")).contains("DENY");
        assertThat(cabeceras.firstValue("Referrer-Policy")).contains("no-referrer");
        assertThat(cabeceras.firstValue("Cross-Origin-Resource-Policy")).contains("same-origin");
        assertThat(cabeceras.firstValue("Permissions-Policy")).hasValueSatisfying(politica ->
                assertThat(politica).contains("camera=()", "geolocation=()", "payment=()"));
        assertThat(cabeceras.firstValue("Server")).isEmpty();
        assertThat(cabeceras.firstValue("X-Powered-By")).isEmpty();
    }
}
