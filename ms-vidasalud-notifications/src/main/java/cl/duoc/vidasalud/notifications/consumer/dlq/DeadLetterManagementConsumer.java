package cl.duoc.vidasalud.notifications.consumer.dlq;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Consumidor y monitor de Dead Letter Queues (DLQ).
 * Por defecto autoStartup='false' para permitir la inspección manual directa en RabbitMQ Management
 * o la activación para auditoría y reprocesamiento.
 */
@Component
public class DeadLetterManagementConsumer {
    private static final Logger log = LoggerFactory.getLogger(DeadLetterManagementConsumer.class);

    @RabbitListener(queues = "${vidasalud.rabbit.queues.email-dlq}", autoStartup = "${vidasalud.rabbit.dlq-auto-startup:false}", ackMode = "MANUAL")
    public void inspectEmailDlq(Message message, Channel channel) throws IOException {
        processDlqMessage("EMAIL_DLQ", message, channel);
    }

    @RabbitListener(queues = "${vidasalud.rabbit.queues.admission-dlq}", autoStartup = "${vidasalud.rabbit.dlq-auto-startup:false}", ackMode = "MANUAL")
    public void inspectAdmissionDlq(Message message, Channel channel) throws IOException {
        processDlqMessage("ADMISSION_DLQ", message, channel);
    }

    @RabbitListener(queues = "${vidasalud.rabbit.queues.record-dlq}", autoStartup = "${vidasalud.rabbit.dlq-auto-startup:false}", ackMode = "MANUAL")
    public void inspectRecordDlq(Message message, Channel channel) throws IOException {
        processDlqMessage("RECORD_DLQ", message, channel);
    }

    private void processDlqMessage(String queueType, Message message, Channel channel) throws IOException {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Object reason = message.getMessageProperties().getHeaders().get("x-death-reason");
        Object origQueue = message.getMessageProperties().getHeaders().get("x-original-queue");
        long tag = message.getMessageProperties().getDeliveryTag();

        log.warn("[DLQ-MONITOR] [ALERTA] Mensaje recibido en {} | Cola Origen: {} | Causa: {} | Body: {}",
                queueType, origQueue, reason, body);

        // Confirmamos el procesamiento de inspección
        channel.basicAck(tag, false);
    }
}
