package cl.duoc.vidasalud.notifications.service;

import cl.duoc.vidasalud.notifications.config.RabbitTopologyProperties;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;

/**
 * Servicio encargado de la gestión MANUAL de mensajes no entregados hacia DLQ.
 * Permite interceptar errores no recuperables, enriquecer el mensaje con metadatos de auditoría
 * y enviarlo explícitamente al exchange DLX (cmd.dead.dlx).
 */
@Service
public class DlqManualManagementService {
    private static final Logger log = LoggerFactory.getLogger(DlqManualManagementService.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitTopologyProperties properties;

    public DlqManualManagementService(RabbitTemplate rabbitTemplate, RabbitTopologyProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    /**
     * Mueve manualmente un mensaje fallido a la DLQ correspondiente con enriquecimiento de encabezados y confirma el ACK en la cola origen.
     */
    public void forwardToDlq(Message message, Channel channel, String originalQueue, String dlqRoutingKey, Exception reason) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            MessageProperties props = message.getMessageProperties();
            props.setHeader("x-original-queue", originalQueue);
            props.setHeader("x-death-reason", reason.getMessage() != null ? reason.getMessage() : reason.toString());
            props.setHeader("x-exception-class", reason.getClass().getName());
            props.setHeader("x-failed-at", Instant.now().toString());
            props.setHeader("x-management-mode", "MANUAL_DISPATCH");
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);

            String dlx = properties.exchanges().dlx();

            log.warn("Gestionando manualmente mensaje a DLX='{}' rkey='{}' desde cola='{}' por error: {}",
                    dlx, dlqRoutingKey, originalQueue, reason.getMessage());

            rabbitTemplate.send(dlx, dlqRoutingKey, message);

            // Confirmamos el ACK en la cola original para retirarlo formalmente una vez asegurado en DLQ
            channel.basicAck(deliveryTag, false);
            log.info("Mensaje deliveryTag={} movido exitosamente a DLQ '{}'", deliveryTag, dlqRoutingKey);
        } catch (Exception ex) {
            log.error("Fallo crítico al despachar mensaje a DLQ manualmente: {}", ex.getMessage(), ex);
            try {
                // En caso extremo de no poder enviar a DLQ, aplicar NACK sin reencolar
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioException) {
                log.error("Error al ejecutar basicNack de emergencia: {}", ioException.getMessage(), ioException);
            }
        }
    }
}
