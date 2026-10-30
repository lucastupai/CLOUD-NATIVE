package cl.duoc.vidasalud.notifications.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AdmissionTicketService {
    private static final Logger log = LoggerFactory.getLogger(AdmissionTicketService.class);

    public void processAdmissionTicket(JsonNode event) {
        if (event == null || !event.isObject()) {
            throw new IllegalArgumentException("Payload de admisión inválido o nulo");
        }
        if (event.path("simulateFailure").asBoolean(false) || event.path("payload").path("simulateFailure").asBoolean(false)) {
            throw new IllegalArgumentException("Simulación de fallo para prueba de DLQ en flujo de Admisión / Box");
        }

        JsonNode payload = event.has("payload") ? event.get("payload") : event;
        String eventType = event.path("type").asText("ADMISSION_ACTION");
        String eventId = event.path("eventId").asText("N/A");
        long appointmentId = payload.path("appointmentId").asLong(0);
        long boxId = payload.path("boxId").asLong(0);
        String patientName = payload.path("patientName").asText("Paciente");
        String ticketType = payload.path("ticketType").asText("TICKET_CLINICO");

        log.info("[ADMISSION-SERVICE] Ticket emitido para Box | EventId: {} | Tipo: {} | Cita: #{} | Paciente: '{}' | Box Asignado: #{} | Ticket: '{}'",
                eventId, eventType, appointmentId, patientName, boxId, ticketType);
    }
}
