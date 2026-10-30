package cl.duoc.vidasalud.notifications.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración centralizada de RabbitMQ según el caso VidaSalud (EP3):
 * 3 Exchanges: cmd.direct, cmd.topic, cmd.dead.dlx
 * 3 Colas principales: q.cmd.email, q.cmd.admission, q.cmd.record
 * 3 DLQ independientes: q.cmd.email.dlq, q.cmd.admission.dlq, q.cmd.record.dlq
 * Bindings directos, por tópicos y dead-letter.
 */
@Configuration
@EnableConfigurationProperties(RabbitTopologyProperties.class)
public class RabbitConfig {

    // --- 1. EXCHANGES ---

    @Bean
    public DirectExchange cmdDirectExchange(RabbitTopologyProperties props) {
        return new DirectExchange(props.exchanges().direct(), true, false);
    }

    @Bean
    public TopicExchange cmdTopicExchange(RabbitTopologyProperties props) {
        return new TopicExchange(props.exchanges().topic(), true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange(RabbitTopologyProperties props) {
        return new DirectExchange(props.exchanges().dlx(), true, false);
    }

    // --- 2. COLAS PRINCIPALES Y DLQ (DURABLES) ---

    @Bean
    public Queue emailQueue(RabbitTopologyProperties props) {
        return QueueBuilder.durable(props.queues().email()).build();
    }

    @Bean
    public Queue emailDlq(RabbitTopologyProperties props) {
        return QueueBuilder.durable(props.queues().emailDlq()).build();
    }

    @Bean
    public Queue admissionQueue(RabbitTopologyProperties props) {
        return QueueBuilder.durable(props.queues().admission()).build();
    }

    @Bean
    public Queue admissionDlq(RabbitTopologyProperties props) {
        return QueueBuilder.durable(props.queues().admissionDlq()).build();
    }

    @Bean
    public Queue recordQueue(RabbitTopologyProperties props) {
        return QueueBuilder.durable(props.queues().record()).build();
    }

    @Bean
    public Queue recordDlq(RabbitTopologyProperties props) {
        return QueueBuilder.durable(props.queues().recordDlq()).build();
    }

    // --- 3. BINDINGS PARA FLUJO DE EMAIL / NOTIFICACIONES PACIENTE ---

    @Bean
    public Binding emailDirectBinding(Queue emailQueue, DirectExchange cmdDirectExchange, RabbitTopologyProperties props) {
        return BindingBuilder.bind(emailQueue).to(cmdDirectExchange).with(props.routingKeys().emailDirect());
    }

    @Bean
    public Binding emailTopicBinding(Queue emailQueue, TopicExchange cmdTopicExchange, RabbitTopologyProperties props) {
        return BindingBuilder.bind(emailQueue).to(cmdTopicExchange).with(props.routingKeys().emailTopic());
    }

    @Bean
    public Binding emailDeadLetterBinding(Queue emailDlq, DirectExchange deadLetterExchange, RabbitTopologyProperties props) {
        return BindingBuilder.bind(emailDlq).to(deadLetterExchange).with(props.routingKeys().emailDlq());
    }

    // --- 4. BINDINGS PARA FLUJO DE ADMISIÓN / BOX CLÍNICO ---

    @Bean
    public Binding admissionDirectBinding(Queue admissionQueue, DirectExchange cmdDirectExchange, RabbitTopologyProperties props) {
        return BindingBuilder.bind(admissionQueue).to(cmdDirectExchange).with(props.routingKeys().admissionDirect());
    }

    @Bean
    public Binding admissionTopicBinding(Queue admissionQueue, TopicExchange cmdTopicExchange, RabbitTopologyProperties props) {
        return BindingBuilder.bind(admissionQueue).to(cmdTopicExchange).with(props.routingKeys().admissionTopic());
    }

    @Bean
    public Binding admissionDeadLetterBinding(Queue admissionDlq, DirectExchange deadLetterExchange, RabbitTopologyProperties props) {
        return BindingBuilder.bind(admissionDlq).to(deadLetterExchange).with(props.routingKeys().admissionDlq());
    }

    // --- 5. BINDINGS PARA FLUJO DE GENERACIÓN DE REGISTROS / PDF ---

    @Bean
    public Binding recordDirectBinding(Queue recordQueue, DirectExchange cmdDirectExchange, RabbitTopologyProperties props) {
        return BindingBuilder.bind(recordQueue).to(cmdDirectExchange).with(props.routingKeys().recordDirect());
    }

    @Bean
    public Binding recordTopicBinding(Queue recordQueue, TopicExchange cmdTopicExchange, RabbitTopologyProperties props) {
        return BindingBuilder.bind(recordQueue).to(cmdTopicExchange).with(props.routingKeys().recordTopic());
    }

    @Bean
    public Binding recordDeadLetterBinding(Queue recordDlq, DirectExchange deadLetterExchange, RabbitTopologyProperties props) {
        return BindingBuilder.bind(recordDlq).to(deadLetterExchange).with(props.routingKeys().recordDlq());
    }

    // --- 6. CONVERTIDOR DE MENSAJES JSON ---

    @Bean
    public Jackson2JsonMessageConverter rabbitJsonConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
