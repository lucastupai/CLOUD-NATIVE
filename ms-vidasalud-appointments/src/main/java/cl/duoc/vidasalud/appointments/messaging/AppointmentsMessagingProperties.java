package cl.duoc.vidasalud.appointments.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vidasalud.messaging")
public record AppointmentsMessagingProperties(
        boolean enabled,
        String directExchange,
        String topicExchange,
        String emailRoutingKey,
        String admissionRoutingKey,
        String recordRoutingKey
) {}
