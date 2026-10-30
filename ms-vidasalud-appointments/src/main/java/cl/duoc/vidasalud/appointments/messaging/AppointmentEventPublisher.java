package cl.duoc.vidasalud.appointments.messaging;

import cl.duoc.vidasalud.appointments.domain.AppointmentStatus;
import cl.duoc.vidasalud.appointments.dto.AppointmentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "vidasalud.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class AppointmentEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(AppointmentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final AppointmentsMessagingProperties properties;

    public AppointmentEventPublisher(RabbitTemplate rabbitTemplate, AppointmentsMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    /**
     * Evento al crear una nueva atención (SOLICITADA).
     */
    public void publishCreationEvents(AppointmentResponse appointment) {
        String correlationId = "app-" + appointment.id();
        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.of(
                "APPOINTMENT_REQUESTED",
                Map.of(
                        "appointmentId", appointment.id(),
                        "patientId", appointment.patientId() != null ? appointment.patientId() : "",
                        "patientName", appointment.patientName(),
                        "patientEmail", appointment.patientEmail(),
                        "serviceId", appointment.serviceId(),
                        "status", appointment.status().name(),
                        "scheduledAt", appointment.scheduledAt().toString(),
                        "notificationSubject", "Solicitud de atención médica recibida",
                        "notificationMessage", "Estimado(a) " + appointment.patientName() + ", su solicitud para la prestación #"
                                + appointment.serviceId() + " ha sido recibida y se encuentra SOLICITADA."
                ),
                correlationId
        );

        // Envío directo al flujo de email
        sendAfterCommit(properties.directExchange(), properties.emailRoutingKey(), envelope);
        // Envío complementario por topic para consumidores con comodines
        sendAfterCommit(properties.topicExchange(), "email.solicitada", envelope);
    }

    /**
     * Eventos accionados al cambiar de estado una cita.
     */
    public void publishStatusTransitionEvents(AppointmentResponse appointment, AppointmentStatus from, AppointmentStatus to) {
        String correlationId = "app-" + appointment.id();

        switch (to) {
            case CONFIRMADA -> {
                // 1. Notificación email al paciente
                EventEnvelope<Map<String, Object>> emailEnv = EventEnvelope.of(
                        "APPOINTMENT_CONFIRMED",
                        Map.of(
                                "appointmentId", appointment.id(),
                                "patientName", appointment.patientName(),
                                "patientEmail", appointment.patientEmail(),
                                "boxId", appointment.boxId() != null ? appointment.boxId() : 0,
                                "scheduledAt", appointment.scheduledAt().toString(),
                                "notificationSubject", "Confirmación de cita médica - VidaSalud",
                                "notificationMessage", "Su cita ha sido confirmada en el Box #" + appointment.boxId() + "."
                        ),
                        correlationId
                );
                sendAfterCommit(properties.directExchange(), properties.emailRoutingKey(), emailEnv);
                sendAfterCommit(properties.topicExchange(), "email.confirmada", emailEnv);

                // 2. Ticket de admisión al box clínico
                EventEnvelope<Map<String, Object>> admissionEnv = EventEnvelope.of(
                        "ADMISSION_TICKET_GENERATED",
                        Map.of(
                                "appointmentId", appointment.id(),
                                "boxId", appointment.boxId() != null ? appointment.boxId() : 0,
                                "patientName", appointment.patientName(),
                                "serviceId", appointment.serviceId(),
                                "ticketType", "BOX_RESERVATION",
                                "scheduledAt", appointment.scheduledAt().toString()
                        ),
                        correlationId
                );
                sendAfterCommit(properties.directExchange(), properties.admissionRoutingKey(), admissionEnv);
                sendAfterCommit(properties.topicExchange(), "admission.ticket.confirmada", admissionEnv);
            }
            case EN_ESPERA -> {
                // Recepcionista marca llegada -> Ticket de admisión en sala de espera
                EventEnvelope<Map<String, Object>> admissionEnv = EventEnvelope.of(
                        "PATIENT_WAITING",
                        Map.of(
                                "appointmentId", appointment.id(),
                                "boxId", appointment.boxId() != null ? appointment.boxId() : 0,
                                "patientName", appointment.patientName(),
                                "status", "EN_ESPERA",
                                "ticketType", "WAITING_ROOM"
                        ),
                        correlationId
                );
                sendAfterCommit(properties.directExchange(), properties.admissionRoutingKey(), admissionEnv);
                sendAfterCommit(properties.topicExchange(), "admission.ticket.espera", admissionEnv);
            }
            case EN_ATENCION -> {
                // Llamado a box clínico
                EventEnvelope<Map<String, Object>> admissionEnv = EventEnvelope.of(
                        "CALL_TO_BOX",
                        Map.of(
                                "appointmentId", appointment.id(),
                                "boxId", appointment.boxId() != null ? appointment.boxId() : 0,
                                "patientName", appointment.patientName(),
                                "action", "LLAMAR_A_BOX"
                        ),
                        correlationId
                );
                sendAfterCommit(properties.directExchange(), properties.admissionRoutingKey(), admissionEnv);
                sendAfterCommit(properties.topicExchange(), "admission.ticket.llamado", admissionEnv);

                // Push / Email al paciente informando el llamado
                EventEnvelope<Map<String, Object>> emailEnv = EventEnvelope.of(
                        "CALL_NOTIFICATION",
                        Map.of(
                                "appointmentId", appointment.id(),
                                "patientEmail", appointment.patientEmail(),
                                "patientName", appointment.patientName(),
                                "notificationSubject", "Llamado a Box Clínico - VidaSalud",
                                "notificationMessage", "Por favor acérquese de inmediato al Box #" + appointment.boxId() + "."
                        ),
                        correlationId
                );
                sendAfterCommit(properties.directExchange(), properties.emailRoutingKey(), emailEnv);
                sendAfterCommit(properties.topicExchange(), "email.llamado", emailEnv);
            }
            case CERRADA -> {
                // 1. Generación de PDF resumen/comprobante de atención
                EventEnvelope<Map<String, Object>> recordEnv = EventEnvelope.of(
                        "GENERATE_RECORD_PDF",
                        Map.of(
                                "appointmentId", appointment.id(),
                                "patientName", appointment.patientName(),
                                "serviceId", appointment.serviceId(),
                                "boxId", appointment.boxId() != null ? appointment.boxId() : 0,
                                "documentType", "RESUMEN_DE_ATENCION_CLINICA"
                        ),
                        correlationId
                );
                sendAfterCommit(properties.directExchange(), properties.recordRoutingKey(), recordEnv);
                sendAfterCommit(properties.topicExchange(), "record.gen.resumen", recordEnv);

                // 2. Email al paciente con cierre de atención
                EventEnvelope<Map<String, Object>> emailEnv = EventEnvelope.of(
                        "APPOINTMENT_CLOSED",
                        Map.of(
                                "appointmentId", appointment.id(),
                                "patientEmail", appointment.patientEmail(),
                                "patientName", appointment.patientName(),
                                "notificationSubject", "Comprobante de atención finalizada - VidaSalud",
                                "notificationMessage", "Su atención médica ha sido completada. Se ha generado su resumen de atención."
                        ),
                        correlationId
                );
                sendAfterCommit(properties.directExchange(), properties.emailRoutingKey(), emailEnv);
                sendAfterCommit(properties.topicExchange(), "email.cerrada", emailEnv);
            }
            case CANCELADA -> {
                // Notificación de cancelación al paciente
                EventEnvelope<Map<String, Object>> emailEnv = EventEnvelope.of(
                        "APPOINTMENT_CANCELLED",
                        Map.of(
                                "appointmentId", appointment.id(),
                                "patientEmail", appointment.patientEmail(),
                                "patientName", appointment.patientName(),
                                "notificationSubject", "Cancelación de atención médica - VidaSalud",
                                "notificationMessage", "Su atención médica #" + appointment.id() + " ha sido CANCELADA."
                        ),
                        correlationId
                );
                sendAfterCommit(properties.directExchange(), properties.emailRoutingKey(), emailEnv);
                sendAfterCommit(properties.topicExchange(), "email.cancelada", emailEnv);
            }
        }
    }

    private void sendAfterCommit(String exchange, String routingKey, EventEnvelope<?> envelope) {
        Runnable task = () -> {
            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, envelope, msg -> {
                    msg.getMessageProperties().setMessageId(envelope.eventId());
                    msg.getMessageProperties().setCorrelationId(envelope.correlationId());
                    msg.getMessageProperties().setContentType("application/json");
                    msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    msg.getMessageProperties().setHeader("X-Trace-Id", envelope.traceId());
                    msg.getMessageProperties().setHeader("X-Event-Type", envelope.type());
                    return msg;
                });
                log.info("Mensaje publicado exitosamente en exchange='{}' rkey='{}' eventId='{}' tipo='{}'",
                        exchange, routingKey, envelope.eventId(), envelope.type());
            } catch (Exception ex) {
                log.error("Error al publicar mensaje a RabbitMQ exchange='{}' rkey='{}': {}",
                        exchange, routingKey, ex.getMessage(), ex);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
