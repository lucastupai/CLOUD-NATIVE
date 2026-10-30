package cl.duoc.vidasalud.notifications.consumer.record;

import cl.duoc.vidasalud.notifications.config.RabbitTopologyProperties;
import cl.duoc.vidasalud.notifications.service.DlqManualManagementService;
import cl.duoc.vidasalud.notifications.service.RecordGenerationService;
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
 * Consumidor del dominio funcional de Registros Clínicos y Generación de PDF.
 * Escucha la cola q.cmd.record y gestiona ACK manual y DLQ manual.
 */
@Component
public class RecordConsumer {
    private static final Logger log = LoggerFactory.getLogger(RecordConsumer.class);

    private final RecordGenerationService service;
    private final DlqManualManagementService dlqService;
    private final ObjectMapper objectMapper;
    private final RabbitTopologyProperties properties;

    public RecordConsumer(RecordGenerationService service,
                          DlqManualManagementService dlqService,
                          ObjectMapper objectMapper,
                          RabbitTopologyProperties properties) {
        this.service = service;
        this.dlqService = dlqService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @RabbitListener(queues = "${vidasalud.rabbit.queues.record}", ackMode = "MANUAL")
    public void onRecordCommand(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            JsonNode event = objectMapper.readTree(message.getBody());
            service.processRecordGeneration(event);
            channel.basicAck(deliveryTag, false);
            log.debug("ACK explícito emitido para generación de registro tag={}", deliveryTag);
        } catch (Exception ex) {
            log.error("Fallo al generar registro médico [tag={}]: {}", deliveryTag, ex.getMessage());
            dlqService.forwardToDlq(message, channel, properties.queues().record(),
                    properties.routingKeys().recordDlq(), ex);
        }
    }
}
