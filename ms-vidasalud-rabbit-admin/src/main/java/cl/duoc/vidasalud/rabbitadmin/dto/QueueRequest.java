package cl.duoc.vidasalud.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record QueueRequest(
        @NotBlank(message = "El nombre de la cola es obligatorio y no puede estar vacío")
        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{1,127}$",
                message = "El nombre de la cola debe iniciar con un carácter alfanumérico y solo puede contener letras, números, puntos, guiones y guiones bajos (hasta 128 caracteres)")
        String name,

        boolean durable
) {}
