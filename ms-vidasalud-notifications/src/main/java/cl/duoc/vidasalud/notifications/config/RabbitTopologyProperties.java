package cl.duoc.vidasalud.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vidasalud.rabbit")
public record RabbitTopologyProperties(
        Exchanges exchanges,
        Queues queues,
        RoutingKeys routingKeys
) {
    public record Exchanges(
            String direct,
            String topic,
            String dlx
    ) {}

    public record Queues(
            String email,
            String emailDlq,
            String admission,
            String admissionDlq,
            String record,
            String recordDlq
    ) {}

    public record RoutingKeys(
            String emailDirect,
            String emailTopic,
            String emailDlq,
            String admissionDirect,
            String admissionTopic,
            String admissionDlq,
            String recordDirect,
            String recordTopic,
            String recordDlq
    ) {}
}
