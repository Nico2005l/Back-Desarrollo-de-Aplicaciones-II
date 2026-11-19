package com.daii.backdaii.config;

import com.daii.backdaii.domain.cobro.Tarifario;
import com.daii.backdaii.domain.enums.TipoEspacio;
import com.daii.backdaii.service.PasarelaPagos;
import com.daii.backdaii.service.impl.PasarelaPagosResiliente;
import com.daii.backdaii.service.impl.PasarelaPagosSimulada;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Configuration
public class CobroConfig {

    /** Hourly rates per space kind, from app.tarifas.por-hora. Fails on startup if one is missing. */
    @Bean
    public Tarifario tarifario(Environment env) {
        Map<TipoEspacio, BigDecimal> porHora = Binder.get(env)
                .bind("app.tarifas.por-hora", Bindable.mapOf(TipoEspacio.class, BigDecimal.class))
                .orElse(Map.of());
        return new Tarifario(porHora);
    }

    /**
     * What the rest of the app gets when it asks for the payment provider: the adapter wrapped with
     * a time limit and a circuit breaker (see PasarelaPagosResiliente).
     */
    @Bean
    @Primary
    public PasarelaPagos pasarelaPagos(PasarelaPagosSimulada adaptador,
                                       @Value("${app.pasarela.timeout-ms:5000}") long timeoutMs,
                                       @Value("${app.pasarela.circuito.abierto-segundos:30}") long abiertoSegundos) {
        TimeLimiter limite = TimeLimiter.of("pasarela-pagos", TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(timeoutMs))
                .cancelRunningFuture(true)
                .build());
        // Opens when half of the last 10 calls failed (counting from the 5th), stays open the given
        // seconds and then lets 2 trial calls through.
        CircuitBreaker circuito = CircuitBreaker.of("pasarela-pagos", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(abiertoSegundos))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build());
        return new PasarelaPagosResiliente(adaptador, limite, circuito);
    }
}
