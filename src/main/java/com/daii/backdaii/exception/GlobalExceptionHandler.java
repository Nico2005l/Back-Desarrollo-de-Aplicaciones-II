package com.daii.backdaii.exception;

import com.daii.backdaii.domain.enums.TransicionInvalidaException;
import com.daii.backdaii.domain.enums.EventoAuditoria;
import com.daii.backdaii.domain.enums.ResultadoAuditoria;
import com.daii.backdaii.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every error answer is a Problem Details body built by {@link Problemas}. */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditoriaService auditoria;

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex) {
        return Problemas.de(ex.getStatus(), ex.getMessage(), ex.getCode());
    }

    // The provider is down or slow: the client is told when to try again.
    @ExceptionHandler(PasarelaNoDisponibleException.class)
    public ResponseEntity<ProblemDetail> handlePasarela(PasarelaNoDisponibleException ex) {
        log.warn("Pedido sin completar: pasarela de pagos no disponible ({})", ex.getMotivo());
        return ResponseEntity.status(ex.getStatus())
                .header("Retry-After", String.valueOf(PasarelaNoDisponibleException.REINTENTAR_EN_SEGUNDOS))
                .body(Problemas.de(ex.getStatus(), ex.getMessage(), ex.getCode()));
    }

    // More specific than ApiException, so Spring picks this one: the body adds the alternatives.
    @ExceptionHandler(FranjaTomadaException.class)
    public ProblemDetail handleFranjaTomada(FranjaTomadaException ex) {
        ProblemDetail problema = Problemas.de(ex.getStatus(), ex.getMessage(), ex.getCode());
        problema.setProperty("alternativas", ex.getAlternativas());
        return problema;
    }

    @ExceptionHandler(ReservasAfectadasException.class)
    public ProblemDetail handleReservasAfectadas(ReservasAfectadasException ex) {
        ProblemDetail problema = Problemas.de(ex.getStatus(), ex.getMessage(), ex.getCode());
        problema.setProperty("reservas", ex.getReservas());
        return problema;
    }

    /** Invalid state change of a booking (State pattern in EstadoReserva). */
    @ExceptionHandler(TransicionInvalidaException.class)
    public ProblemDetail handleTransicionInvalida(TransicionInvalidaException ex) {
        return Problemas.de(HttpStatus.CONFLICT, ex.getMessage(), TransicionInvalidaException.CODE);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        String message = errors.values().stream().findFirst().orElse("Los datos enviados no son válidos");
        ProblemDetail problema = Problemas.de(HttpStatus.BAD_REQUEST, message);
        problema.setProperty("errors", errors);
        return problema;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleUnreadable(Exception ex) {
        return Problemas.de(HttpStatus.BAD_REQUEST, "El formato de la solicitud no es válido");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return Problemas.de(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos");
    }

    @ExceptionHandler({DisabledException.class, LockedException.class})
    public ProblemDetail handleDisabled(AuthenticationException ex) {
        return Problemas.de(HttpStatus.FORBIDDEN, "La cuenta no está habilitada");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return Problemas.de(HttpStatus.UNAUTHORIZED, "No autenticado");
    }

    // Thrown by @PreAuthorize inside the controller layer, so it arrives here instead of the security filter.
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, jakarta.servlet.http.HttpServletRequest request) {
        auditoria.registrar(EventoAuditoria.ACCESO_DENEGADO, ResultadoAuditoria.DENEGADO, request.getRemoteUser(),
                request.getMethod() + " " + request.getRequestURI());
        return Problemas.de(HttpStatus.FORBIDDEN, "No tenés permiso para esta acción");
    }

    // MissingPathVariableException: an id in the URL that is blank, such as /api/espacios/%20.
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
            MissingPathVariableException.class})
    public ProblemDetail handleMissing(Exception ex) {
        return Problemas.de(HttpStatus.BAD_REQUEST, "Falta un dato obligatorio de la solicitud");
    }

    // A malformed multipart body (for example without its boundary) is the client's mistake.
    @ExceptionHandler(MultipartException.class)
    public ProblemDetail handleMultipart(MultipartException ex) {
        return Problemas.de(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return Problemas.de(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El cuerpo tiene que enviarse como application/json");
    }

    // The client accepts nothing the API can produce, so the answer carries no body.
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethod(HttpRequestMethodNotSupportedException ex) {
        return Problemas.de(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return Problemas.de(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    // Unknown failures keep their detail in the log only, never in the response.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return Problemas.de(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado");
    }
}
