package cl.duoc.vidasalud.appointments.messaging;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Envelope común para eventos distribuidos en RabbitMQ.
 * Sigue buenas prácticas de Cloud Native: type, eventId, timestamp, traceId, correlationId y payload.
 */
public record EventEnvelope<T>(
        String type,
        String eventId,
        String timestamp,
        String traceId,
        String correlationId,
        T payload
) implements Serializable {

    public static <T> EventEnvelope<T> of(String type, T payload, String correlationId) {
        String eventId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        String finalCorrId = (correlationId != null && !correlationId.isBlank()) ? correlationId : eventId;
        return new EventEnvelope<>(type, eventId, Instant.now().toString(), traceId, finalCorrId, payload);
    }
}
