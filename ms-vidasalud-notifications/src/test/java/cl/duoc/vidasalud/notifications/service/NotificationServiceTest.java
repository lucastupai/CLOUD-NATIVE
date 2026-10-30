package cl.duoc.vidasalud.notifications.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationServiceTest {
    private final EmailNotificationService emailService = new EmailNotificationService();
    private final AdmissionTicketService admissionService = new AdmissionTicketService();
    private final RecordGenerationService recordService = new RecordGenerationService();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void processesEmailNotificationSuccessfully() throws Exception {
        String json = """
                {
                    "type": "APPOINTMENT_CONFIRMED",
                    "eventId": "evt-001",
                    "payload": {
                        "appointmentId": 1001,
                        "patientEmail": "paciente@vidasalud.cl",
                        "notificationSubject": "Cita confirmada"
                    }
                }
                """;
        assertThatCode(() -> emailService.processEmail(mapper.readTree(json))).doesNotThrowAnyException();
    }

    @Test
    void processesAdmissionTicketSuccessfully() throws Exception {
        String json = """
                {
                    "type": "ADMISSION_TICKET_GENERATED",
                    "eventId": "evt-002",
                    "payload": {
                        "appointmentId": 1002,
                        "boxId": 5,
                        "patientName": "Juan Perez"
                    }
                }
                """;
        assertThatCode(() -> admissionService.processAdmissionTicket(mapper.readTree(json))).doesNotThrowAnyException();
    }

    @Test
    void processesRecordGenerationSuccessfully() throws Exception {
        String json = """
                {
                    "type": "GENERATE_RECORD_PDF",
                    "eventId": "evt-003",
                    "payload": {
                        "appointmentId": 1003,
                        "patientName": "Maria Lopez",
                        "documentType": "RESUMEN_CLINICO"
                    }
                }
                """;
        assertThatCode(() -> recordService.processRecordGeneration(mapper.readTree(json))).doesNotThrowAnyException();
    }

    @Test
    void rejectsSimulatedFailureForDlq() throws Exception {
        String json = """
                {
                    "eventId": "evt-999",
                    "simulateFailure": true,
                    "payload": { "appointmentId": 999 }
                }
                """;
        assertThatThrownBy(() -> emailService.processEmail(mapper.readTree(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DLQ");
    }
}
