package com.daii.backdaii.web;

import com.daii.backdaii.exception.Problemas;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Idempotency-Key on the POST endpoints marked {@link Idempotente}. Runs after Spring Security,
 * so the key belongs to the signed-in user: two users can use the same key without clashing.
 *
 * <ul>
 *   <li>First request with a key: the key is reserved, the request runs and its answer is stored.</li>
 *   <li>Same key, same request again: the stored answer is returned as is, with
 *       {@code Idempotent-Replayed: true}; nothing is created a second time.</li>
 *   <li>Same key while the first request is still running: 409.</li>
 *   <li>Same key with another body or another endpoint: 422.</li>
 *   <li>The first request failed on the server (5xx): the key is freed so the client can retry with it.</li>
 * </ul>
 */
@Component
public class IdempotenciaFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotenciaFilter.class);
    static final String REPETIDA = "Idempotent-Replayed";
    private static final Pattern CLAVE_VALIDA = Pattern.compile("[\\x21-\\x7E]{8,255}");

    private final AlmacenIdempotencia almacen;
    private final ObjectMapper json;
    private final ObjectProvider<RequestMappingHandlerMapping> rutas;

    public IdempotenciaFilter(AlmacenIdempotencia almacen, ObjectMapper json,
                              @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> rutas) {
        this.almacen = almacen;
        this.json = json;
        this.rutas = rutas;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || request.getHeader(Idempotente.CABECERA) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String usuario = usuario();
        if (usuario == null || !idempotente(request)) {
            // Not signed in (the controller answers 401/403) or an endpoint without idempotency.
            chain.doFilter(request, response);
            return;
        }
        String clave = request.getHeader(Idempotente.CABECERA).trim();
        if (!CLAVE_VALIDA.matcher(clave).matches()) {
            error(request, response, HttpStatus.BAD_REQUEST,
                    "La Idempotency-Key tiene que tener entre 8 y 255 caracteres visibles, sin espacios.",
                    "IDEMPOTENCY_KEY_INVALIDA");
            return;
        }

        byte[] cuerpo = request.getInputStream().readAllBytes();
        String ruta = request.getRequestURI();
        String huella = huella(request.getMethod(), ruta, cuerpo);
        Optional<AlmacenIdempotencia.Previa> previa = almacen.reservar(usuario, clave, request.getMethod(), ruta, huella);
        if (previa.isPresent()) {
            responderConPrevia(request, response, previa.get(), huella);
            return;
        }

        ContentCachingResponseWrapper respuesta = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(new CuerpoLeido(request, cuerpo), respuesta);
        } catch (IOException | ServletException | RuntimeException e) {
            almacen.liberar(usuario, clave);
            throw e;
        }
        if (respuesta.getStatus() >= 500) {
            almacen.liberar(usuario, clave);
        } else {
            almacen.completar(usuario, clave, respuesta.getStatus(), respuesta.getContentType(),
                    new String(respuesta.getContentAsByteArray(), StandardCharsets.UTF_8));
        }
        respuesta.copyBodyToResponse();
    }

    /** Answers older than 24 hours are no longer replayed; this only keeps the table small. */
    @Scheduled(cron = "${app.idempotencia.purga-cron:0 30 4 * * *}")
    void purgar() {
        int borradas = almacen.purgarVencidas();
        if (borradas > 0) {
            log.info("Idempotency-Key: {} claves vencidas borradas", borradas);
        }
    }

    private void responderConPrevia(HttpServletRequest request, HttpServletResponse response,
                                    AlmacenIdempotencia.Previa previa, String huella) throws IOException {
        if (!previa.huella().equals(huella)) {
            error(request, response, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Esta Idempotency-Key ya se usó con otro pedido. Generá una clave nueva para cada operación.",
                    "IDEMPOTENCY_KEY_REUTILIZADA");
        } else if (previa.enCurso()) {
            error(request, response, HttpStatus.CONFLICT,
                    "Ya hay un pedido en curso con esta Idempotency-Key. Esperá su respuesta.",
                    "IDEMPOTENCY_KEY_EN_CURSO");
        } else {
            response.setStatus(previa.status());
            if (previa.tipoContenido() != null) {
                response.setContentType(previa.tipoContenido());
            }
            // The stored body is text; it goes back as the same UTF-8 bytes the first answer had.
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader(REPETIDA, "true");
            if (previa.cuerpo() != null) {
                response.getOutputStream().write(previa.cuerpo().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void error(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String mensaje,
                       String codigo) throws IOException {
        Problemas.escribir(response, json, request.getRequestURI(), Problemas.de(status, mensaje, codigo));
    }

    private boolean idempotente(HttpServletRequest request) {
        try {
            HandlerExecutionChain destino = rutas.getObject().getHandler(request);
            return destino != null && destino.getHandler() instanceof HandlerMethod metodo
                    && metodo.hasMethodAnnotation(Idempotente.class);
        } catch (Exception e) {
            return false;
        }
    }

    private static String usuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getName();
    }

    private static String huella(String metodo, String ruta, byte[] cuerpo) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update((metodo + " " + ruta + "\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha.digest(cuerpo));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** The request with its body already read, so the controller can read it again. */
    private static final class CuerpoLeido extends HttpServletRequestWrapper {

        private final byte[] cuerpo;

        CuerpoLeido(HttpServletRequest request, byte[] cuerpo) {
            super(request);
            this.cuerpo = cuerpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream datos = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return datos.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return datos.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return datos.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Lectura asíncrona no soportada");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return cuerpo.length;
        }

        @Override
        public long getContentLengthLong() {
            return cuerpo.length;
        }
    }
}
