package cl.duoc.vidasalud.rabbitadmin.service;

import cl.duoc.vidasalud.rabbitadmin.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * Servicio de administración que encapsula las operaciones de bajo nivel con el broker RabbitMQ
 * utilizando AmqpAdmin. La lógica está desacoplada de los controladores.
 */
@Service
public class RabbitAdminService {
    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin admin;

    public RabbitAdminService(AmqpAdmin admin) {
        this.admin = admin;
    }

    public void createQueue(QueueRequest request) {
        log.info("Declarando cola: name='{}', durable={}", request.name(), request.durable());
        Queue queue = new Queue(request.name(), request.durable(), false, false);
        admin.declareQueue(queue);
    }

    public boolean deleteQueue(String name) {
        log.info("Eliminando cola: name='{}'", name);
        return admin.deleteQueue(name);
    }

    public Map<String, Object> getQueueInfo(String name) {
        Properties props = admin.getQueueProperties(name);
        if (props == null) {
            return Collections.emptyMap();
        }
        return Map.of(
                "name", name,
                "messageCount", props.getProperty("QUEUE_MESSAGE_COUNT", "0"),
                "consumerCount", props.getProperty("QUEUE_CONSUMER_COUNT", "0")
        );
    }

    public void purgeQueue(String name) {
        log.info("Vaciando cola: name='{}'", name);
        admin.purgeQueue(name, false);
    }

    public void createExchange(ExchangeRequest request) {
        log.info("Declarando exchange: name='{}', type='{}'", request.name(), request.type());
        Exchange exchange = switch (request.type().toLowerCase()) {
            case "direct" -> new DirectExchange(request.name(), true, false);
            case "topic" -> new TopicExchange(request.name(), true, false);
            case "fanout" -> new FanoutExchange(request.name(), true, false);
            case "headers" -> new HeadersExchange(request.name(), true, false);
            default -> throw new IllegalArgumentException("Tipo de exchange desconocido: " + request.type());
        };
        admin.declareExchange(exchange);
    }

    public boolean deleteExchange(String name) {
        log.info("Eliminando exchange: name='{}'", name);
        return admin.deleteExchange(name);
    }

    public void createBinding(BindingRequest request) {
        String rKey = request.routingKey() == null ? "" : request.routingKey();
        log.info("Declarando binding: queue='{}' -> exchange='{}' with rkey='{}'",
                request.queue(), request.exchange(), rKey);
        Binding binding = new Binding(request.queue(), Binding.DestinationType.QUEUE,
                request.exchange(), rKey, null);
        admin.declareBinding(binding);
    }

    public void deleteBinding(BindingRequest request) {
        String rKey = request.routingKey() == null ? "" : request.routingKey();
        log.info("Eliminando binding: queue='{}' -> exchange='{}' with rkey='{}'",
                request.queue(), request.exchange(), rKey);
        Binding binding = new Binding(request.queue(), Binding.DestinationType.QUEUE,
                request.exchange(), rKey, null);
        admin.removeBinding(binding);
    }
}
