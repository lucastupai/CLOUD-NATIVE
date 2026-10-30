package cl.duoc.vidasalud.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BindingRequest(
        @NotBlank(message = "El nombre del exchange es obligatorio y no puede estar vacío")
        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{1,127}$", message = "Nombre de exchange inválido")
        String exchange,

        @NotBlank(message = "El nombre de la cola es obligatorio y no puede estar vacío")
        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{1,127}$", message = "Nombre de cola inválido")
        String queue,

        @Pattern(regexp = "^[a-zA-Z0-9.*#_-]{0,128}$", message = "Routing key con formato no válido")
        String routingKey
) {}
