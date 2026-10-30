package cl.duoc.vidasalud.notifications.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RecordGenerationService {
    private static final Logger log = LoggerFactory.getLogger(RecordGenerationService.class);

    public void processRecordGeneration(JsonNode event) {
        if (event == null || !event.isObject()) {
            throw new IllegalArgumentException("Payload de registro médico inválido o nulo");
        }
        if (event.path("simulateFailure").asBoolean(false) || event.path("payload").path("simulateFailure").asBoolean(false)) {
            throw new IllegalArgumentException("Simulación de fallo para prueba de DLQ en flujo de Registro / PDF");
        }

        JsonNode payload = event.has("payload") ? event.get("payload") : event;
        String eventType = event.path("type").asText("RECORD_GEN");
        String eventId = event.path("eventId").asText("N/A");
        long appointmentId = payload.path("appointmentId").asLong(0);
        String patientName = payload.path("patientName").asText("Paciente");
        String docType = payload.path("documentType").asText("COMPROBANTE_RESUMEN_ATENCION");
        String docId = "DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("[RECORD-SERVICE] Documento PDF generado exitosamente | EventId: {} | Tipo: {} | Cita: #{} | Paciente: '{}' | Documento: {} | DocId: {}",
                eventId, eventType, appointmentId, patientName, docType, docId);
    }
}
