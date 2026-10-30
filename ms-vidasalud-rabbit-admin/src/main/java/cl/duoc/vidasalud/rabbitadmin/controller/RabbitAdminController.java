package cl.duoc.vidasalud.rabbitadmin.controller;

import cl.duoc.vidasalud.rabbitadmin.dto.*;
import cl.duoc.vidasalud.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints REST para administración dinámica de recursos en RabbitMQ.
 * Expone rutas estándar REST (/queues, /exchanges, /bindings) y rutas de compatibilidad (/api/rabbit-admin/*).
 */
@RestController
@Validated
public class RabbitAdminController {
    private final RabbitAdminService service;

    public RabbitAdminController(RabbitAdminService service) {
        this.service = service;
    }

    // --- 1. GESTIÓN DE COLAS ---

    @PostMapping({"/api/queues", "/queues", "/api/rabbit-admin/queues"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createQueue(@Valid @RequestBody QueueRequest request) {
        service.createQueue(request);
        return Map.of(
                "status", "CREATED",
                "resource", "queue",
                "name", request.name(),
                "durable", request.durable()
        );
    }

    @GetMapping({"/api/queues/{name}", "/queues/{name}", "/api/rabbit-admin/queues/{name}"})
    public ResponseEntity<Map<String, Object>> getQueue(
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$", message = "Nombre de cola inválido") String name) {
        Map<String, Object> info = service.getQueueInfo(name);
        if (info.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Cola no encontrada en el broker"));
        }
        return ResponseEntity.ok(info);
    }

    @DeleteMapping({"/api/queues/{name}", "/queues/{name}", "/api/rabbit-admin/queues/{name}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQueue(
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$", message = "Nombre de cola inválido") String name) {
        service.deleteQueue(name);
    }

    // --- 2. GESTIÓN DE EXCHANGES ---

    @PostMapping({"/api/exchanges", "/exchanges", "/api/rabbit-admin/exchanges"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createExchange(@Valid @RequestBody ExchangeRequest request) {
        service.createExchange(request);
        return Map.of(
                "status", "CREATED",
                "resource", "exchange",
                "name", request.name(),
                "type", request.type()
        );
    }

    @DeleteMapping({"/api/exchanges/{name}", "/exchanges/{name}", "/api/rabbit-admin/exchanges/{name}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExchange(
            @PathVariable @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}$", message = "Nombre de exchange inválido") String name) {
        service.deleteExchange(name);
    }

    // --- 3. GESTIÓN DE BINDINGS ---

    @PostMapping({"/api/bindings", "/bindings", "/api/rabbit-admin/bindings"})
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createBinding(@Valid @RequestBody BindingRequest request) {
        service.createBinding(request);
        return Map.of(
                "status", "CREATED",
                "resource", "binding",
                "queue", request.queue(),
                "exchange", request.exchange(),
                "routingKey", request.routingKey() != null ? request.routingKey() : ""
        );
    }

    @DeleteMapping({"/api/bindings", "/bindings", "/api/rabbit-admin/bindings"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBinding(@Valid @RequestBody BindingRequest request) {
        service.deleteBinding(request);
    }
}
