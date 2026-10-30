package cl.duoc.vidasalud.notifications.consumer.email;

import cl.duoc.vidasalud.notifications.config.RabbitTopologyProperties;
import cl.duoc.vidasalud.notifications.service.DlqManualManagementService;
import cl.duoc.vidasalud.notifications.service.EmailNotificationService;
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
 * Consumidor del dominio funcional de Email / Notificaciones al paciente.
 * Escucha la cola q.cmd.email y gestiona ACK manual y DLQ manual ante fallos.
 */
@Component
public class EmailConsumer {
    private static final Logger log = LoggerFactory.getLogger(EmailConsumer.class);

    private final EmailNotificationService service;
    private final DlqManualManagementService dlqService;
    private final ObjectMapper objectMapper;
    private final RabbitTopologyProperties properties;

    public EmailConsumer(EmailNotificationService service,
                         DlqManualManagementService dlqService,
                         ObjectMapper objectMapper,
                         RabbitTopologyProperties properties) {
        this.service = service;
        this.dlqService = dlqService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @RabbitListener(queues = "${vidasalud.rabbit.queues.email}", ackMode = "MANUAL")
    public void onEmailCommand(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            JsonNode event = objectMapper.readTree(message.getBody());
            service.processEmail(event);
            channel.basicAck(deliveryTag, false);
            log.debug("ACK explícito emitido para mensaje email tag={}", deliveryTag);
        } catch (Exception ex) {
            log.error("Fallo al procesar comando de email [tag={}]: {}", deliveryTag, ex.getMessage());
            dlqService.forwardToDlq(message, channel, properties.queues().email(),
                    properties.routingKeys().emailDlq(), ex);
        }
    }
}
