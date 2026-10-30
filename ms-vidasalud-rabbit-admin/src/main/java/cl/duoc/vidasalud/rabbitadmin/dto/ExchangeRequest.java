package cl.duoc.vidasalud.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ExchangeRequest(
        @NotBlank(message = "El nombre del exchange es obligatorio y no puede estar vacío")
        @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{1,127}$",
                message = "El nombre del exchange debe iniciar con carácter alfanumérico y contener caracteres válidos (hasta 128 caracteres)")
        String name,

        @NotBlank(message = "El tipo de exchange es obligatorio")
        @Pattern(regexp = "^(direct|topic|fanout|headers)$",
                message = "El tipo de exchange debe ser uno de los siguientes: direct, topic, fanout, headers")
        String type
) {}
