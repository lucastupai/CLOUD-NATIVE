package cl.duoc.vidasalud.notifications.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    public void processEmail(JsonNode event) {
        if (event == null || !event.isObject()) {
            throw new IllegalArgumentException("Payload de evento inválido o nulo");
        }
        if (event.path("simulateFailure").asBoolean(false) || event.path("payload").path("simulateFailure").asBoolean(false)) {
            throw new IllegalArgumentException("Simulación de fallo para prueba de DLQ en flujo de Email");
        }

        JsonNode payload = event.has("payload") ? event.get("payload") : event;
        String eventType = event.path("type").asText("EMAIL_DISPATCH");
        String eventId = event.path("eventId").asText("N/A");
        String recipient = payload.path("patientEmail").asText(payload.path("email").asText("sin-email@vidasalud.cl"));
        String subject = payload.path("notificationSubject").asText("Notificación VidaSalud");
        long appointmentId = payload.path("appointmentId").asLong(0);

        log.info("[EMAIL-SERVICE] Notificación procesada con éxito | EventId: {} | Tipo: {} | Cita: #{} | Destinatario: {} | Asunto: '{}'",
                eventId, eventType, appointmentId, recipient, subject);
    }
}
