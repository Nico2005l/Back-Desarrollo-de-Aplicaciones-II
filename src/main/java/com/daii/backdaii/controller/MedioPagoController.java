package com.daii.backdaii.controller;

import com.daii.backdaii.dto.medioPago.MedioPagoRequest;
import com.daii.backdaii.dto.medioPago.MedioPagoResumen;
import com.daii.backdaii.dto.medioPago.MedioPagoUpdateRequest;
import com.daii.backdaii.service.MedioPagoService;
import com.daii.backdaii.service.Versiones;
import com.daii.backdaii.web.Idempotente;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Presentation layer of the payment methods component. Every operation works on the
 * cards of the signed-in user, taken from the token, never from the request.
 */
@RestController
@RequestMapping("/api/usuarios/me/medios-pago")
@RequiredArgsConstructor
public class MedioPagoController {

    private final MedioPagoService medioPagoService;
    private final Versiones versiones;

    @GetMapping
    public ResponseEntity<List<MedioPagoResumen>> listar(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(medioPagoService.listar(user.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedioPagoResumen> obtener(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        MedioPagoResumen medio = medioPagoService.obtener(user.getUsername(), id);
        return ResponseEntity.ok().eTag(Etags.de(versiones.medioPago(id))).body(medio);
    }

    @PostMapping
    @Idempotente
    @Parameter(in = ParameterIn.HEADER, name = Idempotente.CABECERA, description = Idempotente.DESCRIPCION,
            schema = @Schema(type = "string", pattern = Etags.PATRON_CLAVE))
    public ResponseEntity<MedioPagoResumen> agregar(@AuthenticationPrincipal UserDetails user,
                                                    @Valid @RequestBody MedioPagoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(medioPagoService.agregarTarjeta(user.getUsername(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MedioPagoResumen> actualizar(@AuthenticationPrincipal UserDetails user,
                                                       @PathVariable Long id,
                                                       @Parameter(description = Etags.DESCRIPCION_IF_MATCH)
                                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                       @Valid @RequestBody MedioPagoUpdateRequest request) {
        MedioPagoResumen medio = medioPagoService.actualizar(user.getUsername(), id, request, Etags.precondicion(ifMatch));
        return ResponseEntity.ok().eTag(Etags.de(versiones.medioPago(id))).body(medio);
    }

    @PatchMapping("/{id}/principal")
    public ResponseEntity<List<MedioPagoResumen>> marcarPrincipal(@AuthenticationPrincipal UserDetails user,
                                                                  @PathVariable Long id) {
        return ResponseEntity.ok(medioPagoService.marcarPrincipal(user.getUsername(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> eliminar(@AuthenticationPrincipal UserDetails user,
                                                        @PathVariable Long id) {
        medioPagoService.eliminar(user.getUsername(), id);
        return ResponseEntity.ok(Map.of("message", "Medio de pago eliminado correctamente."));
    }
}
