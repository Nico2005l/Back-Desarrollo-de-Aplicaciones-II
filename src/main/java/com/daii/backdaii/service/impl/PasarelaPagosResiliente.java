package com.daii.backdaii.service.impl;

import com.daii.backdaii.exception.PasarelaNoDisponibleException;
import com.daii.backdaii.service.PasarelaPagos;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * Decorator of the payment provider port. Every call runs on its own (virtual) thread and is cut
 * at the time limit (5 s): the call is interrupted, so a provider that never answers does not keep
 * a thread hanging. Calls also go through a circuit breaker: when most recent calls failed, the
 * next ones fail at once for a while instead of waiting the full timeout each.
 *
 * Every technical failure surfaces as PasarelaNoDisponibleException and the caller applies its
 * fallback: the charge consumer rolls back and the message is retried, then the sweep requests it
 * again and finally cancels the booking with a mail; a cancellation answers 503 and nothing changes.
 * Declines are not failures: they come back as a result, as before.
 */
public class PasarelaPagosResiliente implements PasarelaPagos, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PasarelaPagosResiliente.class);

    private final PasarelaPagos destino;
    private final TimeLimiter limite;
    private final CircuitBreaker circuito;
    private final ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();

    public PasarelaPagosResiliente(PasarelaPagos destino, TimeLimiter limite, CircuitBreaker circuito) {
        this.destino = destino;
        this.limite = limite;
        this.circuito = circuito;
    }

    @Override
    public ResultadoPago cobrar(SolicitudPago solicitud) {
        return llamar("cobro", () -> destino.cobrar(solicitud));
    }

    @Override
    public ResultadoPago reintegrar(SolicitudReintegro solicitud) {
        return llamar("reintegro", () -> destino.reintegrar(solicitud));
    }

    CircuitBreaker.State estadoCircuito() {
        return circuito.getState();
    }

    private ResultadoPago llamar(String operacion, Callable<ResultadoPago> llamada) {
        try {
            return circuito.executeCallable(() -> limite.executeFutureSupplier(() -> hilos.submit(llamada)));
        } catch (CallNotPermittedException e) {
            throw fallo(operacion, "circuito abierto", e);
        } catch (TimeoutException e) {
            throw fallo(operacion, "sin respuesta en " + limite.getTimeLimiterConfig().getTimeoutDuration().toMillis() + " ms", e);
        } catch (ExecutionException e) {
            throw fallo(operacion, "error del proveedor", e.getCause() != null ? e.getCause() : e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fallo(operacion, "espera interrumpida", e);
        } catch (Exception e) {
            throw fallo(operacion, "error del proveedor", e);
        }
    }

    private PasarelaNoDisponibleException fallo(String operacion, String motivo, Throwable causa) {
        log.warn("Pasarela de pagos: {} fallido ({}); circuito {}", operacion, motivo, circuito.getState());
        return new PasarelaNoDisponibleException(motivo, causa);
    }

    @Override
    public void close() {
        hilos.shutdownNow();
    }
}
