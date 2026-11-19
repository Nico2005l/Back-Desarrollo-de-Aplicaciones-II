package com.daii.backdaii.service.impl;

import com.daii.backdaii.exception.PasarelaNoDisponibleException;
import com.daii.backdaii.service.PasarelaPagos;
import com.daii.backdaii.service.PasarelaPagos.ResultadoPago;
import com.daii.backdaii.service.PasarelaPagos.SolicitudPago;
import com.daii.backdaii.service.PasarelaPagos.SolicitudReintegro;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The time limit and the circuit breaker around the payment provider, with a provider we control. */
class PasarelaPagosResilienteTest {

    private static final SolicitudPago PAGO = new SolicitudPago("R-1", new BigDecimal("3500.00"), "4242", "Ana");
    private static final SolicitudReintegro REINTEGRO = new SolicitudReintegro("SIM-1", new BigDecimal("3500.00"));

    private PasarelaPagosResiliente pasarela;

    @AfterEach
    void cerrar() {
        if (pasarela != null) {
            pasarela.close();
        }
    }

    @Test
    void unaRespuestaATiempoPasaSinCambios() {
        pasarela = resiliente(proveedor(0, null, ResultadoPago.aprobado("SIM-OK")), 500, 30);

        assertThat(pasarela.cobrar(PAGO)).isEqualTo(ResultadoPago.aprobado("SIM-OK"));
        assertThat(pasarela.reintegrar(REINTEGRO)).isEqualTo(ResultadoPago.aprobado("SIM-OK"));
    }

    @Test
    void unRechazoNoEsUnaFallaYNoAbreElCircuito() {
        pasarela = resiliente(proveedor(0, null, ResultadoPago.rechazado("Fondos insuficientes")), 500, 30);

        for (int i = 0; i < 10; i++) {
            assertThat(pasarela.cobrar(PAGO).aprobado()).isFalse();
        }
        assertThat(pasarela.estadoCircuito()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void unProveedorQueNoRespondeSeCortaEnElLimiteYSuHiloSeInterrumpe() throws Exception {
        CountDownLatch interrumpido = new CountDownLatch(1);
        PasarelaPagos lento = new PasarelaPagos() {
            @Override
            public ResultadoPago cobrar(SolicitudPago solicitud) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    interrumpido.countDown();
                    Thread.currentThread().interrupt();
                }
                return ResultadoPago.aprobado("tarde");
            }

            @Override
            public ResultadoPago reintegrar(SolicitudReintegro solicitud) {
                return cobrar(null);
            }
        };
        pasarela = resiliente(lento, 200, 30);

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> pasarela.cobrar(PAGO))
                .isInstanceOf(PasarelaNoDisponibleException.class)
                .satisfies(e -> assertThat(((PasarelaNoDisponibleException) e).getMotivo()).contains("200 ms"))
                .satisfies(e -> assertThat(((PasarelaNoDisponibleException) e).getStatus().value()).isEqualTo(503));
        long transcurridoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

        assertThat(transcurridoMs).isBetween(150L, 2_000L);
        // No hung thread: the provider's call was interrupted when the limit cut it.
        assertThat(interrumpido.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void unaFallaTecnicaDelProveedorLlegaComoPasarelaNoDisponible() {
        pasarela = resiliente(proveedor(0, new IllegalStateException("La pasarela de pagos no respondió"), null), 500, 30);

        assertThatThrownBy(() -> pasarela.cobrar(PAGO))
                .isInstanceOf(PasarelaNoDisponibleException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(((PasarelaNoDisponibleException) e).getMotivo()).isEqualTo("error del proveedor"));
    }

    @Test
    void conLaMitadDeLasLlamadasFallidasElCircuitoSeAbreYNoLlamaAlProveedor() {
        AtomicInteger llamadas = new AtomicInteger();
        AtomicBoolean falla = new AtomicBoolean(true);
        PasarelaPagos inestable = new PasarelaPagos() {
            @Override
            public ResultadoPago cobrar(SolicitudPago solicitud) {
                llamadas.incrementAndGet();
                if (falla.get()) {
                    throw new IllegalStateException("caída");
                }
                return ResultadoPago.aprobado("SIM-OK");
            }

            @Override
            public ResultadoPago reintegrar(SolicitudReintegro solicitud) {
                return cobrar(null);
            }
        };
        pasarela = resiliente(inestable, 500, 30);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> pasarela.cobrar(PAGO)).isInstanceOf(PasarelaNoDisponibleException.class);
        }
        assertThat(pasarela.estadoCircuito()).isEqualTo(CircuitBreaker.State.OPEN);

        falla.set(false);
        assertThatThrownBy(() -> pasarela.reintegrar(REINTEGRO))
                .isInstanceOf(PasarelaNoDisponibleException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class)
                .satisfies(e -> assertThat(((PasarelaNoDisponibleException) e).getMotivo()).isEqualTo("circuito abierto"));
        // Open: the provider was not called again.
        assertThat(llamadas.get()).isEqualTo(5);
    }

    @Test
    void elCircuitoVuelveACerrarseCuandoElProveedorSeRecupera() throws Exception {
        AtomicBoolean falla = new AtomicBoolean(true);
        PasarelaPagos inestable = new PasarelaPagos() {
            @Override
            public ResultadoPago cobrar(SolicitudPago solicitud) {
                if (falla.get()) {
                    throw new IllegalStateException("caída");
                }
                return ResultadoPago.aprobado("SIM-OK");
            }

            @Override
            public ResultadoPago reintegrar(SolicitudReintegro solicitud) {
                return cobrar(null);
            }
        };
        pasarela = new PasarelaPagosResiliente(inestable, limite(500), CircuitBreaker.of("prueba", CircuitBreakerConfig.custom()
                .slidingWindowSize(10).minimumNumberOfCalls(5).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMillis(100)).permittedNumberOfCallsInHalfOpenState(2).build()));
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> pasarela.cobrar(PAGO)).isInstanceOf(PasarelaNoDisponibleException.class);
        }

        falla.set(false);
        Thread.sleep(150);
        assertThat(pasarela.cobrar(PAGO).aprobado()).isTrue();
        assertThat(pasarela.cobrar(PAGO).aprobado()).isTrue();
        assertThat(pasarela.estadoCircuito()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private static PasarelaPagosResiliente resiliente(PasarelaPagos destino, long timeoutMs, long abiertoSegundos) {
        return new PasarelaPagosResiliente(destino, limite(timeoutMs), CircuitBreaker.of("prueba", CircuitBreakerConfig.custom()
                .slidingWindowSize(10).minimumNumberOfCalls(5).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(abiertoSegundos)).build()));
    }

    private static TimeLimiter limite(long timeoutMs) {
        return TimeLimiter.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(timeoutMs))
                .cancelRunningFuture(true).build());
    }

    private static PasarelaPagos proveedor(long demoraMs, RuntimeException falla, ResultadoPago resultado) {
        return new PasarelaPagos() {
            @Override
            public ResultadoPago cobrar(SolicitudPago solicitud) {
                if (demoraMs > 0) {
                    try {
                        Thread.sleep(demoraMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (falla != null) {
                    throw falla;
                }
                return resultado;
            }

            @Override
            public ResultadoPago reintegrar(SolicitudReintegro solicitud) {
                return cobrar(null);
            }
        };
    }
}
