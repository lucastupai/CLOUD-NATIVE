package cl.duoc.vidasalud.notifications.consumer.admission;

import cl.duoc.vidasalud.notifications.config.RabbitTopologyProperties;
import cl.duoc.vidasalud.notifications.service.AdmissionTicketService;
import cl.duoc.vidasalud.notifications.service.DlqManualManagementService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor del dominio funcional de Admisión y Tickets de Box Clínico.
 * Escucha la cola q.cmd.admission y gestiona ACK manual y DLQ manual.
 */
@Component
public class AdmissionConsumer {
    private static final Logger log = LoggerFactory.getLogger(AdmissionConsumer.class);

    private final AdmissionTicketService service;
    private final DlqManualManagementService dlqService;
    private final ObjectMapper objectMapper;
    private final RabbitTopologyProperties properties;

    public AdmissionConsumer(AdmissionTicketService service,
                             DlqManualManagementService dlqService,
                             ObjectMapper objectMapper,
                             RabbitTopologyProperties properties) {
        this.service = service;
        this.dlqService = dlqService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @RabbitListener(queues = "${vidasalud.rabbit.queues.admission}", ackMode = "MANUAL")
    public void onAdmissionCommand(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            JsonNode event = objectMapper.readTree(message.getBody());
            service.processAdmissionTicket(event);
            channel.basicAck(deliveryTag, false);
            log.debug("ACK explícito emitido para mensaje admisión tag={}", deliveryTag);
        } catch (Exception ex) {
            log.error("Fallo al procesar ticket de admisión [tag={}]: {}", deliveryTag, ex.getMessage());
            dlqService.forwardToDlq(message, channel, properties.queues().admission(),
                    properties.routingKeys().admissionDlq(), ex);
        }
    }
}
